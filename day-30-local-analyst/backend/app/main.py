from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pathlib import Path

from app.db.database import init_db
from app.routers import sessions, upload, analyze, export, settings_router
from app.services.llm_client import LLMClient
from app.config import settings as app_settings

import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    logger.info("Initializing database...")
    await init_db()
    logger.info("Database ready.")

    # Проверяем Ollama при старте
    llm = LLMClient()
    health = await llm.health_check()
    if health["available"]:
        logger.info(f"Ollama is available. Models: {health['models']}")
    else:
        logger.warning(
            f"Ollama is NOT available at {app_settings.ollama_base_url}. "
            "Start it with: `ollama serve`"
        )

    yield
    # Shutdown
    logger.info("Shutting down.")


app = FastAPI(
    title="Local Log Analyst",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# API роутеры
app.include_router(sessions.router)
app.include_router(upload.router)
app.include_router(analyze.router)
app.include_router(export.router)
app.include_router(settings_router.router)


@app.get("/api/health")
async def health():
    return {"status": "ok", "version": "1.0.0"}


# Serve React build in production
static_dir = Path(__file__).parent.parent.parent / "frontend" / "dist"
if static_dir.exists():
    app.mount("/", StaticFiles(directory=str(static_dir), html=True), name="static")
