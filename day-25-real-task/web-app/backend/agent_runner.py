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

from gigachat import GigaChat
from gigachat.exceptions import RateLimitError
from gigachat.models import Chat, Messages, MessagesRole

from prompts import SYSTEM_PROMPT

# GigaChat free-tier не допускает параллельных completion-запросов
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


def _call(giga: GigaChat, system: str, user: str, max_retries: int = 5) -> str:
    """Отправляет один запрос через глобальный семафор, повторяя при 429."""
    for attempt in range(max_retries):
        try:
            with _api_semaphore:
                response = giga.chat(Chat(messages=[
                    Messages(role=MessagesRole.SYSTEM, content=system),
                    Messages(role=MessagesRole.USER, content=user),
                ]))
            return response.choices[0].message.content
        except RateLimitError:
            if attempt == max_retries - 1:
                raise
            delay = 3 * (2 ** attempt) + random.uniform(0.5, 2.0)
            time.sleep(delay)


def classify(
    credentials: str,
    model: str,
    document: str,
    progress_callback: Callable | None = None,
) -> dict:
    """
    Определяет тип документа и возвращает список агентов для запуска.

    Returns:
        dict с ключами: document_type, executor_type, customer_type, relevant_agents, context
        При ошибке парсинга — fallback: все агенты, пустой контекст.
    """
    if progress_callback:
        progress_callback({"type": "classifying"})

    giga = GigaChat(credentials=credentials, model=model, verify_ssl_certs=False)
    system = SYSTEM_PROMPT.format(document=document)
    raw = _call(giga, system, _load_prompt("classifier"))

    # GigaChat может обернуть JSON в ```json ... ```
    match = re.search(r"\{.*\}", raw, re.DOTALL)
    if match:
        try:
            result = json.loads(match.group())
            if "relevant_agents" in result and "context" in result:
                # Если документ не является юридическим — возвращаем как есть, без агентов
                if not result.get("is_legal_document", True):
                    result["relevant_agents"] = []
                    return result
                # Оставляем только известные агенты
                result["relevant_agents"] = [
                    a for a in result["relevant_agents"] if a in AGENTS
                ]
                # Если классификатор вернул пустой список — запускаем базовые агенты
                if not result["relevant_agents"]:
                    result["relevant_agents"] = [
                        "financial", "obligations", "post_contract"
                    ]
                # Гарантируем consumer_rights для физлиц (надёжнее, чем полагаться на LLM)
                if result.get("customer_type") == "физлицо":
                    if "consumer_rights" not in result["relevant_agents"]:
                        result["relevant_agents"].append("consumer_rights")
                # Гарантируем ip_rights для договоров на дизайн и схожие творческие работы —
                # LLM часто путает интерьерный дизайн со строительством/ремонтом
                _ip_keywords = ("дизайн", "иллюстрац", "логотип", "брендинг", "фотограф", "верстк")
                _doc_text = (result.get("document_type", "") + " " + result.get("context", "")).lower()
                if any(kw in _doc_text for kw in _ip_keywords):
                    if "ip_rights" not in result["relevant_agents"]:
                        result["relevant_agents"].append("ip_rights")
                result.setdefault("is_legal_document", True)
                return result
        except (json.JSONDecodeError, TypeError):
            pass

    # Fallback: запускаем базовых агентов без контекста
    return {
        "document_type": "неизвестно",
        "executor_type": "иное",
        "customer_type": "иное",
        "relevant_agents": ["financial", "obligations", "post_contract"],
        "context": "",
    }


def _run_agent(
    credentials: str,
    model: str,
    document: str,
    agent_name: str,
    start_delay: float,
    context: str = "",
    progress_callback: Callable | None = None,
) -> tuple[str, str]:
    """Запускает агента и его верификатора, используя один GigaChat-объект."""
    time.sleep(start_delay)

    if progress_callback:
        progress_callback({"type": "agent_start", "agent": agent_name})

    giga = GigaChat(credentials=credentials, model=model, verify_ssl_certs=False)
    system = SYSTEM_PROMPT.format(document=document)
    verifier_tmpl = _load_prompt("verifier")

    agent_prompt = _load_prompt(agent_name)
    if context:
        agent_prompt = f"Контекст: {context}\n\n{agent_prompt}"

    analysis = _call(giga, system, agent_prompt)
    verified = _call(
        giga,
        system,
        verifier_tmpl.format(domain=AGENT_LABELS[agent_name], analysis=analysis),
    )

    if progress_callback:
        progress_callback({"type": "agent_done", "agent": agent_name})

    return agent_name, verified


def run_all_agents(
    credentials: str,
    model: str,
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
                _run_agent, credentials, model, document, name,
                i * 0.5,  # stagger: 0s, 0.5s, 1s, ...
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
    credentials: str,
    model: str,
    document: str,
    results: dict[str, str],
    context: str = "",
) -> str:
    """Агрегирует результаты всех агентов в финальную таблицу и верифицирует её."""
    giga = GigaChat(credentials=credentials, model=model, verify_ssl_certs=False)
    system = SYSTEM_PROMPT.format(document=document)

    combined = "\n\n".join(
        f"=== {AGENT_LABELS[k]} ===\n{v}"
        for k, v in results.items()
    )

    table = _call(
        giga,
        system,
        _load_prompt("aggregator").format(results=combined),
    )
    final = _call(
        giga,
        system,
        _load_prompt("final_verifier").format(table=table),
    )
    checked = _call(
        giga,
        system,
        _load_prompt("gap_checker").format(table=final, context=context),
    )
    # GigaChat иногда использует нестандартные эмодзи вместо предписанных — нормализуем
    checked = checked.replace("🔵", "🔴")
    checked = re.sub(r"✅\s*(Норма|Низкая)", "", checked)
    return checked
