import asyncio
import pathlib

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

load_dotenv(pathlib.Path(__file__).parent / ".env")

from extractor import list_models, run_extraction
from prompts import SAMPLE_PROMPTS, SAMPLE_TEXTS

app = FastAPI(title="LLM Parameter Lab")

FRONTEND = pathlib.Path(__file__).parent.parent / "frontend"


# ── models ──────────────────────────────────────────────────────────────────

@app.get("/api/models")
async def get_models():
    try:
        models = await list_models()
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Ollama недоступен: {e}")
    return {"models": models}


# ── prompts & samples ────────────────────────────────────────────────────────

@app.get("/api/prompts")
async def get_prompts():
    return {"prompts": SAMPLE_PROMPTS}


@app.get("/api/samples")
async def get_samples():
    return {"samples": SAMPLE_TEXTS}


# ── extraction ───────────────────────────────────────────────────────────────

class ExtractionConfig(BaseModel):
    text: str
    model: str
    temperature: float = Field(ge=0.0, le=2.0)
    num_ctx: int = Field(ge=128, le=131072)
    max_tokens: int = Field(ge=-1)
    system_prompt: str = ""


class BatchRequest(BaseModel):
    text: str
    configs: list[ExtractionConfig]


@app.post("/api/extract")
async def extract_single(cfg: ExtractionConfig):
    result = await run_extraction(
        text=cfg.text,
        model=cfg.model,
        temperature=cfg.temperature,
        num_ctx=cfg.num_ctx,
        max_tokens=cfg.max_tokens,
        system_prompt=cfg.system_prompt,
    )
    return result


@app.post("/api/extract/batch")
async def extract_batch(req: BatchRequest):
    tasks = [
        run_extraction(
            text=req.text,
            model=cfg.model,
            temperature=cfg.temperature,
            num_ctx=cfg.num_ctx,
            max_tokens=cfg.max_tokens,
            system_prompt=cfg.system_prompt,
        )
        for cfg in req.configs
    ]
    results = await asyncio.gather(*tasks)
    return {"results": results}


# ── frontend ─────────────────────────────────────────────────────────────────

app.mount("/static", StaticFiles(directory=str(FRONTEND)), name="static")


@app.get("/")
async def index():
    return FileResponse(str(FRONTEND / "index.html"))
