"""
JuriLytics — FastAPI backend (Фаза 2).

Endpoints:
  POST /api/login               — вход, устанавливает httpOnly cookie
  POST /api/logout              — выход, удаляет сессию
  GET  /api/me                  — текущий пользователь
  GET  /api/models              — доступные модели (динамически)
  GET  /api/settings/model      — сохранённая модель текущего пользователя
  PUT  /api/settings/model      — сохранить выбор модели
  GET  /api/admin/users         — список пользователей (только admin)
  POST /api/admin/users         — создать пользователя (только admin)
  DELETE /api/admin/users/{u}   — удалить пользователя (только admin)
  GET  /api/admin/settings      — настройки Ollama (только admin)
  PUT  /api/admin/settings      — обновить настройки Ollama (только admin)
  POST /api/admin/settings/test — проверить подключение к Ollama (только admin)
  POST /api/analyze             — загружает файл (.txt / .pdf), стримит прогресс через SSE
  POST /api/ask                 — Q&A вопрос по документу (с историей диалога)
  GET  /api/history             — список сохранённых анализов
  GET  /api/history/{id}        — детали анализа + создание Q&A сессии
  POST /api/history/{id}/reanalyze — повторный анализ
  DELETE /api/history/{id}      — удалить запись из истории
  GET  /                        — frontend (static files)

HTTPS:
  Обеспечивается Nginx + Certbot при деплое на VPS (см. VPS Wizard).
  Локально работает по HTTP.
"""

import asyncio
import json
import logging
import os
import threading
import time
import uuid

from dotenv import load_dotenv
from fastapi import Cookie, Depends, FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address

import auth
import history as hist
import settings
from agent_runner import aggregate, classify, run_all_agents
from llm import ChatSession
from reader import read_file

load_dotenv()
hist.init_db()
auth.init_auth_db()
settings.init_settings_db()

admin_username = os.getenv("ADMIN_USERNAME", "admin")
admin_password = os.getenv("ADMIN_PASSWORD", "")
if admin_password:
    auth.create_admin_if_needed(admin_username, admin_password)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

limiter = Limiter(key_func=get_remote_address)

app = FastAPI(title="JuriLytics", version="2.0.0")
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Хранилище Q&A сессий: session_id -> (ChatSession, created_at)
SESSION_TTL_SECONDS = 2 * 60 * 60  # 2 часа
_sessions_lock = threading.Lock()
_sessions: dict[str, tuple[ChatSession, float]] = {}


def _cleanup_sessions() -> None:
    now = time.monotonic()
    with _sessions_lock:
        expired = [sid for sid, (_, ts) in _sessions.items() if now - ts > SESSION_TTL_SECONDS]
        for sid in expired:
            del _sessions[sid]
    if expired:
        logger.info("Session cleanup: удалено %d устаревших сессий, осталось %d", len(expired), len(_sessions))


def _session_cleanup_worker() -> None:
    while True:
        time.sleep(1800)
        _cleanup_sessions()


threading.Thread(target=_session_cleanup_worker, daemon=True).start()


# ── Auth dependencies ───────────────────────────────────────────────────────────

def require_auth(session_id: str | None = Cookie(default=None)) -> dict:
    if not session_id:
        raise HTTPException(status_code=401, detail="Не авторизован")
    user = auth.get_session_user(session_id)
    if not user:
        raise HTTPException(status_code=401, detail="Сессия истекла или недействительна")
    return user


def require_admin(user: dict = Depends(require_auth)) -> dict:
    if user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Нет прав администратора")
    return user


# ── Helpers ────────────────────────────────────────────────────────────────────

def _get_config(model: str | None, user_id: str):
    """Строит LLMConfig: model из запроса → сохранённый выбор → дефолт GigaChat."""
    effective_model = model or settings.get_user_model(user_id)
    config = settings.build_llm_config(effective_model)
    if config.provider == "gigachat" and not config.gigachat_credentials:
        raise HTTPException(
            status_code=500,
            detail="GIGACHAT_AUTHORIZATION_KEY не найден. Создайте .env файл в backend/",
        )
    return config


def _sse(data: dict) -> str:
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


def _make_session(config, document_text: str) -> str:
    session_id = str(uuid.uuid4())
    with _sessions_lock:
        _sessions[session_id] = (
            ChatSession(config=config, document=document_text),
            time.monotonic(),
        )
    return session_id


# ── Pydantic models ────────────────────────────────────────────────────────────

class LoginRequest(BaseModel):
    username: str
    password: str


class CreateUserRequest(BaseModel):
    username: str
    password: str


class AskRequest(BaseModel):
    session_id: str
    question: str


class ModelRequest(BaseModel):
    model: str


class OllamaSettingsRequest(BaseModel):
    ollama_base_url: str
    ollama_model: str
    ollama_model2: str = ""
    ollama_model3: str = ""


class AdminChatStartRequest(BaseModel):
    model: str | None = None
    system_prompt: str = "Ты — полезный ассистент."


# ── Auth endpoints ─────────────────────────────────────────────────────────────

