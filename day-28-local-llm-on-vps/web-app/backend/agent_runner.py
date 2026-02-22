"""
Параллельные специализированные агенты для анализа юридических документов.

Архитектура:
  Классификатор      ↓
  Агент 1: IP & Права         → Верификатор 1 ─┐  (только если нужен)
  Агент 2: Финансы            → Верификатор 2 ─┤
  Агент 3: Обязательства      → Верификатор 3 ─┼─► Агрегатор → Финальный верификатор → Gap-checker
  Агент 4: Пост-контракт      → Верификатор 4 ─┤
  Агент 5: Права потребителя  → Верификатор 5 ─┘  (только если заказчик — физлицо)
"""

import json
import random
import re
import threading
import time
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import llm
from llm import LLMConfig
from prompts import SYSTEM_PROMPT

# Семафор для последовательных запросов:
# GigaChat free-tier не допускает параллельных запросов;
# Ollama на CPU также лучше обрабатывает запросы последовательно.
_api_semaphore = threading.Semaphore(1)

AGENTS = ["ip_rights", "financial", "obligations", "post_contract", "consumer_rights"]

AGENT_LABELS = {
    "ip_rights": "IP/Права",
    "financial": "Финансы",
    "obligations": "Обязательства",
    "post_contract": "Пост-контракт",
    "consumer_rights": "Права потребителя",
}


def _load_prompt(name: str) -> str:
    path = Path(__file__).parent / "agents" / "prompts" / f"{name}.txt"
    return path.read_text(encoding="utf-8")


def _call(config: LLMConfig, system: str, user: str, max_retries: int = 5) -> str:
    """Отправляет один запрос через глобальный семафор, повторяя при временных ошибках."""
    for attempt in range(max_retries):
        try:
            with _api_semaphore:
                return llm.chat(config, system, user)
        except Exception as e:
            if attempt == max_retries - 1:
                raise
            err = str(e).lower()
            if "429" in err or "rate" in err or "limit" in err:
                delay = 3 * (2 ** attempt) + random.uniform(0.5, 2.0)
                time.sleep(delay)
            else:
                raise


def classify(
    config: LLMConfig,
    document: str,
    progress_callback: Callable | None = None,
) -> dict:
    """
    Определяет тип документа и возвращает список агентов для запуска.

    Returns:
        dict с ключами: document_type, executor_type, customer_type, relevant_agents, context
        При ошибке парсинга — fallback: базовые агенты, пустой контекст.
    """
    if progress_callback:
        progress_callback({"type": "classifying"})

    system = SYSTEM_PROMPT.format(document=document)
    raw = _call(config, system, _load_prompt("classifier"))

    match = re.search(r"\{.*\}", raw, re.DOTALL)
    if match:
        try:
            result = json.loads(match.group())
            if "relevant_agents" in result and "context" in result:
                if not result.get("is_legal_document", True):
                    result["relevant_agents"] = []
                    return result
                result["relevant_agents"] = [
                    a for a in result["relevant_agents"] if a in AGENTS
                ]
                if not result["relevant_agents"]:
                    result["relevant_agents"] = ["financial", "obligations", "post_contract"]
                if result.get("customer_type") == "физлицо":
                    if "consumer_rights" not in result["relevant_agents"]:
                        result["relevant_agents"].append("consumer_rights")
                _ip_keywords = ("дизайн", "иллюстрац", "логотип", "брендинг", "фотограф", "верстк")
                _doc_text = (result.get("document_type", "") + " " + result.get("context", "")).lower()
                if any(kw in _doc_text for kw in _ip_keywords):
                    if "ip_rights" not in result["relevant_agents"]:
                        result["relevant_agents"].append("ip_rights")
                result.setdefault("is_legal_document", True)
                return result
        except (json.JSONDecodeError, TypeError):
            pass

    return {
        "document_type": "неизвестно",
        "executor_type": "иное",
        "customer_type": "иное",
        "relevant_agents": ["financial", "obligations", "post_contract"],
        "context": "",
    }


def _run_agent(
    config: LLMConfig,
    document: str,
    agent_name: str,
    start_delay: float,
    context: str = "",
    progress_callback: Callable | None = None,
) -> tuple[str, str]:
    """Запускает агента и его верификатора."""
    time.sleep(start_delay)

    if progress_callback:
        progress_callback({"type": "agent_start", "agent": agent_name})

    system = SYSTEM_PROMPT.format(document=document)
    verifier_tmpl = _load_prompt("verifier")

    agent_prompt = _load_prompt(agent_name)
    if context:
        agent_prompt = f"Контекст: {context}\n\n{agent_prompt}"

    analysis = _call(config, system, agent_prompt)
    verified = _call(
        config,
        system,
        verifier_tmpl.format(domain=AGENT_LABELS[agent_name], analysis=analysis),
    )

    if progress_callback:
        progress_callback({"type": "agent_done", "agent": agent_name})

    return agent_name, verified


def run_all_agents(
    config: LLMConfig,
    document: str,
    progress_callback: Callable | None = None,
    active_agents: list[str] | None = None,
    context: str = "",
) -> dict[str, str]:
    """Запускает нужные агенты параллельно (каждый включает верификатор)."""
    agents_to_run = active_agents if active_agents is not None else [
        "financial", "obligations", "post_contract"
    ]
    with ThreadPoolExecutor(max_workers=len(agents_to_run)) as executor:
        futures = {
            executor.submit(
                _run_agent, config, document, name,
                i * 0.5,
                context,
                progress_callback,
            ): name
            for i, name in enumerate(agents_to_run)
        }
        results: dict[str, str] = {}
        for future in as_completed(futures):
            name, verified = future.result()
            results[name] = verified
    return results


def aggregate(
    config: LLMConfig,
    document: str,
    results: dict[str, str],
    context: str = "",
) -> str:
    """Агрегирует результаты всех агентов в финальную таблицу и верифицирует её."""
    system = SYSTEM_PROMPT.format(document=document)

    combined = "\n\n".join(
        f"=== {AGENT_LABELS[k]} ===\n{v}"
        for k, v in results.items()
    )

    table = _call(config, system, _load_prompt("aggregator").format(results=combined))
    final = _call(config, system, _load_prompt("final_verifier").format(table=table))
    checked = _call(config, system, _load_prompt("gap_checker").format(table=final, context=context))

    checked = checked.replace("🔵", "🔴")
    checked = re.sub(r"✅\s*(Норма|Низкая)", "", checked)
    return checked
