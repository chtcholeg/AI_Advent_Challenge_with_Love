import asyncio
import json
import os
import time
import httpx

OLLAMA_BASE = "http://localhost:11434"

GIGACHAT_MODELS = [
    {"name": "GigaChat", "size_gb": None, "provider": "gigachat"},
    {"name": "GigaChat-Pro", "size_gb": None, "provider": "gigachat"},
    {"name": "GigaChat-Max", "size_gb": None, "provider": "gigachat"},
]


def is_gigachat(model: str) -> bool:
    return model.startswith("GigaChat")


# ── model listing ─────────────────────────────────────────────────────────────

async def list_models() -> list[dict]:
    ollama_models = await _list_ollama_models()
    gigachat_available = bool(os.getenv("GIGACHAT_AUTHORIZATION_KEY"))
    if gigachat_available:
        return GIGACHAT_MODELS + ollama_models
    return ollama_models


async def _list_ollama_models() -> list[dict]:
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get(f"{OLLAMA_BASE}/api/tags")
            resp.raise_for_status()
            data = resp.json()
            return [
                {
                    "name": m["name"],
                    "size_gb": round(m.get("size", 0) / 1e9, 1),
                    "provider": "ollama",
                }
                for m in data.get("models", [])
                if "embed" not in m["name"]
            ]
    except Exception:
        return []


# ── extraction router ─────────────────────────────────────────────────────────

async def run_extraction(
    text: str,
    model: str,
    temperature: float,
    num_ctx: int,
    max_tokens: int,
    system_prompt: str,
) -> dict:
    if is_gigachat(model):
        return await asyncio.get_event_loop().run_in_executor(
            None,
            _gigachat_extract,
            text, model, temperature, max_tokens, system_prompt,
        )
    return await _ollama_extract(text, model, temperature, num_ctx, max_tokens, system_prompt)


# ── Ollama ────────────────────────────────────────────────────────────────────

async def _ollama_extract(
    text: str,
    model: str,
    temperature: float,
    num_ctx: int,
    max_tokens: int,
    system_prompt: str,
) -> dict:
    messages = []
    if system_prompt.strip():
        messages.append({"role": "system", "content": system_prompt.strip()})
    messages.append({"role": "user", "content": text})

    payload = {
        "model": model,
        "messages": messages,
        "stream": False,
        "options": {
            "temperature": temperature,
            "num_ctx": num_ctx,
        },
    }
    if max_tokens > 0:
        payload["options"]["num_predict"] = max_tokens

    start = time.monotonic()
    try:
        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.post(f"{OLLAMA_BASE}/api/chat", json=payload)
            resp.raise_for_status()
            data = resp.json()
    except httpx.TimeoutException:
        return {"error": "Timeout: модель не ответила за 120 секунд", "elapsed": 0}
    except Exception as e:
        return {"error": str(e), "elapsed": 0}

    elapsed = round(time.monotonic() - start, 2)
    raw = data.get("message", {}).get("content", "")

    parsed, valid, parse_error = _try_parse_json(raw)
    return {
        "raw": raw,
        "parsed": parsed,
        "valid_json": valid,
        "parse_error": parse_error,
        "elapsed": elapsed,
        "tokens_generated": data.get("eval_count"),
        "tokens_prompt": data.get("prompt_eval_count"),
        "provider": "ollama",
    }


# ── GigaChat ──────────────────────────────────────────────────────────────────

def _gigachat_extract(
    text: str,
    model: str,
    temperature: float,
    max_tokens: int,
    system_prompt: str,
) -> dict:
    key = os.getenv("GIGACHAT_AUTHORIZATION_KEY", "")
    if not key:
        return {"error": "GIGACHAT_AUTHORIZATION_KEY не задан в .env", "elapsed": 0}

    try:
        from gigachat import GigaChat
        from gigachat.models import Chat, Messages, MessagesRole
    except ImportError:
        return {"error": "Пакет gigachat не установлен", "elapsed": 0}

    role_map = {
        "system": MessagesRole.SYSTEM,
        "user": MessagesRole.USER,
        "assistant": MessagesRole.ASSISTANT,
    }

    messages = []
    if system_prompt.strip():
        messages.append(Messages(role=MessagesRole.SYSTEM, content=system_prompt.strip()))
    messages.append(Messages(role=MessagesRole.USER, content=text))

    chat_kwargs: dict = {"messages": messages, "temperature": temperature}
    if max_tokens > 0:
        chat_kwargs["max_tokens"] = max_tokens

    start = time.monotonic()
    try:
        with GigaChat(credentials=key, model=model, verify_ssl_certs=False) as giga:
            resp = giga.chat(Chat(**chat_kwargs))
    except Exception as e:
        return {"error": str(e), "elapsed": round(time.monotonic() - start, 2)}

    elapsed = round(time.monotonic() - start, 2)
    raw = resp.choices[0].message.content

    usage = resp.usage
    parsed, valid, parse_error = _try_parse_json(raw)
    return {
        "raw": raw,
        "parsed": parsed,
        "valid_json": valid,
        "parse_error": parse_error,
        "elapsed": elapsed,
        "tokens_generated": getattr(usage, "completion_tokens", None),
        "tokens_prompt": getattr(usage, "prompt_tokens", None),
        "provider": "gigachat",
        "note": "num_ctx не применим для GigaChat",
    }


# ── helpers ───────────────────────────────────────────────────────────────────

def _try_parse_json(text: str) -> tuple[dict | None, bool, str | None]:
    try:
        return json.loads(text.strip()), True, None
    except json.JSONDecodeError:
        pass

    start = text.find("{")
    end = text.rfind("}")
    if start != -1 and end != -1 and end > start:
        candidate = text[start : end + 1]
        try:
            return json.loads(candidate), True, None
        except json.JSONDecodeError as e:
            return None, False, str(e)

    return None, False, "JSON не найден в ответе"
