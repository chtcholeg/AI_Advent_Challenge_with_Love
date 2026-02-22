"""
VPS Manager — объединённый FastAPI backend.

Endpoints:
  GET  /api/last-connection       — последние сохранённые SSH-данные
  GET  /api/config                — дефолтные пути для деплоя
  GET  /api/scenarios             — сценарии VPS Wizard
  POST /api/connect               — SSH-подключение (сохраняет last_connection.json)
  WS   /ws/{session_id}           — операции деплоя (deploy, restart, status, logs)
  WS   /ws/exec/{session_id}      — выполнение шагов мастера
  GET  /api/download/{session_id} — скачать файл с VPS через SFTP
  GET  /                          — frontend (static files)
"""

import asyncio
import io
import json
import logging
import os
import re
import threading
from pathlib import Path
from typing import Optional

import paramiko
from fastapi import FastAPI, HTTPException, Query, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from scenarios import SCENARIOS

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="VPS Manager", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

# Единый словарь SSH-сессий: session_id -> {client, host, username}
sessions: dict = {}

# Файл для хранения последних данных подключения
LAST_CONNECTION_FILE = Path(__file__).parent / "last_connection.json"


def _save_last_connection(data: dict) -> None:
    try:
        LAST_CONNECTION_FILE.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    except Exception:
        pass


def _load_last_connection() -> dict:
    try:
        if LAST_CONNECTION_FILE.exists():
            return json.loads(LAST_CONNECTION_FILE.read_text(encoding="utf-8"))
    except Exception:
        pass
    return {}


# Директории и файлы, которые не загружаем на сервер
SKIP_DIRS  = {".venv", "__pycache__", ".git", "node_modules", ".mypy_cache",
              ".pytest_cache", ".ruff_cache", "dist", "build"}
SKIP_FILES = {".env", ".DS_Store", "Thumbs.db"}
SKIP_EXTS  = {".pyc", ".pyo", ".db", ".sqlite", ".sqlite3"}


# ── Pydantic models ────────────────────────────────────────────────────────────

class ConnectRequest(BaseModel):
    host: str
    port: int = 22
    username: str
    password: Optional[str] = None
    private_key: Optional[str] = None


# ── API routes ─────────────────────────────────────────────────────────────────

@app.get("/api/last-connection")
def get_last_connection():
    """Возвращает последние сохранённые данные SSH-подключения."""
    return _load_last_connection()


@app.get("/api/config")
def get_config():
    """Возвращает дефолтные пути для формы деплоя."""
    here = Path(__file__).resolve().parent.parent.parent  # day-25-real-task/
    local = here / "web-app"
    return {
        "local_path": str(local),
        "remote_path": "/opt/jurilytics",
        "service": "jurilytics",
    }


@app.get("/api/scenarios")
def get_scenarios():
    """Возвращает сценарии без команд (они хранятся только на бэкенде)."""
    result = {}
    for key, scenario in SCENARIOS.items():
        result[key] = {
            "name": scenario["name"],
            "subtitle": scenario["subtitle"],
            "description": scenario["description"],
            "icon": scenario["icon"],
            "steps": [
                {
                    "id": step["id"],
                    "name": step["name"],
                    "description": step["description"],
                    "type": step["type"],
                    "inputs": step.get("inputs", []),
                    "info_text": step.get("info_text", ""),
                    "download_file": step.get("download_file", ""),
                    "download_name": step.get("download_name", ""),
                    "skippable": step.get("skippable", False),
                    "error_hints": _safe_hints(step.get("error_hints", [])),
                }
                for step in scenario["steps"]
            ],
        }
    return result


