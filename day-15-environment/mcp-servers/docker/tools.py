"""Docker MCP Server tool definitions."""

from shared.models import Tool, ToolParameter

# =============================================================================
# Tool Definitions
# =============================================================================

TOOLS = [
    Tool(
        name="list_containers",
        description="List Docker containers (running or all)",
        parameters=[
            ToolParameter(
                name="all",
                type="boolean",
                description="Show all containers including stopped (default: false, only running)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "user": "Покажи запущенные контейнеры",
                "arguments": {"all": False}
            },
            {
                "user": "Покажи все Docker контейнеры, включая остановленные",
                "arguments": {"all": True}
            },
            {
                "user": "Какие контейнеры работают?",
                "arguments": {"all": False}
            }
        ]
    ),
    Tool(
        name="start_container",
        description="Start a stopped Docker container by ID or name",
        parameters=[
            ToolParameter(
                name="container_id",
                type="string",
                description="Container ID or name to start",
                required=True
            )
        ],
        fewShotExamples=[
            {
                "user": "Запусти контейнер nginx",
                "arguments": {"container_id": "nginx"}
            },
            {
                "user": "Стартани контейнер с ID abc123",
                "arguments": {"container_id": "abc123"}
            }
        ]
    ),
    Tool(
        name="stop_container",
        description="Stop a running Docker container by ID or name",
        parameters=[
            ToolParameter(
                name="container_id",
                type="string",
                description="Container ID or name to stop",
                required=True
            ),
            ToolParameter(
                name="timeout",
                type="integer",
                description="Seconds to wait before killing container (default: 10)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "user": "Останови контейнер nginx",
                "arguments": {"container_id": "nginx"}
            },
            {
                "user": "Останови контейнер abc123 через 5 секунд",
                "arguments": {"container_id": "abc123", "timeout": 5}
            }
        ]
    ),
    Tool(
        name="remove_container",
        description="Remove a Docker container by ID or name",
        parameters=[
            ToolParameter(
                name="container_id",
                type="string",
                description="Container ID or name to remove",
                required=True
            ),
            ToolParameter(
                name="force",
                type="boolean",
                description="Force removal of running container (default: false)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "user": "Удали контейнер nginx",
                "arguments": {"container_id": "nginx"}
            },
            {
                "user": "Принудительно удали работающий контейнер abc123",
                "arguments": {"container_id": "abc123", "force": True}
            }
        ]
    ),
    Tool(
        name="get_container_logs",
        description="Get logs from a Docker container",
        parameters=[
            ToolParameter(
                name="container_id",
                type="string",
                description="Container ID or name",
                required=True
            ),
            ToolParameter(
                name="tail",
                type="integer",
                description="Number of lines from the end (default: 100)",
                required=False
            ),
            ToolParameter(
                name="timestamps",
                type="boolean",
                description="Include timestamps in logs (default: false)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "user": "Покажи логи контейнера nginx",
                "arguments": {"container_id": "nginx"}
            },
            {
                "user": "Покажи последние 50 строк логов контейнера app с временными метками",
                "arguments": {"container_id": "app", "tail": 50, "timestamps": True}
            }
        ]
    ),
    Tool(
        name="inspect_container",
        description="Get detailed information about a Docker container",
        parameters=[
            ToolParameter(
                name="container_id",
                type="string",
                description="Container ID or name to inspect",
                required=True
            )
        ],
        fewShotExamples=[
            {
                "user": "Покажи детальную информацию о контейнере nginx",
                "arguments": {"container_id": "nginx"}
            },
            {
                "user": "Что внутри контейнера abc123?",
                "arguments": {"container_id": "abc123"}
            }
        ]
    ),
    Tool(
        name="list_images",
        description="List all Docker images on the system",
        parameters=[],
        fewShotExamples=[
            {
                "user": "Покажи все Docker образы",
                "arguments": {}
            },
            {
                "user": "Какие images есть?",
                "arguments": {}
            }
        ]
    ),
    Tool(
        name="pull_image",
        description="Pull Docker image from registry (Docker Hub by default)",
        parameters=[
            ToolParameter(
                name="image",
                type="string",
                description="Image name (e.g., 'nginx', 'ubuntu', 'myrepo/myimage')",
                required=True
            ),
            ToolParameter(
                name="tag",
                type="string",
                description="Image tag (default: 'latest')",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "user": "Скачай образ nginx",
                "arguments": {"image": "nginx"}
            },
            {
                "user": "Стяни образ ubuntu версии 22.04",
                "arguments": {"image": "ubuntu", "tag": "22.04"}
            },
            {
                "user": "Pull образ python:3.11",
                "arguments": {"image": "python", "tag": "3.11"}
            }
        ]
    ),
    Tool(
        name="remove_image",
        description="Remove Docker image by ID or tag",
        parameters=[
            ToolParameter(
                name="image_id",
                type="string",
                description="Image ID or tag to remove",
                required=True
            ),
            ToolParameter(
                name="force",
                type="boolean",
                description="Force removal (default: false)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "user": "Удали образ nginx",
                "arguments": {"image_id": "nginx"}
            },
            {
                "user": "Принудительно удали образ abc123",
                "arguments": {"image_id": "abc123", "force": True}
            }
        ]
    ),
    Tool(
        name="execute_command",
        description="Execute command inside a running Docker container",
        parameters=[
            ToolParameter(
                name="container_id",
                type="string",
                description="Container ID or name",
                required=True
            ),
            ToolParameter(
                name="command",
                type="string",
                description="Command to execute (e.g., 'ls -la', 'cat /etc/hosts')",
                required=True
            )
        ],
        fewShotExamples=[
            {
                "user": "Выполни ls -la в контейнере nginx",
                "arguments": {"container_id": "nginx", "command": "ls -la"}
            },
            {
                "user": "Запусти команду 'ps aux' в контейнере app",
                "arguments": {"container_id": "app", "command": "ps aux"}
            }
        ]
    ),
    Tool(
        name="get_system_info",
        description="Get Docker system information (version, containers, images, resources)",
        parameters=[],
        fewShotExamples=[
            {
                "user": "Покажи информацию о Docker системе",
                "arguments": {}
            },
            {
                "user": "Сколько контейнеров и образов?",
                "arguments": {}
            }
        ]
    ),
]


def get_tools() -> list[Tool]:
    """Return list of all Docker tools."""
    return TOOLS


def get_tool_by_name(name: str) -> Tool | None:
    """Get tool by name."""
    for tool in TOOLS:
        if tool.name == name:
            return tool
    return None
