"""
Определяет схему таблицы и с помощью LLM интерпретирует поля.
"""
import json
from pathlib import Path
from typing import TYPE_CHECKING

from app.services.parser import get_table_info, sample_rows, get_sqlite_tables
from app.models.schemas import ColumnInfo

if TYPE_CHECKING:
    from app.services.llm_client import LLMClient


def build_schema_from_db(db_path: Path, table: str) -> list[ColumnInfo]:
    """Строит схему без LLM — только из SQLite PRAGMA."""
    import sqlite3

    pragma = get_table_info(db_path, table)

    def _is_numeric(dtype: str) -> bool:
        dt = dtype.upper()
        return any(t in dt for t in ("INT", "FLOAT", "REAL", "NUM", "DOUBLE"))

    con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    columns = []
    try:
        for col in pragma:
            name = col["name"]
            dtype = col["type"] or "TEXT"
            if _is_numeric(dtype):
                # Для числовых: берём 3 примера из случайной выборки
                rows = sample_rows(db_path, table, n=5)
                examples = [str(r.get(name, "")) for r in rows if r.get(name) is not None][:3]
            else:
                # Для текстовых: берём до 10 наиболее частых уникальных значений
                try:
                    cursor = con.execute(
                        f'SELECT "{name}", COUNT(*) as cnt FROM "{table}" '
                        f'WHERE "{name}" IS NOT NULL AND TRIM(CAST("{name}" AS TEXT)) != "" '
                        f'GROUP BY "{name}" ORDER BY cnt DESC LIMIT 10'
                    )
                    examples = [str(row[0]) for row in cursor.fetchall()]
                except Exception:
                    rows = sample_rows(db_path, table, n=5)
                    examples = [str(r.get(name, "")) for r in rows if r.get(name) is not None][:3]
            columns.append(ColumnInfo(name=name, dtype=dtype, examples=examples))
    finally:
        con.close()
    return columns


def _dtype_to_friendly(dtype: str) -> str:
    dt = dtype.upper()
    if "INT" in dt:
        return "INTEGER"
    if "FLOAT" in dt or "REAL" in dt or "DOUBLE" in dt or "NUM" in dt:
        return "FLOAT"
    if "BOOL" in dt:
        return "BOOLEAN"
    return "TEXT"


async def enrich_schema_with_llm(
    columns: list[ColumnInfo],
    table_name: str,
    llm: "LLMClient",
) -> list[ColumnInfo]:
    """
    Отправляет схему в LLM и просит угадать описание каждого поля.
    Возвращает обогащённую схему.
    """
    cols_repr = "\n".join(
        f"- {c.name} ({c.dtype}): examples = {c.examples}"
        for c in columns
    )
    prompt = f"""Ты анализируешь таблицу "{table_name}".
Вот её колонки с типами и примерами значений:

{cols_repr}

Для каждой колонки напиши короткое понятное описание (не более одного предложения, на русском языке).
Ответь ТОЛЬКО валидным JSON-массивом в точно таком формате:
[
  {{"name": "имя_колонки", "description": "что означает эта колонка"}},
  ...
]
Никакого другого текста."""

    raw = await llm.chat_sync(prompt, system="Ты ассистент-аналитик данных. Отвечай строго в формате JSON.")
    raw = raw.strip()

    # Извлекаем JSON из ответа
    try:
        start = raw.index("[")
        end = raw.rindex("]") + 1
        data = json.loads(raw[start:end])
        desc_map = {item["name"]: item.get("description", "") for item in data}
    except (ValueError, json.JSONDecodeError, KeyError):
        return columns

    for col in columns:
        if col.name in desc_map:
            col.description = desc_map[col.name]
    return columns


def get_primary_table(db_path: Path) -> str:
    """Возвращает первую таблицу из БД (для файлов с одной таблицей)."""
    tables = get_sqlite_tables(db_path)
    if not tables:
        raise ValueError("No tables found in database")
    # Предпочитаем таблицу с именем "logs", иначе первая
    if "logs" in tables:
        return "logs"
    return tables[0]
