"""ADB MCP Server - Main entry point."""

import argparse
import asyncio
import json
import logging
import os
import sys
from pathlib import Path

import uvicorn
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

# Add parent directory to path for shared modules
sys.path.insert(0, str(Path(__file__).parent.parent))

from shared import McpProtocolHandler, SseTransport, BaseTool, ToolResult

from adb.config import SERVER_NAME, SERVER_VERSION, DESCRIPTION, DEFAULT_ADB_PATH
from adb.adb_client import ADBClient

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)


# =============================================================================
# Tool Implementations
# =============================================================================

class ADBBaseTool(BaseTool):
    """Base class for ADB tools."""

    def __init__(self, adb_client: ADBClient):
        self.adb_client = adb_client


class RestartADBServerTool(ADBBaseTool):
    name = "restart_adb_server"
    description = "Restart ADB server to fix connection issues (useful when 'no devices' errors occur)"
    input_schema = {
        "type": "object",
        "properties": {},
    }

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            result = await self.adb_client.restart_adb_server()
            return ToolResult(content=json.dumps(result))
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


class ListDevicesTool(ADBBaseTool):
    name = "list_devices"
    description = "List connected Android devices and emulators"
    input_schema = {
        "type": "object",
        "properties": {},
    }

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            result = await self.adb_client.list_devices()
            return ToolResult(content=json.dumps(result))
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


class ListAVDsTool(ADBBaseTool):
    name = "list_avds"
    description = """Показать список ДОСТУПНЫХ Android Virtual Devices (AVD), которые можно запустить.

Используй ПЕРЕД start_emulator чтобы узнать точные имена AVD.
После получения имени AVD используй start_emulator для запуска."""
    input_schema = {
        "type": "object",
        "properties": {},
    }
    few_shot_examples = [
        {"request": "Покажи доступные AVD", "params": {}},
        {"request": "Какие эмуляторы можно запустить?", "params": {}},
        {"request": "Список виртуальных устройств", "params": {}}
    ]

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            result = await self.adb_client.list_avds()
            return ToolResult(content=json.dumps(result))
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


class StartEmulatorTool(ADBBaseTool):
    name = "start_emulator"
    description = """ЗАПУСТИТЬ Android эмулятор по имени AVD.

ЭТО ЕДИНСТВЕННЫЙ СПОСОБ запустить эмулятор!
НЕ используй execute_adb или launch_app для запуска эмулятора.

Используй когда:
- Пользователь просит "запусти эмулятор", "стартани эмулятор"
- list_devices показывает пустой список (нет устройств)
- Нужно запустить виртуальное устройство для тестирования

Параметры для ускорения:
- gpu_mode: 'auto' (рекомендуется), 'host' (быстрее если есть GPU), 'off' (без GPU)
- memory_mb: RAM в МБ (2048 по умолчанию, можно увеличить до 4096)
- cores: ядра CPU (2 по умолчанию, можно увеличить до 4)

Сначала вызови list_avds чтобы узнать имя AVD."""
    input_schema = {
        "type": "object",
        "properties": {
            "avd_name": {
                "type": "string",
                "description": "Имя AVD из list_avds (например pixel6_api34)"
            },
            "no_window": {
                "type": "boolean",
                "description": "Без окна/GUI (true по умолчанию)"
            },
            "no_audio": {
                "type": "boolean",
                "description": "Отключить звук (true по умолчанию)"
            },
            "timeout": {
                "type": "integer",
                "description": "Таймаут загрузки в секундах (180 по умолчанию)"
            },
            "gpu_mode": {
                "type": "string",
                "description": "Режим GPU: 'auto' (по умолчанию), 'host', 'swiftshader_indirect', 'off'",
                "enum": ["auto", "host", "swiftshader_indirect", "off"]
            },
            "memory_mb": {
                "type": "integer",
                "description": "RAM в МБ (2048 по умолчанию). Увеличь до 4096 для лучшей производительности"
            },
            "cores": {
                "type": "integer",
                "description": "Количество ядер CPU (2 по умолчанию). Увеличь до 4 для ускорения"
            }
        },
        "required": ["avd_name"]
    }
    few_shot_examples = [
        {"request": "Запусти эмулятор pixel6_api34", "params": {"avd_name": "pixel6_api34"}},
        {"request": "Запусти быстрый эмулятор", "params": {"avd_name": "pixel6_api34", "memory_mb": 4096, "cores": 4}},
        {"request": "Запусти эмулятор без GPU", "params": {"avd_name": "pixel6_api34", "gpu_mode": "off"}},
        {"request": "Стартани эмулятор с окном", "params": {"avd_name": "pixel6_api34", "no_window": False}}
    ]

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            result = await self.adb_client.start_emulator(
                avd_name=arguments["avd_name"],
                no_window=arguments.get("no_window", True),
                no_audio=arguments.get("no_audio", True),
                timeout=arguments.get("timeout", 180),
                gpu_mode=arguments.get("gpu_mode", "auto"),
                memory_mb=arguments.get("memory_mb", 2048),
                cores=arguments.get("cores", 2)
            )
            return ToolResult(content=json.dumps(result))
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


