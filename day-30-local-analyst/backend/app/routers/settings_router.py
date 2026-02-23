from fastapi import APIRouter
from app.db.database import get_db
from app.models.schemas import OllamaStatus, AppSettingsIn
from app.services.llm_client import LLMClient
from app.config import settings

router = APIRouter(prefix="/api", tags=["settings"])


@router.get("/ollama/status", response_model=OllamaStatus)
async def ollama_status():
    db = await get_db()
    try:
        # Читаем настройки из БД если есть
        cursor = await db.execute("SELECT value FROM app_settings WHERE key = 'ollama_base_url'")
        row = await cursor.fetchone()
        base_url = row["value"] if row else settings.ollama_base_url

        cursor = await db.execute("SELECT value FROM app_settings WHERE key = 'ollama_model'")
        row = await cursor.fetchone()
        model = row["value"] if row else settings.ollama_model
    finally:
        await db.close()

    llm = LLMClient(base_url=base_url, model=model)
    health = await llm.health_check()
    return OllamaStatus(
        available=health["available"],
        models=health.get("models", []),
        error=health.get("error"),
    )


@router.get("/settings")
async def get_settings():
    db = await get_db()
    try:
        cursor = await db.execute("SELECT key, value FROM app_settings")
        rows = await cursor.fetchall()
        result = {r["key"]: r["value"] for r in rows}
        # Дефолты
        result.setdefault("ollama_model", settings.ollama_model)
        result.setdefault("ollama_base_url", settings.ollama_base_url)
        return result
    finally:
        await db.close()


@router.patch("/settings")
async def update_settings(body: AppSettingsIn):
    db = await get_db()
    try:
        updates = {}
        if body.ollama_model:
            updates["ollama_model"] = body.ollama_model
        if body.ollama_base_url:
            updates["ollama_base_url"] = body.ollama_base_url

        for key, value in updates.items():
            await db.execute(
                "INSERT OR REPLACE INTO app_settings (key, value) VALUES (?, ?)",
                (key, value),
            )
        await db.commit()
        return {"ok": True, "updated": list(updates.keys())}
    finally:
        await db.close()
