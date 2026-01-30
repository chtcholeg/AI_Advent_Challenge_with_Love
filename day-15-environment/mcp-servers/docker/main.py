#!/usr/bin/env python3
"""Docker MCP Server.

Provides MCP tools for Docker container management:
- docker_ps: List running containers
- docker_run: Start a new container
- docker_stop: Stop a container
- docker_start: Start a stopped container
- docker_restart: Restart a container
- docker_logs: Get container logs
- docker_exec: Execute command in container
- docker_images: List Docker images
- docker_pull: Pull an image from registry
- docker_build: Build image from Dockerfile
- docker_compose_up: Start Docker Compose services
- docker_compose_down: Stop Docker Compose services
- docker_compose_ps: List Compose services status
"""

import argparse
import asyncio
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

# Add parent directory to path for shared imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from shared import McpProtocolHandler, SseTransport, ToolResult, BaseTool


class DockerTool(BaseTool):
    """Base class for Docker tools."""

    def check_docker_available(self) -> tuple[bool, str]:
        """Check if Docker is installed and running."""
        try:
            result = subprocess.run(
                ["docker", "version"],
                capture_output=True,
                text=True,
                timeout=5
            )
            if result.returncode == 0:
                return True, ""
            return False, "Docker is installed but not running. Please start Docker."
        except FileNotFoundError:
            return False, "Docker is not installed. Please install Docker first."
        except subprocess.TimeoutExpired:
            return False, "Docker command timed out. Docker may not be responding."
        except Exception as e:
            return False, f"Error checking Docker: {e}"

    def run_docker_command(self, cmd: list[str], timeout: int = 30) -> tuple[bool, str]:
        """Run a docker command and return success status and output."""
        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=timeout
            )

            output = result.stdout.strip()
            if result.returncode != 0:
                error = result.stderr.strip()
                return False, error or "Command failed"

            return True, output
        except subprocess.TimeoutExpired:
            return False, f"Command timed out after {timeout} seconds"
        except Exception as e:
            return False, f"Error executing command: {e}"


class DockerPsTool(DockerTool):
    """List running Docker containers."""

    name = "docker_ps"
    description = "List running Docker containers with their status"
    input_schema = {
        "type": "object",
        "properties": {
            "all": {
                "type": "boolean",
                "description": "Show all containers (default shows just running)",
                "default": False
            }
        }
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        show_all = arguments.get("all", False)
        cmd = ["docker", "ps", "--format", "table {{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"]
        if show_all:
            cmd.append("-a")

        success, output = self.run_docker_command(cmd)
        if not success:
            return ToolResult(content=output, is_error=True)

        return ToolResult(content=output or "No containers found")


class DockerRunTool(DockerTool):
    """Start a new Docker container."""

    name = "docker_run"
    description = "Start a new Docker container with specified image and options"
    input_schema = {
        "type": "object",
        "properties": {
            "image": {
                "type": "string",
                "description": "Docker image to run (e.g., 'nginx:latest', 'postgres:15')"
            },
            "name": {
                "type": "string",
                "description": "Container name (optional)"
            },
            "ports": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Port mappings in format 'host:container' (e.g., ['8080:80', '5432:5432'])"
            },
            "env": {
                "type": "object",
                "description": "Environment variables as key-value pairs",
                "properties": {},
                "additionalProperties": {"type": "string"}
            },
            "volumes": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Volume mappings in format 'host:container' (e.g., ['/data:/var/lib/data'])"
            },
            "detach": {
                "type": "boolean",
                "description": "Run container in background (default: true)",
                "default": True
            },
            "remove": {
                "type": "boolean",
                "description": "Automatically remove container when it exits (default: false)",
                "default": False
            }
        },
        "required": ["image"]
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        image = arguments["image"]
        cmd = ["docker", "run"]

        # Add flags
        if arguments.get("detach", True):
            cmd.append("-d")
        if arguments.get("remove", False):
            cmd.append("--rm")

        # Add name
        if name := arguments.get("name"):
            cmd.extend(["--name", name])

        # Add ports
        for port in arguments.get("ports", []):
            cmd.extend(["-p", port])

        # Add environment variables
        for key, value in arguments.get("env", {}).items():
            cmd.extend(["-e", f"{key}={value}"])

        # Add volumes
        for volume in arguments.get("volumes", []):
            cmd.extend(["-v", volume])

        # Add image
        cmd.append(image)

        success, output = self.run_docker_command(cmd)
        if not success:
            return ToolResult(content=output, is_error=True)

        container_id = output[:12]
        return ToolResult(content=f"Container started successfully\nContainer ID: {container_id}")