class StopEmulatorTool(ADBBaseTool):
    name = "stop_emulator"
    description = """ОСТАНОВИТЬ/ВЫКЛЮЧИТЬ работающий Android эмулятор.

⚠️ ВНИМАНИЕ: НЕ используй этот инструмент для:
- Установки APK (используй install_apk)
- Запуска приложения (используй launch_app)
- Сборки проекта (используй build_apk)

Используй ТОЛЬКО когда пользователь явно просит:
- "останови эмулятор", "выключи эмулятор", "закрой эмулятор"
- "shut down emulator", "stop emulator"
- Завершить работу с устройством"""
    input_schema = {
        "type": "object",
        "properties": {
            "device_id": {
                "type": "string",
                "description": "Device serial number (optional, will stop first emulator if not specified)"
            }
        }
    }
    few_shot_examples = [
        {"request": "Останови эмулятор", "params": {}},
        {"request": "Выключи эмулятор emulator-5554", "params": {"device_id": "emulator-5554"}},
        {"request": "Закрой Android эмулятор", "params": {}}
    ]

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            result = await self.adb_client.stop_emulator(
                device_id=arguments.get("device_id")
            )
            return ToolResult(content=json.dumps(result))
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


class InstallAPKTool(ADBBaseTool):
    name = "install_apk"
    description = """УСТАНОВИТЬ APK файл на Android устройство или эмулятор.

Используй когда пользователь просит:
- "установи APK", "инсталлируй APK", "поставь приложение"
- "install APK", "deploy app"
- После сборки (build_apk) для установки на устройство

Требования:
- Устройство/эмулятор должен быть запущен (проверь list_devices)
- APK файл должен существовать по указанному пути

Типичный путь к APK после сборки:
- /path/to/project/app/build/outputs/apk/debug/app-debug.apk
- /path/to/project/composeApp/build/outputs/apk/debug/composeApp-debug.apk"""
    input_schema = {
        "type": "object",
        "properties": {
            "apk_path": {
                "type": "string",
                "description": "Полный путь к APK файлу. Пример: /Users/user/project/app/build/outputs/apk/debug/app-debug.apk"
            },
            "device_id": {
                "type": "string",
                "description": "ID устройства (опционально, по умолчанию первое устройство)"
            },
            "replace": {
                "type": "boolean",
                "description": "Заменить существующее приложение (true по умолчанию)"
            }
        },
        "required": ["apk_path"]
    }
    few_shot_examples = [
        {"request": "Установи APK из /path/to/app.apk", "params": {"apk_path": "/path/to/app.apk"}},
        {"request": "Инсталлируй собранное приложение", "params": {"apk_path": "/project/app/build/outputs/apk/debug/app-debug.apk"}},
        {"request": "Поставь APK на эмулятор", "params": {"apk_path": "/path/to/app-debug.apk"}},
        {"request": "Install the built APK on device", "params": {"apk_path": "/project/composeApp/build/outputs/apk/debug/composeApp-debug.apk"}}
    ]

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            result = await self.adb_client.install_apk(
                apk_path=arguments["apk_path"],
                device_id=arguments.get("device_id"),
                replace=arguments.get("replace", True)
            )
            return ToolResult(content=json.dumps(result))
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


