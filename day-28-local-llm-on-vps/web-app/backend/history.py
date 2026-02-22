"""
История анализов — SQLite через стандартный sqlite3 (без лишних зависимостей).

Схема:
  analyses(id, user_id, filename, uploaded_at, char_count, result_markdown, document_text)
"""

import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path

DB_PATH = Path(__file__).parent / "history.db"


def _conn() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    with _conn() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS analyses (
                id            TEXT PRIMARY KEY,
                user_id       TEXT NOT NULL DEFAULT '',
                filename      TEXT NOT NULL,
                uploaded_at   TEXT NOT NULL,
                char_count    INTEGER NOT NULL,
                result_markdown TEXT NOT NULL,
                document_text TEXT NOT NULL
            )
        """)
        # Миграция: добавить колонку user_id если её нет (для существующих БД)
        try:
            conn.execute("ALTER TABLE analyses ADD COLUMN user_id TEXT NOT NULL DEFAULT ''")
        except Exception:
            pass  # колонка уже существует


def save_analysis(filename: str, char_count: int, result_markdown: str, document_text: str, user_id: str = '') -> str:
    record_id = str(uuid.uuid4())
    uploaded_at = datetime.now(timezone.utc).isoformat()
    with _conn() as conn:
        conn.execute(
            "INSERT INTO analyses (id, user_id, filename, uploaded_at, char_count, result_markdown, document_text) VALUES (?, ?, ?, ?, ?, ?, ?)",
            (record_id, user_id, filename, uploaded_at, char_count, result_markdown, document_text),
        )
    return record_id


def list_analyses(user_id: str = '') -> list[dict]:
    with _conn() as conn:
        rows = conn.execute(
            "SELECT id, filename, uploaded_at, char_count FROM analyses WHERE user_id = ? ORDER BY uploaded_at DESC",
            (user_id,),
        ).fetchall()
    return [dict(r) for r in rows]


def get_analysis(record_id: str) -> dict | None:
    with _conn() as conn:
        row = conn.execute(
            "SELECT * FROM analyses WHERE id = ?", (record_id,)
        ).fetchone()
    return dict(row) if row else None


def update_analysis(record_id: str, result_markdown: str) -> bool:
    with _conn() as conn:
        cur = conn.execute(
            "UPDATE analyses SET result_markdown = ?, uploaded_at = ? WHERE id = ?",
            (result_markdown, datetime.now(timezone.utc).isoformat(), record_id),
        )
    return cur.rowcount > 0


def delete_analysis(record_id: str) -> bool:
    with _conn() as conn:
        cur = conn.execute("DELETE FROM analyses WHERE id = ?", (record_id,))
    return cur.rowcount > 0