@app.post("/api/connect")
def connect_ssh(req: ConnectRequest):
    """Создаёт SSH-сессию и возвращает session_id."""
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        kwargs: dict = {
            "hostname": req.host,
            "port": req.port,
            "username": req.username,
            "timeout": 15,
            "banner_timeout": 15,
            "look_for_keys": False,
            "allow_agent": False,
        }
        if req.private_key:
            try:
                pkey = paramiko.Ed25519Key.from_private_key(io.StringIO(req.private_key))
            except Exception:
                pkey = paramiko.RSAKey.from_private_key(io.StringIO(req.private_key))
            kwargs["pkey"] = pkey
        elif req.password:
            kwargs["password"] = req.password
        else:
            return {"success": False, "error": "Укажите пароль или приватный ключ"}

        client.connect(**kwargs)

        _, stdout, _ = client.exec_command("echo __ok__")
        if stdout.read().decode().strip() != "__ok__":
            return {"success": False, "error": "Сервер не отвечает на команды"}

        session_id = f"{req.host}:{req.port}:{req.username}"
        if session_id in sessions:
            try:
                sessions[session_id]["client"].close()
            except Exception:
                pass
        sessions[session_id] = {"client": client, "host": req.host, "username": req.username}
        logger.info("SSH session opened: %s", session_id)
        _save_last_connection({
            "host": req.host,
            "port": req.port,
            "username": req.username,
        })
        return {"success": True, "session_id": session_id}

    except paramiko.AuthenticationException:
        return {"success": False, "error": "Неверный логин или пароль / ключ отклонён"}
    except paramiko.SSHException as e:
        return {"success": False, "error": f"SSH ошибка: {e}"}
    except OSError as e:
        return {"success": False, "error": f"Не удалось подключиться: {e}"}
    except Exception as e:
        logger.exception("Connect error")
        return {"success": False, "error": str(e)}


# ── Wizard helpers ─────────────────────────────────────────────────────────────

def _safe_hints(hints: list) -> list:
    """Возвращает подсказки без fix_commands (не передаём команды на фронтенд)."""
    result = []
    for i, h in enumerate(hints):
        result.append({
            "title": h["title"],
            "hint": h["hint"],
            "fix_id": i if "fix_commands" in h else None,
        })
    return result


def _substitute(template: str, context: dict) -> str:
    """Подставляет {key} из context. Неизвестные {key} оставляет как есть."""
    def repl(m: re.Match) -> str:
        key = m.group(1)
        return str(context[key]) if key in context else m.group(0)
    return re.sub(r'\{([A-Za-z_]\w*)\}', repl, template)


async def _stream_ssh(websocket: WebSocket, client: paramiko.SSHClient, commands: list, context: dict) -> int:
    """Выполняет команды по SSH, стримит stdout/stderr в WebSocket. Возвращает exit code."""
    subst = [_substitute(cmd, context) for cmd in commands]
    script = "\n".join(["#!/usr/bin/env bash", "set -euo pipefail", ""] + subst)

    transport = client.get_transport()
    channel = transport.open_session()
    channel.set_combine_stderr(False)
    channel.exec_command("bash -s")
    channel.sendall(script.encode())
    channel.shutdown_write()

    while True:
        if channel.recv_ready():
            await websocket.send_json({"type": "stdout", "data": channel.recv(4096).decode("utf-8", errors="replace")})
        if channel.recv_stderr_ready():
            await websocket.send_json({"type": "stderr", "data": channel.recv_stderr(4096).decode("utf-8", errors="replace")})
        if channel.exit_status_ready():
            while channel.recv_ready():
                await websocket.send_json({"type": "stdout", "data": channel.recv(4096).decode("utf-8", errors="replace")})
            while channel.recv_stderr_ready():
                await websocket.send_json({"type": "stderr", "data": channel.recv_stderr(4096).decode("utf-8", errors="replace")})
            break
        await asyncio.sleep(0.05)

    exit_code = channel.recv_exit_status()
    channel.close()
    return exit_code


# ── Deploy helpers ─────────────────────────────────────────────────────────────

