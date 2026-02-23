"""
Клиент для Ollama.
Поддерживает синхронные запросы и SSE-стриминг.
"""
import json
from typing import AsyncIterator, Optional

import httpx

from app.config import settings


class OllamaUnavailableError(Exception):
    pass


class LLMClient:
    def __init__(
        self,
        base_url: Optional[str] = None,
        model: Optional[str] = None,
        timeout: float = 120.0,
    ):
        self.base_url = (base_url or settings.ollama_base_url).rstrip("/")
        self.model = model or settings.ollama_model
        self.timeout = timeout

    async def health_check(self) -> dict:
        """Проверяет доступность Ollama и возвращает список моделей."""
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.get(f"{self.base_url}/api/tags")
                resp.raise_for_status()
                data = resp.json()
                models = [m["name"] for m in data.get("models", [])]
                return {"available": True, "models": models}
        except Exception as e:
            return {"available": False, "models": [], "error": str(e)}

    async def chat_sync(
        self,
        user_message: str,
        system: str = "",
        temperature: float = 0.0,
    ) -> str:
        """Синхронный (не-стриминговый) запрос к LLM. Возвращает полный ответ."""
        messages = []
        if system:
            messages.append({"role": "system", "content": system})
        messages.append({"role": "user", "content": user_message})

        payload = {
            "model": self.model,
            "messages": messages,
            "stream": False,
            "options": {"temperature": temperature},
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.post(
                    f"{self.base_url}/api/chat",
                    json=payload,
                )
                resp.raise_for_status()
                data = resp.json()
                return data["message"]["content"]
        except httpx.ConnectError as e:
            raise OllamaUnavailableError(
                f"Cannot connect to Ollama at {self.base_url}: {e}"
            )
        except httpx.HTTPStatusError as e:
            raise OllamaUnavailableError(
                f"Ollama request failed ({e.response.status_code}): {e.response.text[:200]}"
            )

    async def chat_stream(
        self,
        user_message: str,
        system: str = "",
        temperature: float = 0.3,
    ) -> AsyncIterator[str]:
        """Стриминговый запрос. Генерирует токены по мере поступления."""
        messages = []
        if system:
            messages.append({"role": "system", "content": system})
        messages.append({"role": "user", "content": user_message})

        payload = {
            "model": self.model,
            "messages": messages,
            "stream": True,
            "options": {"temperature": temperature},
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                async with client.stream(
                    "POST",
                    f"{self.base_url}/api/chat",
                    json=payload,
                ) as resp:
                    resp.raise_for_status()
                    async for line in resp.aiter_lines():
                        if not line:
                            continue
                        try:
                            chunk = json.loads(line)
                            token = chunk.get("message", {}).get("content", "")
                            if token:
                                yield token
                            if chunk.get("done"):
                                break
                        except json.JSONDecodeError:
                            continue
        except httpx.ConnectError as e:
            raise OllamaUnavailableError(
                f"Cannot connect to Ollama at {self.base_url}: {e}"
            )
        except httpx.HTTPStatusError as e:
            raise OllamaUnavailableError(
                f"Ollama request failed ({e.response.status_code}): {e.response.text[:200]}"
            )


# ─── Prompts ──────────────────────────────────────────────────────────────────

SYSTEM_ANALYST = """Ты опытный аналитик данных. Твоя задача — анализировать данные из таблиц SQLite.
Отвечай чётко, по делу и на русском языке.
"""


def build_sql_prompt(
    question: str,
    tables: list[dict],  # [{"name": str, "columns": [ColumnInfo], "row_count": int}]
) -> str:
    schema_parts = []
    for t in tables:
        cols = []
        for c in t["columns"]:
            col_str = f"{c['name']} ({c['dtype']})"
            if c.get("description"):
                col_str += f" -- {c['description']}"
            if c.get("examples"):
                col_str += f" [примеры: {', '.join(str(e) for e in c['examples'][:8])}]"
            cols.append(col_str)
        schema_parts.append(f'Table "{t["name"]}" ({t["row_count"]} rows): {", ".join(cols)}')

    schema_str = "\n".join(schema_parts)

    return f"""Схема базы данных:
{schema_str}

Вопрос пользователя: {question}

Сгенерируй один SQL SELECT-запрос для ответа на этот вопрос.
Правила:
- Только SELECT, никаких изменений данных
- Верни ТОЛЬКО SQL-запрос, без пояснений и блоков кода
- Для сводных или общих вопросов верни наиболее информативный агрегирующий запрос (GROUP BY, COUNT, AVG и т.д.)
- Пиши CANNOT_ANSWER только если вопрос полностью не связан с данными (например, "напиши стихотворение")
- Никогда не пиши CANNOT_ANSWER только потому что вопрос широкий — всегда старайся вернуть полезный SQL
- При GROUP BY по текстовой колонке всегда используй COALESCE(column, 'Неизвестный'), чтобы NULL не выпадал отдельной строкой
- Если в примерах видны похожие варианты одного продукта (например "Zoom" и "Zoom Workplace"), объедини их через CASE WHEN под единым каноническим названием (берётся более полное/актуальное)"""


def build_explanation_prompt(
    question: str,
    sql: str,
    result_rows: list[dict],
    chart_hint: Optional[str] = None,
) -> str:
    result_str = json.dumps(result_rows[:50], default=str, ensure_ascii=False, indent=2)

    chart_instruction = ""
    if chart_hint:
        chart_instruction = f"\nТакже предложи график. После текста ответа добавь JSON-блок: ```json\n{{\"chart\": {{\"type\": \"bar|line|pie\", \"labels\": [...], \"datasets\": [...]}}}}\n```"

    return f"""Пользователь спросил: "{question}"

Был выполнен SQL-запрос:
{sql}

Результаты ({len(result_rows)} строк):
{result_str}

Дай чёткий и конкретный ответ на вопрос пользователя на основе этих данных.
Отвечай на русском языке.{chart_instruction}"""


def build_retry_sql_prompt(
    original_prompt: str,
    failed_sql: str,
    error: str,
) -> str:
    return f"""{original_prompt}

Предыдущая попытка сгенерировала SQL, который завершился ошибкой:
{failed_sql}
Ошибка: {error}

Исправь SQL-запрос. Верни ТОЛЬКО исправленный SQL-запрос."""


def should_suggest_chart(result_rows: list[dict]) -> bool:
    """Эвристика: предлагать ли график."""
    if not result_rows:
        return False
    if len(result_rows) < 2:
        return False
    # Есть хотя бы одно числовое поле
    sample = result_rows[0]
    has_numeric = any(isinstance(v, (int, float)) for v in sample.values())
    return has_numeric and len(result_rows) <= 100