class ScreenshotTool(ADBBaseTool):
    name = "screenshot"
    description = """Сделать скриншот экрана Android устройства.

Если команда зависает/таймаутит - возможно эмулятор завис.
Используй dismiss_dialogs чтобы закрыть диалоги ANR и повторить."""
    input_schema = {
        "type": "object",
        "properties": {
            "device_id": {
                "type": "string",
                "description": "ID устройства (опционально)"
            },
            "output_path": {
                "type": "string",
                "description": "Путь для сохранения (опционально)"
            }
        }
    }
    few_shot_examples = [
        {"request": "Сделай скриншот", "params": {}},
        {"request": "Покажи экран эмулятора", "params": {}},
        {"request": "Что на экране?", "params": {}}
    ]

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            result = await self.adb_client.screenshot(
                device_id=arguments.get("device_id"),
                output_path=arguments.get("output_path")
            )
            # Use json.dumps for proper JSON formatting (Kotlin parser needs double quotes)
            return ToolResult(content=json.dumps(result))
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


class ExecuteADBTool(ADBBaseTool):
    name = "execute_adb"
    description = "Execute arbitrary ADB command"
    input_schema = {
        "type": "object",
        "properties": {
            "command": {
                "type": "string",
                "description": "ADB command to execute"
            },
            "device_id": {
                "type": "string",
                "description": "Device serial number (optional)"
            },
            "timeout": {
                "type": "integer",
                "description": "Command timeout in seconds (default: 30)"
            }
        },
        "required": ["command"]
    }

    # Commands that should NOT be used with execute_adb
    FORBIDDEN_COMMANDS = {
        "emulator": "Для запуска эмулятора используй инструмент 'start_emulator' с параметром avd_name. Пример: start_emulator({\"avd_name\": \"pixel6_api34\"})",
        "avdmanager": "Для просмотра доступных AVD используй инструмент 'list_avds'",
    }

    async def execute(self, arguments: dict) -> ToolResult:
        command = arguments.get("command", "").strip()

        # Check for forbidden commands
        first_word = command.split()[0] if command else ""
        if first_word in self.FORBIDDEN_COMMANDS:
            error_msg = (
                f"ОШИБКА: Команда '{first_word}' не является ADB командой!\n\n"
                f"{self.FORBIDDEN_COMMANDS[first_word]}\n\n"
                f"execute_adb предназначен ТОЛЬКО для команд вида: shell, logcat, push, pull, forward, reverse и т.д."
            )
            return ToolResult(content=error_msg, is_error=True)

        # Check if command looks like it's trying to start emulator
        if "-avd" in command or "emulator" in command.lower():
            error_msg = (
                "ОШИБКА: Похоже, вы пытаетесь запустить эмулятор через execute_adb.\n\n"
                "Это НЕ сработает! Используйте инструмент 'start_emulator'.\n\n"
                "Пример: start_emulator({\"avd_name\": \"pixel6_api34\"})\n\n"
                "Сначала можно проверить доступные AVD: list_avds({})"
            )
            return ToolResult(content=error_msg, is_error=True)

        try:
            output = await self.adb_client.execute_adb(
                command=command,
                device_id=arguments.get("device_id"),
                timeout=arguments.get("timeout", 30)
            )
            return ToolResult(content=output)
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


class GetDeviceInfoTool(ADBBaseTool):
    name = "get_device_info"
    description = "Get device information (model, Android version, etc.)"
    input_schema = {
        "type": "object",
        "properties": {
            "device_id": {
                "type": "string",
                "description": "Device serial number (optional)"
            }
        }
    }

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            result = await self.adb_client.get_device_info(
                device_id=arguments.get("device_id")
            )
            return ToolResult(content=json.dumps(result))
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


