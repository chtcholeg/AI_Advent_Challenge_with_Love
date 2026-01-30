"""Entry point for the Weather MCP Server.

Usage:
    python -m weather.main                # with auth (requires MCP_API_KEY env var)
    python -m weather.main --no-auth      # without authentication
    python -m weather.main --port 8002    # custom port

Environment variables:
    MCP_API_KEY  Server API key for client authentication (required unless --no-auth)
    HOST         Bind address (default: 0.0.0.0)
    PORT         Bind port (default: 8002)

Weather data provided by Open-Meteo (https://open-meteo.com) - free, no API key required.
"""

import argparse
import logging
import sys

import uvicorn
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from shared import McpProtocolHandler, SseTransport
from .config import load_config
from .weather_client import WeatherClient
from .tools import get_all_tools

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)

SERVER_NAME = "weather-mcp-server"
SERVER_VERSION = "1.0.0"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Weather MCP Server — get weather data via MCP protocol"
    )
    parser.add_argument("--no-auth", action="store_true", help="Disable API key authentication")
    parser.add_argument("--host", type=str, default=None, help="Override bind host")
    parser.add_argument("--port", type=int, default=None, help="Override bind port")
    return parser.parse_args()


def build_app(config, weather_client: WeatherClient) -> FastAPI:
    tools = get_all_tools(weather_client)
    protocol_handler = McpProtocolHandler(tools, server_name=SERVER_NAME, server_version=SERVER_VERSION)
    sse_transport = SseTransport(protocol_handler)

    app = FastAPI(
        title="Weather MCP Server",
        description="MCP server for getting weather data via SSE transport. Powered by Open-Meteo.",
        version=SERVER_VERSION,
    )

    # CORS — allow any origin so clients can connect
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_headers=["*"],
        allow_methods=["GET", "POST", "OPTIONS"],
    )

    # API-key authentication middleware (only when auth is enabled)
    if config.auth.enabled:
        api_key = config.auth.api_key
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

    # MCP SSE transport routes (/sse and /message)
    sse_transport.setup_routes(app)

    # --- Convenience endpoints ---

    @app.get("/health")
    async def health():
        return {
            "status": "ok",
            "tools_count": len(tools),
            "active_sessions": sse_transport.get_active_session_count(),
            "auth_enabled": config.auth.enabled,
            "data_source": "Open-Meteo (https://open-meteo.com)",
        }

    @app.get("/")
    async def root():
        auth_note = " (requires X-API-Key)" if config.auth.enabled else ""
        return {
            "name": "Weather MCP Server",
            "version": SERVER_VERSION,
            "protocol": "MCP 2024-11-05",
            "data_source": "Open-Meteo (free, no API key required)",
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
                    "required_params": t.input_schema.get("required", []),
                }
                for t in tools
            ],
        }

    return app


def main():
    args = parse_args()

    try:
        config = load_config(disable_auth=args.no_auth)
    except ValueError as e:
        logger.error(str(e))
        sys.exit(1)

    if args.host:
        config.server.host = args.host
    if args.port:
        config.server.port = args.port

    # Startup banner
    auth_status = "DISABLED" if not config.auth.enabled else "enabled"
    logger.info(f"Weather MCP Server v{SERVER_VERSION}")
    logger.info(f"  Address:      {config.server.host}:{config.server.port}")
    logger.info(f"  Auth:         {auth_status}")
    logger.info("  Data source:  Open-Meteo (https://open-meteo.com)")
    logger.info("  Tools:        get_current_weather, get_weather_forecast")

    weather_client = WeatherClient()
    app = build_app(config, weather_client)

    uvicorn.run(app, host=config.server.host, port=config.server.port)


if __name__ == "__main__":
    main()
