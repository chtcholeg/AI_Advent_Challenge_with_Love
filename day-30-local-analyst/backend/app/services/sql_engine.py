"""
SQL Engine с sandbox-защитой и кэшированием.
Разрешены только SELECT-запросы.
"""
import hashlib
import json
import sqlite3
from pathlib import Path
from typing import Any, Optional

import sqlparse
from sqlparse.sql import Statement
from sqlparse.tokens import Keyword, DDL, DML


FORBIDDEN_KEYWORDS = frozenset(
    [
        "DROP",
        "DELETE",
        "UPDATE",
        "INSERT",
        "CREATE",
        "ALTER",
        "ATTACH",
        "DETACH",
        "REPLACE",
        "TRUNCATE",
    ]
)


class SqlSandboxError(ValueError):
    pass


def validate_sql(sql: str) -> None:
    """Проверяет, что SQL безопасен (только SELECT). Бросает SqlSandboxError."""
    parsed = sqlparse.parse(sql)
    if not parsed:
        raise SqlSandboxError("Empty SQL query")

    stmt: Statement = parsed[0]

    # Проверяем первый DML/DDL токен
    first_keyword = None
    for token in stmt.flatten():
        if token.ttype in (DML, DDL, Keyword):
            first_keyword = token.value.upper()
            break

    if first_keyword != "SELECT":
        raise SqlSandboxError(
            f"Only SELECT queries are allowed (got: {first_keyword or 'unknown'})"
        )

    # Проверяем все ключевые слова на запрещённые
    for token in stmt.flatten():
        if token.ttype in (DML, DDL, Keyword):
            kw = token.value.upper()
            if kw in FORBIDDEN_KEYWORDS:
                raise SqlSandboxError(f"Forbidden keyword in query: {kw}")


def sql_hash(file_id: str, sql: str) -> str:
    return hashlib.sha256(f"{file_id}::{sql.strip()}".encode()).hexdigest()[:32]


def execute_query(db_path: Path, sql: str, max_rows: int = 1000) -> list[dict[str, Any]]:
    """
    Выполняет SQL-запрос к SQLite-файлу.
    Возвращает список словарей (строки).
    Лимитирует до max_rows результатов.
    """
    validate_sql(sql)

    # Оборачиваем в LIMIT если нет своего
    limited_sql = _inject_limit(sql, max_rows)

    con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    try:
        cursor = con.execute(limited_sql)
        rows = cursor.fetchall()
        return [dict(row) for row in rows]
    finally:
        con.close()


def _inject_limit(sql: str, limit: int) -> str:
    """Добавляет LIMIT если его нет."""
    upper = sql.upper()
    if "LIMIT" in upper:
        return sql
    stripped = sql.rstrip().rstrip(";")
    return f"{stripped} LIMIT {limit}"


# ─── Cache helpers (sync, используются из async context через run_in_executor) ──

def cache_key(file_id: str, sql: str) -> str:
    return sql_hash(file_id, sql)


def serialize_result(rows: list[dict]) -> str:
    return json.dumps(rows, default=str, ensure_ascii=False)


def deserialize_result(raw: str) -> list[dict]:
    return json.loads(raw)
