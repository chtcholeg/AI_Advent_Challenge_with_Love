"""
Параллельные специализированные агенты для анализа юридических документов.

Архитектура:
  Агент 1: IP & Права     → Верификатор 1 ─┐
  Агент 2: Финансы        → Верификатор 2 ─┤
  Агент 3: Обязательства  → Верификатор 3 ─┼─► Агрегатор → Финальный верификатор
  Агент 4: Пост-контракт  → Верификатор 4 ─┘
"""

import random
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from gigachat import GigaChat
from gigachat.exceptions import RateLimitError
from gigachat.models import Chat, Messages, MessagesRole

from prompts import SYSTEM_PROMPT

# GigaChat free-tier не допускает параллельных completion-запросов
_api_semaphore = threading.Semaphore(1)

AGENTS = ["ip_rights", "financial", "obligations", "post_contract"]

AGENT_LABELS = {
    "ip_rights": "IP/Права",
    "financial": "Финансы",
    "obligations": "Обязательства",
    "post_contract": "Пост-контракт",
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


def _run_agent(
    credentials: str, model: str, document: str, agent_name: str, start_delay: float
) -> tuple[str, str]:
    """Запускает агента и его верификатора, используя один GigaChat-объект."""
    # Небольшой случайный старт чтобы не засыпать OAuth одновременно
    time.sleep(start_delay)

    giga = GigaChat(credentials=credentials, model=model, verify_ssl_certs=False)
    system = SYSTEM_PROMPT.format(document=document)
    verifier_tmpl = _load_prompt("verifier")

    analysis = _call(giga, system, _load_prompt(agent_name))
    verified = _call(
        giga,
        system,
        verifier_tmpl.format(domain=AGENT_LABELS[agent_name], analysis=analysis),
    )
    return agent_name, verified


def run_all_agents(
    credentials: str, model: str, document: str
) -> dict[str, str]:
    """Запускает все 4 агента параллельно (каждый включает верификатор).

    Возвращает словарь {agent_name: verified_analysis}.
    """
    with ThreadPoolExecutor(max_workers=4) as executor:
        futures = {
            executor.submit(
                _run_agent, credentials, model, document, name,
                i * 0.5  # stagger: 0s, 0.5s, 1s, 1.5s
            ): name
            for i, name in enumerate(AGENTS)
        }
        results: dict[str, str] = {}
        for future in as_completed(futures):
            name, verified = future.result()
            results[name] = verified
    return results


def aggregate(
    credentials: str, model: str, document: str, results: dict[str, str]
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
    return final
