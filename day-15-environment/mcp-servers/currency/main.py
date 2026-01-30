"""Entry point for the CurrencyExchange MCP Server.

Usage:
    python -m currency.main                # with auth (requires MCP_API_KEY env var)
    python -m currency.main --no-auth      # without authentication
    python -m currency.main --port 8004    # custom port

Environment variables:
    MCP_API_KEY  Server API key for client authentication (required unless --no-auth)
    HOST         Bind address (default: 0.0.0.0)
    PORT         Bind port (default: 8004)

Currency data provided by Frankfurter API (https://www.frankfurter.app) - free, no API key required.
Data sourced from European Central Bank.
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
from .currency_client import CurrencyClient
from .tools import get_all_tools

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)

SERVER_NAME = "currency-mcp-server"
SERVER_VERSION = "1.0.0"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="CurrencyExchange MCP Server — get currency rates via MCP protocol"
    )
    parser.add_argument("--no-auth", action="store_true", help="Disable API key authentication")
    parser.add_argument("--host", type=str, default=None, help="Override bind host")
    parser.add_argument("--port", type=int, default=None, help="Override bind port")
    return parser.parse_args()


def build_app(config, currency_client: CurrencyClient) -> FastAPI:
    tools = get_all_tools(currency_client)
    protocol_handler = McpProtocolHandler(tools, server_name=SERVER_NAME, server_version=SERVER_VERSION)
    sse_transport = SseTransport(protocol_handler)

    app = FastAPI(
        title="CurrencyExchange MCP Server",
        description="MCP server for getting currency exchange rates via SSE transport. Powered by Frankfurter API.",
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
            "data_source": "European Central Bank (via Frankfurter API)",
        }

    @app.get("/")
    async def root():
        auth_note = " (requires X-API-Key)" if config.auth.enabled else ""
        return {
            "name": "CurrencyExchange MCP Server",
            "version": SERVER_VERSION,
            "protocol": "MCP 2024-11-05",
            "data_source": "European Central Bank (via Frankfurter API)",
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
    logger.info(f"CurrencyExchange MCP Server v{SERVER_VERSION}")
    logger.info(f"  Address:      {config.server.host}:{config.server.port}")
    logger.info(f"  Auth:         {auth_status}")
    logger.info("  Data source:  European Central Bank (via Frankfurter API)")
    logger.info("  Tools:        get_exchange_rate, convert_currency, get_latest_rates")

    currency_client = CurrencyClient()
    app = build_app(config, currency_client)

    uvicorn.run(app, host=config.server.host, port=config.server.port)


if __name__ == "__main__":
    main()
