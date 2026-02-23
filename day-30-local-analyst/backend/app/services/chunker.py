"""
Стратегия обработки больших текстовых файлов через chunking + суммаризация.
"""
from pathlib import Path
from typing import TYPE_CHECKING

from app.services.parser import read_text_chunks

if TYPE_CHECKING:
    from app.services.llm_client import LLMClient


async def summarize_text_file(
    source_path: Path,
    question: str,
    llm: "LLMClient",
    chunk_lines: int = 300,
) -> str:
    """
    Для больших текстовых логов:
    1. Делит файл на чанки
    2. Каждый чанк суммаризирует с учётом вопроса
    3. Объединяет суммари и делает финальный ответ
    """
    chunks = read_text_chunks(source_path, chunk_lines=chunk_lines)

    if not chunks:
        return "Файл пуст."

    # Если файл маленький — отправляем всё сразу
    total_chars = sum(len(c) for c in chunks)
    if total_chars < 8000:
        combined = "\n".join(chunks)
        prompt = f"""Проанализируй содержимое лога и ответь на вопрос: "{question}"

Содержимое:
{combined}

Дай краткий ответ на русском языке."""
        return await llm.chat_sync(prompt, system="Ты эксперт по анализу логов. Отвечай на русском языке.")

    # Большой файл: суммаризируем каждый чанк
    summaries = []
    for i, chunk in enumerate(chunks):
        prompt = f"""Ты анализируешь часть {i + 1} из {len(chunks)} лог-файла.
Вопрос: "{question}"

Фрагмент лога:
{chunk[:4000]}

Кратко изложи ключевую информацию из этого фрагмента, относящуюся к вопросу (2–4 предложения)."""
        summary = await llm.chat_sync(prompt, system="Ты эксперт по анализу логов. Отвечай на русском языке.")
        summaries.append(f"[Часть {i + 1}]: {summary}")

    combined_summaries = "\n\n".join(summaries)
    final_prompt = f"""На основе этих выдержек из лог-файла ответь на вопрос: "{question}"

Выдержки:
{combined_summaries}

Дай развёрнутый итоговый ответ на русском языке."""

    return await llm.chat_sync(final_prompt, system="Ты эксперт по анализу логов. Отвечай на русском языке.")
