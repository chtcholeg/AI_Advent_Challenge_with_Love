"""
JuriLytics — FastAPI backend (Фаза 2).

Endpoints:
  POST /api/login               — вход, устанавливает httpOnly cookie
  POST /api/logout              — выход, удаляет сессию
  GET  /api/me                  — текущий пользователь
  GET  /api/admin/users         — список пользователей (только admin)
  POST /api/admin/users         — создать пользователя (только admin)
  DELETE /api/admin/users/{u}   — удалить пользователя (только admin)
  POST /api/analyze             — загружает файл (.txt / .pdf), стримит прогресс через SSE
  POST /api/ask                 — Q&A вопрос по документу (с историей диалога)
  GET  /api/history             — список сохранённых анализов
  GET  /api/history/{id}        — детали анализа + создание Q&A сессии
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
from agent_runner import aggregate, classify, run_all_agents
from client import DocumentAnalyzer
from reader import read_file

load_dotenv()
hist.init_db()
auth.init_auth_db()

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

# Хранилище Q&A сессий: session_id -> (DocumentAnalyzer, created_at)
# Сессии автоматически удаляются через SESSION_TTL_SECONDS секунд.
SESSION_TTL_SECONDS = 2 * 60 * 60  # 2 часа
_sessions_lock = threading.Lock()
sessions: dict[str, tuple[DocumentAnalyzer, float]] = {}


def _cleanup_sessions() -> None:
    """Удаляет Q&A сессии, которые не использовались дольше SESSION_TTL_SECONDS."""
    now = time.monotonic()
    with _sessions_lock:
        expired = [sid for sid, (_, ts) in sessions.items() if now - ts > SESSION_TTL_SECONDS]
        for sid in expired:
            del sessions[sid]
    if expired:
        logger.info("Session cleanup: удалено %d устаревших сессий, осталось %d", len(expired), len(sessions))


def _session_cleanup_worker() -> None:
    while True:
        time.sleep(1800)  # каждые 30 минут
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

AVAILABLE_MODELS = [
    "GigaChat",
    "GigaChat-Plus",
    "GigaChat-Pro",
    "GigaChat-Max",
]


def _load_credentials(model_override: str | None = None) -> tuple[str, str]:
    credentials = os.getenv("GIGACHAT_AUTHORIZATION_KEY")
    default_model = os.getenv("GIGACHAT_MODEL", "GigaChat")
    model = model_override if model_override in AVAILABLE_MODELS else default_model
    if not credentials:
        raise HTTPException(
            status_code=500,
            detail="GIGACHAT_AUTHORIZATION_KEY не найден. Создайте .env файл в backend/",
        )
    return credentials, model


def _sse(data: dict) -> str:
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


def _make_session(credentials: str, model: str, document_text: str) -> str:
    session_id = str(uuid.uuid4())
    with _sessions_lock:
        sessions[session_id] = (
            DocumentAnalyzer(credentials=credentials, model=model, document=document_text),
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


# ── API routes ─────────────────────────────────────────────────────────────────

@app.get("/api/models")
def get_models(user: dict = Depends(require_auth)):
    """Список доступных языковых моделей."""
    return {"models": AVAILABLE_MODELS}


@app.post("/api/analyze")
async def analyze(
    file: UploadFile = File(...),
    model: str | None = Form(None),
    user: dict = Depends(require_auth),
):
    """Анализирует документ, стримит прогресс через SSE."""
    credentials, model = _load_credentials(model)

    content = await file.read()
    filename = file.filename or "document.txt"

    try:
        text, truncated = read_file(filename, content)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    if not text.strip():
        raise HTTPException(status_code=400, detail="Файл пустой или не содержит текста")

    async def generate():
        loop = asyncio.get_running_loop()
        queue: asyncio.Queue = asyncio.Queue()

        yield _sse({"type": "meta", "filename": filename, "chars": len(text), "truncated": truncated})

        def progress_callback(event: dict):
            asyncio.run_coroutine_threadsafe(queue.put(event), loop)

        def run_analysis():
            try:
                doc_class = classify(credentials, model, text, progress_callback)

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
                    credentials, model, text, progress_callback,
                    active_agents=doc_class["relevant_agents"],
                    context=doc_class["context"],
                )
                progress_callback({"type": "aggregating"})
                table = aggregate(credentials, model, text, results, doc_class.get("context", ""))
                progress_callback({"type": "done", "markdown": table})
            except Exception as e:
                logger.exception("Analysis error")
                progress_callback({"type": "error", "message": str(e)})

        threading.Thread(target=run_analysis, daemon=True).start()

        while True:
            event = await queue.get()
            yield _sse(event)

            if event["type"] == "done":
                hist.save_analysis(filename, len(text), event["markdown"], text, user_id=user["id"])
                session_id = _make_session(credentials, model, text)
                yield _sse({"type": "session", "session_id": session_id})
                break

            if event["type"] == "error":
                break

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.post("/api/ask")
async def ask(body: AskRequest, user: dict = Depends(require_auth)):
    """Задаёт вопрос по ранее загруженному документу."""
    with _sessions_lock:
        entry = sessions.get(body.session_id)
    if not entry:
        raise HTTPException(status_code=404, detail="Сессия не найдена. Загрузите документ снова.")
    analyzer, _ = entry

    if not body.question.strip():
        raise HTTPException(status_code=400, detail="Вопрос не может быть пустым")

    loop = asyncio.get_running_loop()
    answer = await loop.run_in_executor(None, analyzer.ask, body.question)
    return {"answer": answer}


@app.get("/api/history")
def get_history(user: dict = Depends(require_auth)):
    """Список сохранённых анализов текущего пользователя (без текста документа)."""
    return hist.list_analyses(user_id=user["id"])


@app.get("/api/history/{record_id}")
def get_history_item(record_id: str, model: str | None = None, user: dict = Depends(require_auth)):
    """Возвращает результат анализа и создаёт Q&A сессию из сохранённого документа."""
    item = hist.get_analysis(record_id)
    if not item or (item["user_id"] and item["user_id"] != user["id"]):
        raise HTTPException(status_code=404, detail="Запись не найдена")

    credentials, model = _load_credentials(model)
    session_id = _make_session(credentials, model, item["document_text"])

    return {
        "id": item["id"],
        "filename": item["filename"],
        "uploaded_at": item["uploaded_at"],
        "char_count": item["char_count"],
        "result_markdown": item["result_markdown"],
        "session_id": session_id,
    }


@app.post("/api/history/{record_id}/reanalyze")
async def reanalyze_history_item(
    record_id: str, model: str | None = None, user: dict = Depends(require_auth)
):
    """Повторный анализ документа из истории (стримит SSE, обновляет запись)."""
    item = hist.get_analysis(record_id)
    if not item or (item["user_id"] and item["user_id"] != user["id"]):
        raise HTTPException(status_code=404, detail="Запись не найдена")

    text = item["document_text"]
    filename = item["filename"]
    credentials, model = _load_credentials(model)

    async def generate():
        loop = asyncio.get_running_loop()
        queue: asyncio.Queue = asyncio.Queue()

        yield _sse({"type": "meta", "filename": filename, "chars": len(text), "truncated": False})

        def progress_callback(event: dict):
            asyncio.run_coroutine_threadsafe(queue.put(event), loop)

        def run_analysis():
            try:
                doc_class = classify(credentials, model, text, progress_callback)

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
                    credentials, model, text, progress_callback,
                    active_agents=doc_class["relevant_agents"],
                    context=doc_class["context"],
                )
                progress_callback({"type": "aggregating"})
                table = aggregate(credentials, model, text, results, doc_class.get("context", ""))
                progress_callback({"type": "done", "markdown": table})
            except Exception as e:
                logger.exception("Reanalysis error")
                progress_callback({"type": "error", "message": str(e)})

        threading.Thread(target=run_analysis, daemon=True).start()

        while True:
            event = await queue.get()
            yield _sse(event)

            if event["type"] == "done":
                hist.update_analysis(record_id, event["markdown"])
                session_id = _make_session(credentials, model, text)
                yield _sse({"type": "session", "session_id": session_id})
                break

            if event["type"] == "error":
                break

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.delete("/api/history/{record_id}", status_code=204)
def delete_history_item(record_id: str, user: dict = Depends(require_auth)):
    """Удаляет запись из истории (только свои записи)."""
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