class LaunchAppTool(ADBBaseTool):
    name = "launch_app"
    description = """Запустить УСТАНОВЛЕННОЕ приложение на устройстве.

ВАЖНО: Это НЕ для запуска эмулятора! Для запуска эмулятора используй start_emulator.

Требования:
- Устройство/эмулятор должен быть УЖЕ запущен (проверь list_devices)
- Приложение должно быть установлено (используй install_apk)

Используй после install_apk для запуска собранного приложения."""
    input_schema = {
        "type": "object",
        "properties": {
            "package": {
                "type": "string",
                "description": "Имя пакета приложения (например ru.chtcholeg.app, com.example.myapp)"
            },
            "activity": {
                "type": "string",
                "description": "Activity для запуска (опционально, автоопределение если не указано)"
            },
            "device_id": {
                "type": "string",
                "description": "ID устройства (опционально, по умолчанию первое устройство)"
            }
        },
        "required": ["package"]
    }
    few_shot_examples = [
        {"request": "Запусти приложение ru.chtcholeg.app", "params": {"package": "ru.chtcholeg.app"}},
        {"request": "Открой установленное приложение", "params": {"package": "com.example.app"}},
        {"request": "Запусти MainActivity", "params": {"package": "ru.chtcholeg.app", "activity": ".MainActivity"}}
    ]

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            result = await self.adb_client.launch_app(
                package=arguments["package"],
                activity=arguments.get("activity"),
                device_id=arguments.get("device_id")
            )
            return ToolResult(content=json.dumps(result))
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


class DismissDialogsTool(ADBBaseTool):
    name = "dismiss_dialogs"
    description = "Dismiss system dialogs (ANR, crashes) and disable future ANR popups"
    input_schema = {
        "type": "object",
        "properties": {
            "device_id": {
                "type": "string",
                "description": "Device serial number (optional)"
            }
        }
    }

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            result = await self.adb_client.dismiss_dialogs(
                device_id=arguments.get("device_id")
            )
            return ToolResult(content=json.dumps(result))
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


class GetAppLogsTool(ADBBaseTool):
    name = "get_app_logs"
    description = "Get application logs (logcat) filtered by package name"
    input_schema = {
        "type": "object",
        "properties": {
            "package": {
                "type": "string",
                "description": "Package name to filter logs"
            },
            "lines": {
                "type": "integer",
                "description": "Number of log lines (default: 100)"
            },
            "level": {
                "type": "string",
                "description": "Minimum log level: V, D, I, W, E, F (default: D)",
                "enum": ["V", "D", "I", "W", "E", "F"]
            },
            "device_id": {
                "type": "string",
                "description": "Device serial number (optional)"
            }
        },
        "required": ["package"]
    }

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            result = await self.adb_client.get_app_logs(
                package=arguments["package"],
                lines=arguments.get("lines", 100),
                level=arguments.get("level", "D"),
                device_id=arguments.get("device_id")
            )
            return ToolResult(content=json.dumps(result))
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


class BuildAPKTool(ADBBaseTool):
    name = "build_apk"
    description = "Build APK using Gradle (./gradlew assembleDebug or assembleRelease)"
    input_schema = {
        "type": "object",
        "properties": {
            "project_path": {
                "type": "string",
                "description": "Path to Android project root (containing gradlew)"
            },
            "build_type": {
                "type": "string",
                "description": "Build type: 'debug' or 'release' (default: debug)",
                "enum": ["debug", "release"]
            },
            "module": {
                "type": "string",
                "description": "Module name to build (auto-detect: composeApp, app, or androidApp)"
            },
            "clean": {
                "type": "boolean",
                "description": "Run clean before build (default: false)"
            },
            "timeout": {
                "type": "integer",
                "description": "Build timeout in seconds (default: 600)"
            }
        },
        "required": ["project_path"]
    }

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            result = await self.adb_client.build_apk(
                project_path=arguments["project_path"],
                build_type=arguments.get("build_type", "debug"),
                module=arguments.get("module"),
                clean=arguments.get("clean", False),
                timeout=arguments.get("timeout", 600)
            )
            return ToolResult(content=json.dumps(result))
        except Exception as e:
            return ToolResult(content=str(e), is_error=True)


# =============================================================================
# Server Setup
# =============================================================================