@app.post("/api/login")
@limiter.limit("5/minute")
def login(request: Request, body: LoginRequest, response: Response):
    user = auth.authenticate(body.username, body.password)
    if not user:
        raise HTTPException(status_code=401, detail="Неверное имя пользователя или пароль")
    session_id = auth.create_session(user["id"])
    response.set_cookie(
        key="session_id",
        value=session_id,
        httponly=True,
        samesite="strict",
        max_age=auth.SESSION_TTL_DAYS * 24 * 3600,
    )
    return {"username": user["username"], "role": user["role"]}


@app.post("/api/logout")
def logout(response: Response, session_id: str | None = Cookie(default=None)):
    if session_id:
        auth.delete_session(session_id)
    response.delete_cookie("session_id")
    return {"ok": True}


@app.get("/api/me")
def me(user: dict = Depends(require_auth)):
    return {"username": user["username"], "role": user["role"]}


# ── Models & user settings ─────────────────────────────────────────────────────

@app.get("/api/models")
def get_models(user: dict = Depends(require_auth)):
    """Список доступных языковых моделей (динамически)."""
    return {"models": settings.available_models()}


@app.get("/api/settings/model")
def get_user_model(user: dict = Depends(require_auth)):
    """Сохранённая модель текущего пользователя."""
    return {"model": settings.get_user_model(user["id"])}


@app.put("/api/settings/model")
def set_user_model(body: ModelRequest, user: dict = Depends(require_auth)):
    """Сохранить выбор модели пользователя."""
    all_values = {m["value"] for m in settings.available_models()}
    if body.model not in all_values:
        raise HTTPException(status_code=400, detail="Неизвестная модель")
    settings.save_user_model(user["id"], body.model)
    return {"ok": True}


# ── Admin endpoints ────────────────────────────────────────────────────────────

@app.get("/api/admin/users")
def admin_list_users(user: dict = Depends(require_admin)):
    return auth.list_users()


@app.post("/api/admin/users", status_code=201)
def admin_create_user(body: CreateUserRequest, user: dict = Depends(require_admin)):
    if not body.username.strip():
        raise HTTPException(status_code=400, detail="Имя пользователя не может быть пустым")
    if len(body.password) < 6:
        raise HTTPException(status_code=400, detail="Пароль должен быть не менее 6 символов")
    if auth.get_user_by_username(body.username):
        raise HTTPException(status_code=409, detail="Пользователь уже существует")
    return auth.create_user(body.username, body.password)


@app.delete("/api/admin/users/{username}", status_code=204)
def admin_delete_user(username: str, user: dict = Depends(require_admin)):
    if username == user["username"]:
        raise HTTPException(status_code=400, detail="Нельзя удалить самого себя")
    if not auth.delete_user(username):
        raise HTTPException(status_code=404, detail="Пользователь не найден")


@app.get("/api/admin/settings")
def admin_get_settings(user: dict = Depends(require_admin)):
    """Глобальные настройки Ollama."""
    return settings.get_llm_settings()


@app.put("/api/admin/settings")
def admin_save_settings(body: OllamaSettingsRequest, user: dict = Depends(require_admin)):
    """Обновить настройки Ollama (URL и модель)."""
    settings.save_llm_settings({
        "ollama_base_url": body.ollama_base_url.strip(),
        "ollama_model":    body.ollama_model.strip(),
        "ollama_model2":   body.ollama_model2.strip(),
        "ollama_model3":   body.ollama_model3.strip(),
    })
    return {"ok": True}


@app.post("/api/admin/chat/start")
def admin_chat_start(body: AdminChatStartRequest, user: dict = Depends(require_admin)):
    """Создать тестовую LLM-сессию без документа (для проверки моделей)."""
    config = _get_config(body.model, user["id"])
    session_id = str(uuid.uuid4())
    with _sessions_lock:
        _sessions[session_id] = (
            ChatSession(config=config, system_prompt=body.system_prompt),
            time.monotonic(),
        )
    return {"session_id": session_id}


@app.post("/api/admin/settings/test")
def admin_test_connection(user: dict = Depends(require_admin)):
    """Проверить подключение к Ollama."""
    s = settings.get_llm_settings()
    try:
        from openai import OpenAI
        client = OpenAI(
            base_url=f"{s['ollama_base_url'].rstrip('/')}/v1",
            api_key="ollama",
        )
        models = client.models.list()
        model_ids = [m.id for m in models.data]
        return {"ok": True, "models": model_ids}
    except Exception as e:
        raise HTTPException(status_code=503, detail=str(e))


# ── Analysis helpers ───────────────────────────────────────────────────────────