class DockerStopTool(DockerTool):
    """Stop a running Docker container."""

    name = "docker_stop"
    description = "Stop a running Docker container by name or ID"
    input_schema = {
        "type": "object",
        "properties": {
            "container": {
                "type": "string",
                "description": "Container name or ID to stop"
            },
            "timeout": {
                "type": "integer",
                "description": "Seconds to wait before killing container (default: 10)",
                "default": 10
            }
        },
        "required": ["container"]
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        container = arguments["container"]
        timeout = arguments.get("timeout", 10)

        cmd = ["docker", "stop", "-t", str(timeout), container]
        success, output = self.run_docker_command(cmd)

        if not success:
            return ToolResult(content=output, is_error=True)

        return ToolResult(content=f"Container '{container}' stopped successfully")


class DockerStartTool(DockerTool):
    """Start a stopped Docker container."""

    name = "docker_start"
    description = "Start a stopped Docker container by name or ID"
    input_schema = {
        "type": "object",
        "properties": {
            "container": {
                "type": "string",
                "description": "Container name or ID to start"
            }
        },
        "required": ["container"]
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        container = arguments["container"]
        cmd = ["docker", "start", container]
        success, output = self.run_docker_command(cmd)

        if not success:
            return ToolResult(content=output, is_error=True)

        return ToolResult(content=f"Container '{container}' started successfully")


class DockerRestartTool(DockerTool):
    """Restart a Docker container."""

    name = "docker_restart"
    description = "Restart a Docker container by name or ID"
    input_schema = {
        "type": "object",
        "properties": {
            "container": {
                "type": "string",
                "description": "Container name or ID to restart"
            }
        },
        "required": ["container"]
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        container = arguments["container"]
        cmd = ["docker", "restart", container]
        success, output = self.run_docker_command(cmd)

        if not success:
            return ToolResult(content=output, is_error=True)

        return ToolResult(content=f"Container '{container}' restarted successfully")


class DockerLogsTool(DockerTool):
    """Get logs from a Docker container."""

    name = "docker_logs"
    description = "Get logs from a Docker container"
    input_schema = {
        "type": "object",
        "properties": {
            "container": {
                "type": "string",
                "description": "Container name or ID"
            },
            "tail": {
                "type": "integer",
                "description": "Number of lines from the end of logs (default: 100)",
                "default": 100
            },
            "follow": {
                "type": "boolean",
                "description": "Follow log output (default: false)",
                "default": False
            }
        },
        "required": ["container"]
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        container = arguments["container"]
        tail = arguments.get("tail", 100)

        cmd = ["docker", "logs", "--tail", str(tail)]

        # Note: follow mode not supported for now as it would block
        # if arguments.get("follow", False):
        #     cmd.append("-f")

        cmd.append(container)

        success, output = self.run_docker_command(cmd, timeout=10)
        if not success:
            return ToolResult(content=output, is_error=True)

        return ToolResult(content=output or "No logs available")


class DockerExecTool(DockerTool):
    """Execute a command in a running container."""

    name = "docker_exec"
    description = "Execute a command in a running Docker container"
    input_schema = {
        "type": "object",
        "properties": {
            "container": {
                "type": "string",
                "description": "Container name or ID"
            },
            "command": {
                "type": "string",
                "description": "Command to execute (e.g., 'ls -la', 'cat /etc/hosts')"
            }
        },
        "required": ["container", "command"]
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        container = arguments["container"]
        command = arguments["command"]

        # Split command into parts
        cmd = ["docker", "exec", container, "sh", "-c", command]

        success, output = self.run_docker_command(cmd, timeout=30)
        if not success:
            return ToolResult(content=output, is_error=True)

        return ToolResult(content=output or "Command executed successfully (no output)")


class DockerImagesTool(DockerTool):
    """List Docker images."""

    name = "docker_images"
    description = "List Docker images available locally"
    input_schema = {
        "type": "object",
        "properties": {}
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        cmd = ["docker", "images", "--format", "table {{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.Size}}"]
        success, output = self.run_docker_command(cmd)

        if not success:
            return ToolResult(content=output, is_error=True)

        return ToolResult(content=output or "No images found")


class DockerPullTool(DockerTool):
    """Pull a Docker image from registry."""

    name = "docker_pull"
    description = "Pull a Docker image from registry"
    input_schema = {
        "type": "object",
        "properties": {
            "image": {
                "type": "string",
                "description": "Image name with optional tag (e.g., 'nginx:latest', 'postgres:15')"
            }
        },
        "required": ["image"]
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        image = arguments["image"]
        cmd = ["docker", "pull", image]

        success, output = self.run_docker_command(cmd, timeout=300)  # 5 minutes for pull
        if not success:
            return ToolResult(content=output, is_error=True)

        return ToolResult(content=f"Image '{image}' pulled successfully")


class DockerBuildTool(DockerTool):
    """Build a Docker image from Dockerfile."""

    name = "docker_build"
    description = "Build a Docker image from Dockerfile in specified directory"
    input_schema = {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "Path to directory containing Dockerfile (default: current directory)",
                "default": "."
            },
            "tag": {
                "type": "string",
                "description": "Name and optionally tag for the image (e.g., 'myapp:latest')"
            },
            "dockerfile": {
                "type": "string",
                "description": "Name of Dockerfile (default: 'Dockerfile')",
                "default": "Dockerfile"
            }
        },
        "required": ["tag"]
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        path = arguments.get("path", ".")
        tag = arguments["tag"]
        dockerfile = arguments.get("dockerfile", "Dockerfile")

        # Check if Dockerfile exists
        dockerfile_path = Path(path) / dockerfile
        if not dockerfile_path.exists():
            return ToolResult(content=f"Dockerfile not found at {dockerfile_path}", is_error=True)

        cmd = ["docker", "build", "-t", tag, "-f", dockerfile, path]

        success, output = self.run_docker_command(cmd, timeout=600)  # 10 minutes for build
        if not success:
            return ToolResult(content=output, is_error=True)

        return ToolResult(content=f"Image '{tag}' built successfully")


class DockerComposeUpTool(DockerTool):
    """Start Docker Compose services."""

    name = "docker_compose_up"
    description = "Start services defined in docker-compose.yml"
    input_schema = {
        "type": "object",
        "properties": {
            "file": {
                "type": "string",
                "description": "Path to docker-compose.yml (default: docker-compose.yml in current directory)",
                "default": "docker-compose.yml"
            },
            "detach": {
                "type": "boolean",
                "description": "Run in background (default: true)",
                "default": True
            },
            "build": {
                "type": "boolean",
                "description": "Build images before starting (default: false)",
                "default": False
            }
        }
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        compose_file = arguments.get("file", "docker-compose.yml")

        # Check if compose file exists
        if not Path(compose_file).exists():
            return ToolResult(content=f"Compose file not found: {compose_file}", is_error=True)

        cmd = ["docker", "compose", "-f", compose_file, "up"]

        if arguments.get("detach", True):
            cmd.append("-d")

        if arguments.get("build", False):
            cmd.append("--build")

        success, output = self.run_docker_command(cmd, timeout=300)
        if not success:
            return ToolResult(content=output, is_error=True)

        return ToolResult(content="Docker Compose services started successfully")


class DockerComposeDownTool(DockerTool):
    """Stop Docker Compose services."""

    name = "docker_compose_down"
    description = "Stop and remove services defined in docker-compose.yml"
    input_schema = {
        "type": "object",
        "properties": {
            "file": {
                "type": "string",
                "description": "Path to docker-compose.yml (default: docker-compose.yml in current directory)",
                "default": "docker-compose.yml"
            },
            "volumes": {
                "type": "boolean",
                "description": "Remove volumes (default: false)",
                "default": False
            }
        }
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        compose_file = arguments.get("file", "docker-compose.yml")

        cmd = ["docker", "compose", "-f", compose_file, "down"]

        if arguments.get("volumes", False):
            cmd.append("-v")

        success, output = self.run_docker_command(cmd, timeout=60)
        if not success:
            return ToolResult(content=output, is_error=True)

        return ToolResult(content="Docker Compose services stopped successfully")


class DockerComposePsTool(DockerTool):
    """List Docker Compose services status."""

    name = "docker_compose_ps"
    description = "List status of services defined in docker-compose.yml"
    input_schema = {
        "type": "object",
        "properties": {
            "file": {
                "type": "string",
                "description": "Path to docker-compose.yml (default: docker-compose.yml in current directory)",
                "default": "docker-compose.yml"
            }
        }
    }

    async def execute(self, arguments: dict[str, Any]) -> ToolResult:
        available, error_msg = self.check_docker_available()
        if not available:
            return ToolResult(content=error_msg, is_error=True)

        compose_file = arguments.get("file", "docker-compose.yml")

        cmd = ["docker", "compose", "-f", compose_file, "ps"]

        success, output = self.run_docker_command(cmd, timeout=10)
        if not success:
            return ToolResult(content=output, is_error=True)

        return ToolResult(content=output or "No services found")


# =============================================================================
# Server setup
# =============================================================================

import uvicorn
import logging
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)

SERVER_NAME = "docker-mcp-server"
SERVER_VERSION = "1.0.0"


def get_all_tools() -> list:
    """Get all Docker tools."""
    return [
        DockerPsTool(),
        DockerRunTool(),
        DockerStopTool(),
        DockerStartTool(),
        DockerRestartTool(),
        DockerLogsTool(),
        DockerExecTool(),
        DockerImagesTool(),
        DockerPullTool(),
        DockerBuildTool(),
        DockerComposeUpTool(),
        DockerComposeDownTool(),
        DockerComposePsTool(),
    ]


def build_app(api_key: str = None) -> FastAPI:
    """Build FastAPI application with MCP protocol."""
    tools = get_all_tools()
    protocol_handler = McpProtocolHandler(tools, server_name=SERVER_NAME, server_version=SERVER_VERSION)
    sse_transport = SseTransport(protocol_handler)

    app = FastAPI(
        title="Docker MCP Server",
        description="MCP server for Docker container management via SSE transport",
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
        }

    @app.get("/")
    async def root():
        auth_note = " (requires X-API-Key)" if api_key else ""
        return {
            "name": "Docker MCP Server",
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
    parser = argparse.ArgumentParser(description="Docker MCP Server")
    parser.add_argument("--port", type=int, default=8006, help="Port to listen on")
    parser.add_argument("--host", default="0.0.0.0", help="Host to bind to")
    parser.add_argument("--no-auth", action="store_true", help="Disable API key authentication")
    args = parser.parse_args()

    # Get API key from environment or disable auth
    api_key = None if args.no_auth else os.getenv("MCP_API_KEY")

    # Startup banner
    auth_status = "DISABLED" if not api_key else "enabled"
    logger.info(f"🐳 Docker MCP Server v{SERVER_VERSION}")
    logger.info(f"  Address: {args.host}:{args.port}")
    logger.info(f"  Auth: {auth_status}")
    logger.info(f"  Tools: {len(get_all_tools())}")

    app = build_app(api_key)
    uvicorn.run(app, host=args.host, port=args.port)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n🛑 Docker MCP Server stopped")