def get_all_tools(adb_client: ADBClient) -> list:
    """Get all ADB tools."""
    return [
        RestartADBServerTool(adb_client),
        ListDevicesTool(adb_client),
        ListAVDsTool(adb_client),
        StartEmulatorTool(adb_client),
        StopEmulatorTool(adb_client),
        InstallAPKTool(adb_client),
        LaunchAppTool(adb_client),
        ScreenshotTool(adb_client),
        ExecuteADBTool(adb_client),
        GetDeviceInfoTool(adb_client),
        GetAppLogsTool(adb_client),
        BuildAPKTool(adb_client),
        DismissDialogsTool(adb_client),
    ]


def build_app(adb_client: ADBClient, api_key: str = None) -> FastAPI:
    """Build FastAPI application with MCP protocol."""
    tools = get_all_tools(adb_client)
    protocol_handler = McpProtocolHandler(tools, server_name=SERVER_NAME, server_version=SERVER_VERSION)
    sse_transport = SseTransport(protocol_handler)

    app = FastAPI(
        title="ADB MCP Server",
        description=DESCRIPTION,
        version=SERVER_VERSION,
    )

    # CORS - allow any origin
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_headers=["*"],
        allow_methods=["GET", "POST", "OPTIONS"],
    )

    # API key authentication middleware
    if api_key:
        public_paths = {"/health", "/", "/docs", "/redoc", "/openapi.json"}

        @app.middleware("http")
        async def auth_middleware(request: Request, call_next):
            if request.url.path in public_paths:
                return await call_next(request)
            key = request.headers.get("x-api-key") or request.query_params.get("api_key")
            if key != api_key:
                return JSONResponse(
                    {"error": "Unauthorized: missing or invalid X-API-Key header"},
                    status_code=401,
                )
            return await call_next(request)

    # Setup SSE transport routes
    sse_transport.setup_routes(app)

    @app.get("/health")
    async def health():
        return {
            "status": "ok",
            "tools_count": len(tools),
            "active_sessions": sse_transport.get_active_session_count(),
            "auth_enabled": api_key is not None,
            "adb_path": adb_client.adb_path,
        }

    @app.get("/")
    async def root():
        auth_note = " (requires X-API-Key)" if api_key else ""
        return {
            "name": SERVER_NAME,
            "version": SERVER_VERSION,
            "protocol": "MCP 2024-11-05",
            "endpoints": {
                "sse": f"/sse{auth_note}",
                "message": f"/message{auth_note}",
                "health": "/health (public)",
                "tools": f"/tools{auth_note}",
                "docs": "/docs (public)",
            },
        }

    @app.get("/tools")
    async def list_tools():
        return {
            "tools": [
                {
                    "name": t.name,
                    "description": t.description,
                    "input_schema": t.input_schema,
                }
                for t in tools
            ],
        }

    return app


def main():
    parser = argparse.ArgumentParser(description=DESCRIPTION)
    parser.add_argument("--port", type=int, default=8007, help="Server port")
    parser.add_argument("--host", default="0.0.0.0", help="Server host")
    parser.add_argument("--adb-path", default=DEFAULT_ADB_PATH, help="Path to adb executable")
    parser.add_argument("--android-home", default=None, help="Android SDK home directory")
    parser.add_argument("--no-auth", action="store_true", help="Disable API key authentication")

    args = parser.parse_args()

    # Get API key from environment
    api_key = None if args.no_auth else os.getenv("MCP_API_KEY")

    # Create ADB client
    adb_client = ADBClient(adb_path=args.adb_path, android_home=args.android_home)

    # Startup banner
    auth_status = "DISABLED" if not api_key else "enabled"
    logger.info(f"📱 {SERVER_NAME} v{SERVER_VERSION}")
    logger.info(f"  Address: {args.host}:{args.port}")
    logger.info(f"  Auth: {auth_status}")
    logger.info(f"  ADB path: {adb_client.adb_path}")
    logger.info(f"  Android home: {adb_client.android_home or 'auto-detect'}")

    app = build_app(adb_client, api_key)
    uvicorn.run(app, host=args.host, port=args.port)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n🛑 ADB MCP Server stopped")
