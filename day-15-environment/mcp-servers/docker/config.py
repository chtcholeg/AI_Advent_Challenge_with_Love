"""Docker MCP Server configuration."""

SERVER_NAME = "docker"
SERVER_VERSION = "1.0.0"
DESCRIPTION = "Docker container and image management via MCP protocol"

# Docker connection
DEFAULT_DOCKER_HOST = "unix:///var/run/docker.sock"  # Local Docker daemon
# For remote Docker: "tcp://host:2375" or via SSH: "ssh://user@host"
