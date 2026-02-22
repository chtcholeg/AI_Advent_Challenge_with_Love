"""
Настройки LLM: глобальные (admin) + выбор модели на пользователя.

Таблицы в той же БД (history.db):
  llm_settings(key TEXT PRIMARY KEY, value TEXT)
  user_model(user_id TEXT PRIMARY KEY, model TEXT)
"""

import os
import sqlite3

from history import DB_PATH
from llm import LLMConfig

GIGACHAT_MODELS = ["GigaChat", "GigaChat-Pro", "GigaChat-Max"]
LOCAL_MODEL_VALUE = "ollama"
LOCAL_MODEL2_VALUE = "ollama2"
LOCAL_MODEL3_VALUE = "ollama3"


def _conn() -> sqlite3.Connection:
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    return conn


def init_settings_db() -> None:
    with _conn() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS llm_settings (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS user_model (
                user_id TEXT PRIMARY KEY,
                model   TEXT NOT NULL DEFAULT 'GigaChat'
            )
        """)


def get_llm_settings() -> dict:
    """Глобальные настройки Ollama — из БД с fallback на .env."""
    with _conn() as conn:
        rows = conn.execute("SELECT key, value FROM llm_settings").fetchall()
    db = {r["key"]: r["value"] for r in rows}
    return {
        "ollama_base_url": db.get("ollama_base_url", os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")),
        "ollama_model":    db.get("ollama_model",    os.getenv("OLLAMA_MODEL",  "qwen2.5:7b")),
        "ollama_model2":   db.get("ollama_model2",   os.getenv("OLLAMA_MODEL2", "")),
        "ollama_model3":   db.get("ollama_model3",   os.getenv("OLLAMA_MODEL3", "")),
    }


def save_llm_settings(data: dict) -> None:
    with _conn() as conn:
        for key, value in data.items():
            conn.execute(
                "INSERT INTO llm_settings(key, value) VALUES(?, ?)"
                " ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                (key, value),
            )


def get_user_model(user_id: str) -> str:
    """Сохранённая модель пользователя. Дефолт — GigaChat."""
    with _conn() as conn:
        row = conn.execute(
            "SELECT model FROM user_model WHERE user_id = ?", (user_id,)
        ).fetchone()
    return row["model"] if row else "GigaChat"


def save_user_model(user_id: str, model: str) -> None:
    with _conn() as conn:
        conn.execute(
            "INSERT INTO user_model(user_id, model) VALUES(?, ?)"
            " ON CONFLICT(user_id) DO UPDATE SET model=excluded.model",
            (user_id, model),
        )


def build_llm_config(model: str) -> LLMConfig:
    """Строит LLMConfig по имени модели."""
    gc_creds = os.getenv("GIGACHAT_AUTHORIZATION_KEY", "")
    if model in (LOCAL_MODEL_VALUE, LOCAL_MODEL2_VALUE, LOCAL_MODEL3_VALUE):
        s = get_llm_settings()
        if model == LOCAL_MODEL2_VALUE:
            ollama_model = s["ollama_model2"]
        elif model == LOCAL_MODEL3_VALUE:
            ollama_model = s["ollama_model3"]
        else:
            ollama_model = s["ollama_model"]
        return LLMConfig(
            provider="ollama",
            gigachat_credentials=gc_creds,
            ollama_base_url=s["ollama_base_url"],
            ollama_model=ollama_model,
        )
    return LLMConfig(
        provider="gigachat",
        gigachat_credentials=gc_creds,
        gigachat_model=model if model in GIGACHAT_MODELS else "GigaChat",
    )


def available_models() -> list[dict]:
    """Список моделей для фронтенда (с группировкой)."""
    models = [
        {"value": m, "label": m, "group": "GigaChat"}
        for m in GIGACHAT_MODELS
    ]
    s = get_llm_settings()
    models.append({
        "value": LOCAL_MODEL_VALUE,
        "label": f"{s['ollama_model']} (локальная)",
        "group": "Локальная модель",
    })
    if s["ollama_model2"] and s["ollama_model2"] != s["ollama_model"]:
        models.append({
            "value": LOCAL_MODEL2_VALUE,
            "label": f"{s['ollama_model2']} (локальная, быстрая)",
            "group": "Локальная модель",
        })
    if s["ollama_model3"] and s["ollama_model3"] not in (s["ollama_model"], s["ollama_model2"]):
        models.append({
            "value": LOCAL_MODEL3_VALUE,
            "label": f"{s['ollama_model3']} (локальная, сверхбыстрая)",
            "group": "Локальная модель",
        })
    return models