async def _exec_script(websocket: WebSocket, client: paramiko.SSHClient, script: str) -> int:
    """Выполняет bash-скрипт по SSH (stdin), стримит вывод в WebSocket."""
    transport = client.get_transport()
    channel = transport.open_session()
    channel.set_combine_stderr(False)
    channel.exec_command("bash -s")
    channel.sendall(script.encode())
    channel.shutdown_write()

    while True:
        if channel.recv_ready():
            await websocket.send_json({"type": "output",
                                       "data": channel.recv(4096).decode("utf-8", errors="replace")})
        if channel.recv_stderr_ready():
            await websocket.send_json({"type": "output",
                                       "data": channel.recv_stderr(4096).decode("utf-8", errors="replace"),
                                       "stderr": True})
        if channel.exit_status_ready():
            while channel.recv_ready():
                await websocket.send_json({"type": "output",
                                           "data": channel.recv(4096).decode("utf-8", errors="replace")})
            while channel.recv_stderr_ready():
                await websocket.send_json({"type": "output",
                                           "data": channel.recv_stderr(4096).decode("utf-8", errors="replace"),
                                           "stderr": True})
            break
        await asyncio.sleep(0.05)

    exit_code = channel.recv_exit_status()
    channel.close()
    return exit_code


async def _stream_command(websocket: WebSocket, client: paramiko.SSHClient, command: str) -> int:
    """Выполняет команду по SSH и стримит вывод в WebSocket."""
    transport = client.get_transport()
    channel = transport.open_session()
    channel.set_combine_stderr(False)
    channel.exec_command(command)

    while True:
        if channel.recv_ready():
            await websocket.send_json({"type": "output",
                                       "data": channel.recv(4096).decode("utf-8", errors="replace")})
        if channel.recv_stderr_ready():
            await websocket.send_json({"type": "output",
                                       "data": channel.recv_stderr(4096).decode("utf-8", errors="replace"),
                                       "stderr": True})
        if channel.exit_status_ready():
            while channel.recv_ready():
                await websocket.send_json({"type": "output",
                                           "data": channel.recv(4096).decode("utf-8", errors="replace")})
            while channel.recv_stderr_ready():
                await websocket.send_json({"type": "output",
                                           "data": channel.recv_stderr(4096).decode("utf-8", errors="replace"),
                                           "stderr": True})
            break
        await asyncio.sleep(0.05)

    exit_code = channel.recv_exit_status()
    channel.close()
    return exit_code


def _makedirs_sftp(sftp: paramiko.SFTPClient, path: str) -> None:
    """Создаёт директорию и все родительские на удалённом сервере."""
    parts = [p for p in path.replace("\\", "/").split("/") if p]
    current = ""
    for part in parts:
        current += "/" + part
        try:
            sftp.mkdir(current)
        except IOError:
            pass


async def _sftp_upload(
    websocket: WebSocket,
    client: paramiko.SSHClient,
    local_dir: str,
    remote_dir: str,
) -> bool:
    """Загружает local_dir → remote_dir через SFTP, стримит прогресс."""
    loop = asyncio.get_event_loop()
    queue: asyncio.Queue = asyncio.Queue()

    def do_upload():
        try:
            local_path = Path(local_dir.rstrip("/\\"))
            if not local_path.exists():
                asyncio.run_coroutine_threadsafe(
                    queue.put({"type": "output",
                               "data": f"ОШИБКА: путь не найден: {local_dir}\n",
                               "stderr": True}),
                    loop,
                )
                asyncio.run_coroutine_threadsafe(queue.put({"_done": False}), loop)
                return

            file_list = []
            for root, dirs, files in os.walk(str(local_path)):
                dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
                rel_root = os.path.relpath(root, str(local_path))
                remote_root = (
                    remote_dir if rel_root == "."
                    else f"{remote_dir}/{rel_root.replace(os.sep, '/')}"
                )
                for fname in files:
                    if fname in SKIP_FILES or any(fname.endswith(e) for e in SKIP_EXTS):
                        continue
                    display = fname if rel_root == "." else f"{rel_root}/{fname}"
                    file_list.append((
                        os.path.join(root, fname),
                        f"{remote_root}/{fname}",
                        display,
                    ))

            total = len(file_list)
            asyncio.run_coroutine_threadsafe(
                queue.put({"type": "output", "data": f"Файлов для загрузки: {total}\n"}),
                loop,
            )

            sftp = client.open_sftp()

            remote_dirs = sorted({os.path.dirname(r).replace("\\", "/")
                                   for _, r, _ in file_list})
            for d in remote_dirs:
                _makedirs_sftp(sftp, d)

            uploaded = errors = 0
            for local_file, remote_file, display in file_list:
                try:
                    sftp.put(local_file, remote_file)
                    uploaded += 1
                    asyncio.run_coroutine_threadsafe(
                        queue.put({"type": "output",
                                   "data": f"  [{uploaded}/{total}] {display}\n"}),
                        loop,
                    )
                except Exception as e:
                    errors += 1
                    asyncio.run_coroutine_threadsafe(
                        queue.put({"type": "output",
                                   "data": f"  ОШИБКА {display}: {e}\n",
                                   "stderr": True}),
                        loop,
                    )

            sftp.close()
            asyncio.run_coroutine_threadsafe(
                queue.put({"type": "output",
                           "data": f"\nЗагружено: {uploaded}, ошибок: {errors}\n"}),
                loop,
            )
            asyncio.run_coroutine_threadsafe(queue.put({"_done": errors == 0}), loop)

        except Exception as e:
            logger.exception("SFTP upload error")
            asyncio.run_coroutine_threadsafe(
                queue.put({"type": "output", "data": f"Ошибка SFTP: {e}\n", "stderr": True}),
                loop,
            )
            asyncio.run_coroutine_threadsafe(queue.put({"_done": False}), loop)

    threading.Thread(target=do_upload, daemon=True).start()

    while True:
        msg = await queue.get()
        if "_done" in msg:
            return msg["_done"]
        await websocket.send_json(msg)


