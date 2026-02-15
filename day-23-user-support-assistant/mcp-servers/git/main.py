"""Git MCP Server - FastAPI server for Git operations via MCP protocol."""

import argparse
import asyncio
import logging
import uuid
from contextlib import asynccontextmanager
from pathlib import Path

import sys
sys.path.append(str(Path(__file__).parent.parent))

from fastapi import FastAPI, Request, Response, HTTPException, Header
from fastapi.responses import StreamingResponse
from typing import Optional

from . import config
from .git_client import GitClient
from .tools import get_all_tools

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

# Global state
git_client: Optional[GitClient] = None
tools: list = []
sessions: dict[str, dict] = {}

# Authentication flag
AUTH_ENABLED = not config.NO_AUTH and bool(config.GIT_API_KEY)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for startup/shutdown."""
    global git_client, tools

    logger.info("=" * 60)
    logger.info("Git MCP Server Starting")
    logger.info("=" * 60)
    logger.info(f"Repository path: {config.DEFAULT_REPO_PATH}")
    logger.info(f"Host: {config.HOST}")
    logger.info(f"Port: {config.PORT}")
    logger.info(f"Authentication: {'ENABLED' if AUTH_ENABLED else 'DISABLED'}")
    logger.info(f"GitHub auth: {'CONFIGURED' if config.GITHUB_TOKEN else 'NOT CONFIGURED'}")
    logger.info("=" * 60)

    # Initialize Git client
    git_client = GitClient(
        repo_path=config.DEFAULT_REPO_PATH,
        github_token=config.GITHUB_TOKEN,
    )
    tools = get_all_tools(git_client)

    logger.info(f"Registered {len(tools)} Git MCP tools")
    for tool in tools:
        logger.info(f"  - {tool.name}: {tool.description[:60]}...")

    yield

    # Cleanup temp files
    if git_client:
        git_client.cleanup()
    logger.info("Git MCP Server shutting down")


# Create FastAPI app
app = FastAPI(
    title="Git MCP Server",
    description="Git repository operations via Model Context Protocol (MCP)",
    version="1.0.0",
    lifespan=lifespan,
)


def check_auth(authorization: Optional[str] = None) -> bool:
    """Check if request is authorized."""
    if not AUTH_ENABLED:
        return True

    if not authorization:
        return False

    # Support both "Bearer <token>" and plain token
    token = authorization.replace("Bearer ", "").strip()
    return token == config.GIT_API_KEY


@app.get("/")
async def root():
    """Server info endpoint."""
    return {
        "name": "Git MCP Server",
        "version": "1.0.0",
        "description": "Git repository operations via MCP",
        "protocol": "mcp/1.0",
        "capabilities": {
            "tools": len(tools),
        },
        "repository": str(config.DEFAULT_REPO_PATH),
    }


@app.get("/health")
async def health():
    """Health check endpoint."""
    try:
        # Try a simple git command to verify repo access
        if git_client:
            await git_client._run_command("status", "--short")
        return {"status": "healthy", "repository": str(config.DEFAULT_REPO_PATH)}
    except Exception as e:
        return {"status": "unhealthy", "error": str(e)}


@app.get("/github/status")
async def github_status():
    """Show GitHub authentication status."""
    configured = bool(config.GITHUB_TOKEN)
    result = {"github_auth": "CONFIGURED" if configured else "NOT CONFIGURED"}

    if configured and git_client:
        try:
            info = await git_client.github_user_info()
            result["login"] = info["login"]
            result["name"] = info["name"]
        except Exception as e:
            result["error"] = str(e)

    return result


@app.get("/tools")
async def list_tools(authorization: Optional[str] = Header(None)):
    """List available MCP tools."""
    if not check_auth(authorization):
        raise HTTPException(status_code=401, detail="Unauthorized")

    return {
        "tools": [tool.to_dict() for tool in tools],
    }


@app.get("/sse")
async def sse_endpoint(
    request: Request,
    authorization: Optional[str] = Header(None),
):
    """SSE endpoint for MCP protocol communication."""
    if not check_auth(authorization):
        raise HTTPException(status_code=401, detail="Unauthorized")

    session_id = str(uuid.uuid4())
    logger.info(f"New SSE connection: {session_id}")

    async def event_generator():
        try:
            # Store session
            sessions[session_id] = {
                "id": session_id,
                "connected": True,
            }

            # Send endpoint URL as first message
            endpoint_url = f"http://{config.HOST}:{config.PORT}/message?sessionId={session_id}"
            yield f"event: endpoint\ndata: {endpoint_url}\n\n"

            # Keep connection alive
            while sessions.get(session_id, {}).get("connected", False):
                yield f"event: ping\ndata: {{}}\n\n"
                await asyncio.sleep(30)

        except asyncio.CancelledError:
            logger.info(f"SSE connection cancelled: {session_id}")
        finally:
            # Clean up session
            if session_id in sessions:
                del sessions[session_id]
            logger.info(f"SSE connection closed: {session_id}")

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@app.post("/message")
async def handle_message(
    request: Request,
    sessionId: str,
    authorization: Optional[str] = Header(None),
):
    """Handle MCP protocol messages."""
    if not check_auth(authorization):
        raise HTTPException(status_code=401, detail="Unauthorized")

    try:
        message = await request.json()
        logger.debug(f"Received message for session {sessionId}: {message}")

        method = message.get("method")
        msg_id = message.get("id")

        if method == "initialize":
            return {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {
                    "protocolVersion": "1.0",
                    "serverInfo": {
                        "name": "git-mcp-server",
                        "version": "1.0.0",
                    },
                    "capabilities": {
                        "tools": {},
                    },
                },
            }

        elif method == "tools/list":
            return {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {
                    "tools": [tool.to_dict() for tool in tools],
                },
            }

        elif method == "tools/call":
            params = message.get("params", {})
            tool_name = params.get("name")
            arguments = params.get("arguments", {})

            logger.info(f"[tools/call] Tool: {tool_name}, Arguments: {arguments}")

            # Find tool
            tool = next((t for t in tools if t.name == tool_name), None)
            if not tool:
                return {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "error": {
                        "code": -32601,
                        "message": f"Tool not found: {tool_name}",
                    },
                }

            # Execute tool
            try:
                result = await tool.execute(arguments)

                # Log response body preview (up to 6 lines: first 3 + last 3)
                body_lines = result.content.splitlines()
                if len(body_lines) <= 6:
                    preview = "\n".join(body_lines)
                else:
                    skipped = len(body_lines) - 6
                    preview = "\n".join(body_lines[:3])
                    preview += f"\n... ({skipped} more lines) ..."
                    preview += "\n" + "\n".join(body_lines[-3:])
                status = "ERROR" if result.is_error else "OK"
                logger.info(f"Tool {tool_name} [{status}]:\n{preview}")

                return {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "result": result.to_dict(),
                }
            except Exception as e:
                logger.error(f"Tool execution error: {e}")
                return {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "error": {
                        "code": -32603,
                        "message": f"Tool execution failed: {str(e)}",
                    },
                }

        else:
            return {
                "jsonrpc": "2.0",
                "id": msg_id,
                "error": {
                    "code": -32601,
                    "message": f"Method not found: {method}",
                },
            }

    except Exception as e:
        logger.error(f"Message handling error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description="Git MCP Server")
    parser.add_argument("--host", default=config.HOST, help="Host to bind to")
    parser.add_argument("--port", type=int, default=config.PORT, help="Port to bind to")
    parser.add_argument("--no-auth", action="store_true", help="Disable authentication")
    parser.add_argument("--repo-path", default=config.DEFAULT_REPO_PATH, help="Git repository path")
    parser.add_argument("--github-token", default=None, help="GitHub Personal Access Token for HTTPS auth")
    args = parser.parse_args()

    # Update config
    config.HOST = args.host
    config.PORT = args.port
    config.DEFAULT_REPO_PATH = args.repo_path

    if args.github_token:
        config.GITHUB_TOKEN = args.github_token

    if args.no_auth:
        config.NO_AUTH = True
        global AUTH_ENABLED
        AUTH_ENABLED = False

    # Run server
    import uvicorn

    uvicorn.run(
        app,
        host=config.HOST,
        port=config.PORT,
        log_level="info",
    )


if __name__ == "__main__":
    main()