def _build_analysis_generator(config, text: str, filename: str, truncated: bool,
                               on_done_callback=None):
    """Генератор SSE для анализа документа."""
    async def generate():
        loop = asyncio.get_running_loop()
        queue: asyncio.Queue = asyncio.Queue()

        yield _sse({"type": "meta", "filename": filename, "chars": len(text), "truncated": truncated})

        def progress_callback(event: dict):
            asyncio.run_coroutine_threadsafe(queue.put(event), loop)

        def run_analysis():
            try:
                doc_class = classify(config, text, progress_callback)

                if not doc_class.get("is_legal_document", True):
                    progress_callback({"type": "classified", "active_agents": []})
                    doc_type = doc_class.get("document_type", "неизвестный тип")
                    reason = doc_class.get("context", "")
                    markdown = (
                        f"## Документ не является юридическим соглашением\n\n"
                        f"**Тип документа:** {doc_type}\n\n"
                        f"{reason}\n\n"
                        f"Анализ рисков невозможен: данный документ не содержит "
                        f"юридических обязательств сторон и не является договором, "
                        f"офертой или иным юридически значимым соглашением."
                    )
                    progress_callback({"type": "done", "markdown": markdown})
                    return

                progress_callback({
                    "type": "classified",
                    "active_agents": doc_class["relevant_agents"],
                })
                results = run_all_agents(
                    config, text, progress_callback,
                    active_agents=doc_class["relevant_agents"],
                    context=doc_class["context"],
                )
                progress_callback({"type": "aggregating"})
                table = aggregate(config, text, results, doc_class.get("context", ""))
                progress_callback({"type": "done", "markdown": table})
            except Exception as e:
                logger.exception("Analysis error")
                progress_callback({"type": "error", "message": str(e)})

        threading.Thread(target=run_analysis, daemon=True).start()

        while True:
            event = await queue.get()
            yield _sse(event)

            if event["type"] == "done":
                if on_done_callback:
                    on_done_callback(event["markdown"])
                session_id = _make_session(config, text)
                yield _sse({"type": "session", "session_id": session_id})
                break

            if event["type"] == "error":
                break

    return generate


# ── API routes ─────────────────────────────────────────────────────────────────

@app.post("/api/analyze")
async def analyze(
    file: UploadFile = File(...),
    model: str | None = Form(None),
    user: dict = Depends(require_auth),
):
    """Анализирует документ, стримит прогресс через SSE."""
    config = _get_config(model, user["id"])

    content = await file.read()
    filename = file.filename or "document.txt"

    try:
        text, truncated = read_file(filename, content)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    if not text.strip():
        raise HTTPException(status_code=400, detail="Файл пустой или не содержит текста")

    def on_done(markdown: str):
        hist.save_analysis(filename, len(text), markdown, text, user_id=user["id"])

    generate = _build_analysis_generator(config, text, filename, truncated, on_done)

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.post("/api/ask")
async def ask(body: AskRequest, user: dict = Depends(require_auth)):
    """Задаёт вопрос по ранее загруженному документу."""
    with _sessions_lock:
        entry = _sessions.get(body.session_id)
    if not entry:
        raise HTTPException(status_code=404, detail="Сессия не найдена. Загрузите документ снова.")
    session, _ = entry

    if not body.question.strip():
        raise HTTPException(status_code=400, detail="Вопрос не может быть пустым")

    loop = asyncio.get_running_loop()
    t0 = time.monotonic()
    answer = await loop.run_in_executor(None, session.ask, body.question)
    elapsed_ms = round((time.monotonic() - t0) * 1000)
    return {"answer": answer, "elapsed_ms": elapsed_ms}


@app.get("/api/history")
def get_history(user: dict = Depends(require_auth)):
    """Список сохранённых анализов текущего пользователя."""
    return hist.list_analyses(user_id=user["id"])


@app.get("/api/history/{record_id}")
def get_history_item(record_id: str, user: dict = Depends(require_auth)):
    """Возвращает результат анализа и создаёт Q&A сессию."""
    item = hist.get_analysis(record_id)
    if not item or (item["user_id"] and item["user_id"] != user["id"]):
        raise HTTPException(status_code=404, detail="Запись не найдена")

    config = _get_config(None, user["id"])
    session_id = _make_session(config, item["document_text"])

    return {
        "id": item["id"],
        "filename": item["filename"],
        "uploaded_at": item["uploaded_at"],
        "char_count": item["char_count"],
        "result_markdown": item["result_markdown"],
        "session_id": session_id,
    }


@app.post("/api/history/{record_id}/reanalyze")
async def reanalyze_history_item(record_id: str, user: dict = Depends(require_auth)):
    """Повторный анализ документа из истории (стримит SSE, обновляет запись)."""
    item = hist.get_analysis(record_id)
    if not item or (item["user_id"] and item["user_id"] != user["id"]):
        raise HTTPException(status_code=404, detail="Запись не найдена")

    config = _get_config(None, user["id"])
    text = item["document_text"]
    filename = item["filename"]

    def on_done(markdown: str):
        hist.update_analysis(record_id, markdown)

    generate = _build_analysis_generator(config, text, filename, False, on_done)

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.delete("/api/history/{record_id}", status_code=204)
def delete_history_item(record_id: str, user: dict = Depends(require_auth)):
    """Удаляет запись из истории."""
    item = hist.get_analysis(record_id)
    if not item or (item["user_id"] and item["user_id"] != user["id"]):
        raise HTTPException(status_code=404, detail="Запись не найдена")
    if not hist.delete_analysis(record_id):
        raise HTTPException(status_code=404, detail="Запись не найдена")


# Serve frontend (должно быть последним)
app.mount("/", StaticFiles(directory="../frontend", html=True), name="frontend")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=True)