async def _update_env_admin(
    websocket: WebSocket,
    client: paramiko.SSHClient,
    remote_path: str,
    admin_username: str,
    admin_password: str,
) -> None:
    """Записывает ADMIN_USERNAME / ADMIN_PASSWORD в .env на сервере через SFTP."""
    loop = asyncio.get_event_loop()

    def do_update():
        sftp = client.open_sftp()
        try:
            env_path = f"{remote_path}/backend/.env"
            try:
                with sftp.open(env_path, "r") as f:
                    content = f.read().decode("utf-8", errors="replace")
            except IOError:
                content = ""

            for key, val in [("ADMIN_USERNAME", admin_username), ("ADMIN_PASSWORD", admin_password)]:
                if re.search(rf"^{key}=", content, re.MULTILINE):
                    content = re.sub(rf"^{key}=.*", f"{key}={val}", content, flags=re.MULTILINE)
                else:
                    if content and not content.endswith("\n"):
                        content += "\n"
                    content += f"{key}={val}\n"

            with sftp.open(env_path, "w") as f:
                f.write(content.encode("utf-8"))
        finally:
            sftp.close()

    await loop.run_in_executor(None, do_update)
    await websocket.send_json({"type": "output", "data": "ADMIN_USERNAME и ADMIN_PASSWORD записаны в .env\n"})


# ── WebSocket: VPS Wizard ──────────────────────────────────────────────────────

