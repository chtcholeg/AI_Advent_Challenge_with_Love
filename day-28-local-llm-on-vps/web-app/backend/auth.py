"""
Аутентификация: пользователи и сессии (SQLite, без доп. зависимостей).

Таблицы хранятся в той же БД что и история (history.db):
  users(id, username, password_hash, role, created_at)
  sessions(session_id, user_id, created_at, expires_at)
"""

import hashlib
import hmac
import os
import sqlite3
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

from history import DB_PATH

SESSION_TTL_DAYS = 7


def _conn() -> sqlite3.Connection:
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    return conn


def init_auth_db() -> None:
    with _conn() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id            TEXT PRIMARY KEY,
                username      TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                role          TEXT NOT NULL DEFAULT 'user',
                created_at    TEXT NOT NULL
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS sessions (
                session_id  TEXT PRIMARY KEY,
                user_id     TEXT NOT NULL,
                created_at  TEXT NOT NULL,
                expires_at  TEXT NOT NULL
            )
        """)


def _hash_password(password: str) -> str:
    salt = os.urandom(32)
    key = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 100_000)
    return salt.hex() + ":" + key.hex()


def _verify_password(password: str, stored: str) -> bool:
    try:
        salt_hex, key_hex = stored.split(":", 1)
        key = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt_hex), 100_000)
        return hmac.compare_digest(key.hex(), key_hex)
    except Exception:
        return False


def create_user(username: str, password: str, role: str = "user") -> dict:
    user_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()
    with _conn() as conn:
        conn.execute(
            "INSERT INTO users (id, username, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)",
            (user_id, username, _hash_password(password), role, now),
        )
    return {"id": user_id, "username": username, "role": role, "created_at": now}


def get_user_by_username(username: str) -> dict | None:
    with _conn() as conn:
        row = conn.execute("SELECT * FROM users WHERE username = ?", (username,)).fetchone()
    return dict(row) if row else None


def list_users() -> list[dict]:
    with _conn() as conn:
        rows = conn.execute(
            "SELECT id, username, role, created_at FROM users ORDER BY created_at"
        ).fetchall()
    return [dict(r) for r in rows]


def delete_user(username: str) -> bool:
    with _conn() as conn:
        conn.execute(
            "DELETE FROM sessions WHERE user_id = (SELECT id FROM users WHERE username = ?)",
            (username,),
        )
        cur = conn.execute("DELETE FROM users WHERE username = ?", (username,))
    return cur.rowcount > 0


def authenticate(username: str, password: str) -> dict | None:
    user = get_user_by_username(username)
    if user and _verify_password(password, user["password_hash"]):
        return user
    return None


def create_session(user_id: str) -> str:
    session_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc)
    expires_at = (now + timedelta(days=SESSION_TTL_DAYS)).isoformat()
    with _conn() as conn:
        conn.execute(
            "INSERT INTO sessions VALUES (?, ?, ?, ?)",
            (session_id, user_id, now.isoformat(), expires_at),
        )
    return session_id


def get_session_user(session_id: str) -> dict | None:
    now = datetime.now(timezone.utc).isoformat()
    with _conn() as conn:
        row = conn.execute(
            """SELECT u.id, u.username, u.role
               FROM sessions s JOIN users u ON s.user_id = u.id
               WHERE s.session_id = ? AND s.expires_at > ?""",
            (session_id, now),
        ).fetchone()
    return dict(row) if row else None


def delete_session(session_id: str) -> None:
    with _conn() as conn:
        conn.execute("DELETE FROM sessions WHERE session_id = ?", (session_id,))


def create_admin_if_needed(username: str, password: str) -> None:
    """Создаёт admin-пользователя из .env если его ещё нет в БД."""
    if not get_user_by_username(username):
        create_user(username, password, role="admin")