@app.websocket("/ws/exec/{session_id}")
async def ws_exec(websocket: WebSocket, session_id: str):
    """
    Клиент → сервер:
      {"type": "exec", "scenario": "vpn", "step_id": "sys_update", "context": {...}}
      {"type": "fix",  "scenario": "vpn", "step_id": "setup_fail2ban", "fix_id": 0, "context": {...}}
      {"type": "ping"}

    Сервер → клиент:
      {"type": "stdout",   "data": "..."}
      {"type": "stderr",   "data": "..."}
      {"type": "done",     "step_id": "...", "exit_code": 0, "success": true}
      {"type": "fix_done", "step_id": "...", "fix_id": 0,   "exit_code": 0, "success": true}
      {"type": "error",    "message": "..."}
      {"type": "pong"}
    """
    await websocket.accept()

    if session_id not in sessions:
        await websocket.send_json({"type": "error", "message": "Сессия не найдена. Подключитесь снова."})
        await websocket.close()
        return

    client: paramiko.SSHClient = sessions[session_id]["client"]

    try:
        while True:
            msg = await websocket.receive_json()
            msg_type = msg.get("type")

            if msg_type == "ping":
                await websocket.send_json({"type": "pong"})
                continue

            scenario_key = msg.get("scenario")
            step_id = msg.get("step_id")
            context: dict = msg.get("context", {})

            scenario = SCENARIOS.get(scenario_key)
            if not scenario:
                await websocket.send_json({"type": "error", "message": f"Неизвестный сценарий: {scenario_key}"})
                continue

            step = next((s for s in scenario["steps"] if s["id"] == step_id), None)
            if not step:
                await websocket.send_json({"type": "error", "message": f"Шаг не найден: {step_id}"})
                continue

            if msg_type == "exec":
                if step["type"] in ("info", "download"):
                    await websocket.send_json({"type": "done", "step_id": step_id, "exit_code": 0, "success": True})
                    continue

                logger.info("Executing step %s for session %s", step_id, session_id)
                exit_code = await _stream_ssh(websocket, client, step.get("commands", []), context)
                await websocket.send_json({
                    "type": "done",
                    "step_id": step_id,
                    "exit_code": exit_code,
                    "success": exit_code == 0,
                })

            elif msg_type == "fix":
                fix_id = msg.get("fix_id")
                hints = step.get("error_hints", [])
                if fix_id is None or fix_id >= len(hints) or "fix_commands" not in hints[fix_id]:
                    await websocket.send_json({"type": "error", "message": "Исправление не найдено"})
                    continue

                logger.info("Applying fix %s/%s for session %s", step_id, fix_id, session_id)
                exit_code = await _stream_ssh(websocket, client, hints[fix_id]["fix_commands"], context)
                await websocket.send_json({
                    "type": "fix_done",
                    "step_id": step_id,
                    "fix_id": fix_id,
                    "exit_code": exit_code,
                    "success": exit_code == 0,
                })

    except WebSocketDisconnect:
        logger.info("WebSocket disconnected: %s", session_id)
    except Exception as e:
        logger.exception("WebSocket error for session %s", session_id)
        try:
            await websocket.send_json({"type": "error", "message": str(e)})
        except Exception:
            pass


# ── WebSocket: Deploy Manager ──────────────────────────────────────────────────

@app.websocket("/ws/{session_id}")
async def ws_deploy(websocket: WebSocket, session_id: str):
    """
    Клиент → сервер:
      {"type": "deploy",          "local_path": "...", "remote_path": "...", "service": "..."}
      {"type": "restart",         "service": "..."}
      {"type": "status",          "service": "..."}
      {"type": "logs",            "service": "...", "lines": 100}
      {"type": "install_service", "remote_path": "...", "service_user": "root"}
      {"type": "ping"}

    Сервер → клиент:
      {"type": "output", "data": "...", "stderr": false}
      {"type": "done",   "success": true}
      {"type": "error",  "message": "..."}
      {"type": "pong"}
    """
    await websocket.accept()

    if session_id not in sessions:
        await websocket.send_json({"type": "error", "message": "Сессия не найдена. Подключитесь снова."})
        await websocket.close()
        return

    client: paramiko.SSHClient = sessions[session_id]["client"]

    try:
        while True:
            msg = await websocket.receive_json()
            op = msg.get("type")

            if op == "ping":
                await websocket.send_json({"type": "pong"})
                continue

            service = msg.get("service", "jurilytics").strip() or "jurilytics"

            if op == "status":
                await websocket.send_json({"type": "output", "data": f"$ systemctl status {service}\n"})
                rc = await _stream_command(websocket, client, f"systemctl status {service} --no-pager -l")
                await websocket.send_json({"type": "done", "success": rc == 0})

            elif op == "restart":
                await websocket.send_json({"type": "output", "data": f"$ systemctl restart {service}\n"})
                rc = await _stream_command(websocket, client, f"systemctl restart {service}")
                if rc == 0:
                    await websocket.send_json({"type": "output",
                                               "data": f"Сервис {service} успешно перезапущен.\n"})
                await websocket.send_json({"type": "done", "success": rc == 0})

            elif op == "logs":
                lines = int(msg.get("lines", 100))
                cmd = f"journalctl -u {service} -n {lines} --no-pager"
                await websocket.send_json({"type": "output", "data": f"$ {cmd}\n"})
                rc = await _stream_command(websocket, client, cmd)
                await websocket.send_json({"type": "done", "success": rc == 0})

            elif op == "deploy":
                local_path     = msg.get("local_path", "").strip()
                remote_path    = msg.get("remote_path", "").strip()
                run_pip        = msg.get("run_pip", True)
                admin_username = msg.get("admin_username", "").strip()
                admin_password = msg.get("admin_password", "").strip()

                if not local_path or not remote_path:
                    await websocket.send_json({"type": "error",
                                               "message": "Укажите локальный и удалённый пути"})
                    continue

                await websocket.send_json({"type": "output", "data": (
                    f"=== Синхронизация файлов ===\n"
                    f"Локально:   {local_path}\n"
                    f"На сервере: {remote_path}\n\n"
                )})

                upload_ok = await _sftp_upload(websocket, client, local_path, remote_path)

                if upload_ok and admin_username and admin_password:
                    await websocket.send_json({"type": "output",
                                               "data": "\n=== Обновление .env (admin credentials) ===\n"})
                    try:
                        await _update_env_admin(websocket, client, remote_path,
                                                admin_username, admin_password)
                    except Exception as e:
                        await websocket.send_json({"type": "output",
                                                   "data": f"Предупреждение: не удалось обновить .env: {e}\n",
                                                   "stderr": True})

                if upload_ok and run_pip:
                    venv_dir = f"{remote_path}/backend/.venv"
                    setup_script = (
                        "#!/usr/bin/env bash\n"
                        "set -e\n"
                        f"if [ ! -f {venv_dir}/bin/pip ]; then\n"
                        f"  echo 'Создаю virtualenv...'\n"
                        f"  python3 -m venv {venv_dir}\n"
                        f"fi\n"
                        f"{venv_dir}/bin/pip install -q"
                        f" -r {remote_path}/backend/requirements.txt\n"
                    )
                    await websocket.send_json({"type": "output",
                                               "data": "\n=== Установка зависимостей ===\n"})
                    rc = await _exec_script(websocket, client, setup_script)
                    if rc != 0:
                        await websocket.send_json({"type": "output",
                                                   "data": "Предупреждение: pip install завершился с ошибкой\n",
                                                   "stderr": True})

                if upload_ok:
                    await websocket.send_json({"type": "output",
                                               "data": f"\n$ systemctl restart {service}\n"})
                    rc = await _stream_command(websocket, client, f"systemctl restart {service}")
                    if rc == 0:
                        await websocket.send_json({"type": "output",
                                                   "data": f"Сервис {service} перезапущен успешно.\n"})
                    else:
                        _, chk_out, _ = client.exec_command(
                            f"systemctl list-unit-files {service}.service 2>&1"
                        )
                        chk = chk_out.read().decode()
                        if service not in chk:
                            await websocket.send_json({"type": "output",
                                                       "data": (
                                                           f"Сервис {service} не установлен.\n"
                                                           f"Используйте кнопку «Установить сервис» для первоначальной настройки.\n"
                                                       ),
                                                       "stderr": True})
                    await websocket.send_json({"type": "done", "success": rc == 0})
                else:
                    await websocket.send_json({"type": "done", "success": False})

            elif op == "install_service":
                remote_path  = msg.get("remote_path", "").strip().rstrip("/")
                service_user = msg.get("service_user", "root").strip() or "root"

                if not remote_path:
                    await websocket.send_json({"type": "error",
                                               "message": "Укажите путь на сервере"})
                    continue

                svc_file = f"/etc/systemd/system/{service}.service"
                await websocket.send_json({"type": "output", "data": (
                    f"=== Установка systemd-сервиса ===\n"
                    f"Файл:      {svc_file}\n"
                    f"ExecStart: {remote_path}/backend/.venv/bin/python main.py\n"
                    f"User:      {service_user}\n\n"
                )})

                script = (
                    "#!/usr/bin/env bash\n"
                    "set -euo pipefail\n\n"
                    f"if [ ! -f {remote_path}/backend/.venv/bin/pip ]; then\n"
                    f"  echo 'Создаю virtualenv...'\n"
                    f"  python3 -m venv {remote_path}/backend/.venv\n"
                    f"fi\n\n"
                    f"echo 'Устанавливаю зависимости...'\n"
                    f"{remote_path}/backend/.venv/bin/pip install -q -r {remote_path}/backend/requirements.txt\n\n"
                    f"cat > {svc_file} << 'SVCEOF'\n"
                    "[Unit]\n"
                    f"Description={service}\n"
                    "After=network.target\n\n"
                    "[Service]\n"
                    "Type=simple\n"
                    f"User={service_user}\n"
                    f"WorkingDirectory={remote_path}/backend\n"
                    f"ExecStart={remote_path}/backend/.venv/bin/python main.py\n"
                    "Restart=on-failure\n"
                    "RestartSec=5\n"
                    "Environment=PYTHONUNBUFFERED=1\n\n"
                    "[Install]\n"
                    "WantedBy=multi-user.target\n"
                    "SVCEOF\n\n"
                    f"echo 'Service file written: {svc_file}'\n"
                    "systemctl daemon-reload\n"
                    f"systemctl enable {service}\n"
                    f"systemctl start {service}\n"
                    "sleep 1\n"
                    f"systemctl status {service} --no-pager -l\n"
                )

                rc = await _exec_script(websocket, client, script)
                await websocket.send_json({"type": "done", "success": rc == 0})

            elif op == "open_ports":
                port_web     = msg.get("port_web", "нет")
                port_openvpn = msg.get("port_openvpn", "нет")
                # strip anything not suitable for a ufw allow argument
                port_custom  = re.sub(r"[^\d/a-z,\s]", "", msg.get("port_custom", "").strip().lower())

                lines = ["#!/usr/bin/env bash", "set -e", ""]
                if port_web == "да":
                    lines += [
                        "ufw allow 80/tcp comment 'HTTP'",
                        "ufw allow 443/tcp comment 'HTTPS'",
                        "echo '→ Порты 80/TCP и 443/TCP (HTTP/HTTPS) открыты'",
                    ]
                if port_openvpn == "да":
                    lines += [
                        "ufw allow 1194/udp comment 'OpenVPN'",
                        "echo '→ Порт 1194/UDP (OpenVPN) открыт'",
                    ]
                if port_custom:
                    lines += [
                        f"echo '{port_custom}' | tr ',' '\\n' | while IFS= read -r _p; do",
                        '  _p=$(echo "$_p" | xargs)',
                        '  [ -n "$_p" ] && ufw allow $_p comment \'Custom\' && echo "→ Порт $_p открыт"',
                        "done",
                    ]
                lines.append("ufw status verbose")
                script = "\n".join(lines)

                await websocket.send_json({"type": "output", "data": "=== Обновление правил UFW ===\n"})
                rc = await _exec_script(websocket, client, script)
                await websocket.send_json({"type": "done", "success": rc == 0})

    except WebSocketDisconnect:
        logger.info("WebSocket disconnected: %s", session_id)
    except Exception as e:
        logger.exception("WebSocket error for session %s", session_id)
        try:
            await websocket.send_json({"type": "error", "message": str(e)})
        except Exception:
            pass


@app.get("/api/download/{session_id}")
def download_file(session_id: str, file_path: str = Query(..., description="Путь к файлу на сервере")):
    """Скачивает файл с VPS через SFTP и отдаёт браузеру."""
    if session_id not in sessions:
        raise HTTPException(status_code=404, detail="Сессия не найдена")

    client: paramiko.SSHClient = sessions[session_id]["client"]
    try:
        sftp = client.open_sftp()
        with sftp.file(file_path, "rb") as f:
            content = f.read()
        sftp.close()
    except FileNotFoundError:
        raise HTTPException(status_code=404, detail=f"Файл не найден на сервере: {file_path}")
    except Exception as e:
        logger.exception("SFTP download error")
        raise HTTPException(status_code=500, detail=str(e))

    filename = file_path.split("/")[-1]
    return Response(
        content=content,
        media_type="application/octet-stream",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


# Serve frontend (должно быть последним — "catch-all")
app.mount("/", StaticFiles(directory="../frontend", html=True), name="frontend")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=True)
