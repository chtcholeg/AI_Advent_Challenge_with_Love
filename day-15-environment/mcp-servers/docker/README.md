# Docker MCP Server

MCP server for Docker container management. Provides 13 tools for managing Docker containers, images, and Docker Compose services.

## Features

### Container Management
- **docker_ps**: List running containers with status
- **docker_run**: Start a new container with ports, volumes, environment variables
- **docker_stop**: Stop a running container
- **docker_start**: Start a stopped container
- **docker_restart**: Restart a container
- **docker_logs**: Get container logs (last N lines)
- **docker_exec**: Execute commands inside a running container

### Image Management
- **docker_images**: List available Docker images
- **docker_pull**: Pull an image from Docker Hub
- **docker_build**: Build an image from Dockerfile

### Docker Compose
- **docker_compose_up**: Start services from docker-compose.yml
- **docker_compose_down**: Stop and remove Compose services
- **docker_compose_ps**: List Compose services status

## Prerequisites

- Docker installed and running
- Python 3.10+
- MCP server dependencies (installed via parent requirements.txt)

## Installation

```bash
# From mcp-servers directory
pip install -e ".[docker]"

# Or install all servers
pip install -e ".[all]"
```

## Usage

### Start Server

```bash
# Default port 8006
python -m docker.main

# Custom port
python -m docker.main --port 8010

# Disable authentication
python -m docker.main --no-auth
```

### Using Launcher

```bash
# Start docker server
python launcher.py docker

# Start with other servers
python launcher.py docker weather timeservice
```

## Tool Examples

### List Containers

```json
{
  "name": "docker_ps",
  "arguments": {
    "all": true
  }
}
```

### Start PostgreSQL

```json
{
  "name": "docker_run",
  "arguments": {
    "image": "postgres:15",
    "name": "my-postgres",
    "ports": ["5432:5432"],
    "env": {
      "POSTGRES_PASSWORD": "secret",
      "POSTGRES_DB": "myapp"
    }
  }
}
```

### Start Nginx

```json
{
  "name": "docker_run",
  "arguments": {
    "image": "nginx:latest",
    "name": "web-server",
    "ports": ["8080:80"],
    "volumes": ["/local/html:/usr/share/nginx/html:ro"]
  }
}
```

### Get Container Logs

```json
{
  "name": "docker_logs",
  "arguments": {
    "container": "my-postgres",
    "tail": 50
  }
}
```

### Execute Command in Container

```json
{
  "name": "docker_exec",
  "arguments": {
    "container": "my-postgres",
    "command": "psql -U postgres -c 'SELECT version();'"
  }
}
```

### Build Docker Image

```json
{
  "name": "docker_build",
  "arguments": {
    "path": "/path/to/app",
    "tag": "myapp:1.0",
    "dockerfile": "Dockerfile"
  }
}
```

### Docker Compose

```json
{
  "name": "docker_compose_up",
  "arguments": {
    "file": "docker-compose.yml",
    "detach": true,
    "build": false
  }
}
```

## AI Usage Examples

### Start Development Environment

**User**: "Start a PostgreSQL database on port 5432 with password 'dev123'"

**AI calls**:
```json
{
  "name": "docker_run",
  "arguments": {
    "image": "postgres:15",
    "name": "dev-postgres",
    "ports": ["5432:5432"],
    "env": {
      "POSTGRES_PASSWORD": "dev123",
      "POSTGRES_DB": "dev"
    }
  }
}
```

### Check Container Status

**User**: "Show me all running Docker containers"

**AI calls**:
```json
{
  "name": "docker_ps",
  "arguments": {
    "all": false
  }
}
```

### Debug Container

**User**: "Show me the last 100 logs from nginx container"

**AI calls**:
```json
{
  "name": "docker_logs",
  "arguments": {
    "container": "nginx",
    "tail": 100
  }
}
```

**User**: "Execute 'ps aux' inside nginx container"

**AI calls**:
```json
{
  "name": "docker_exec",
  "arguments": {
    "container": "nginx",
    "command": "ps aux"
  }
}
```

### Start Microservices

**User**: "Start all services from docker-compose.yml"

**AI calls**:
```json
{
  "name": "docker_compose_up",
  "arguments": {
    "file": "docker-compose.yml",
    "detach": true
  }
}
```

### Stop Everything

**User**: "Stop and remove all compose services including volumes"

**AI calls**:
```json
{
  "name": "docker_compose_down",
  "arguments": {
    "file": "docker-compose.yml",
    "volumes": true
  }
}
```

## Common Use Cases

### 1. Development Database Setup

```bash
# AI: "Set up a dev database"
docker_run:
  image: postgres:15
  name: dev-db
  ports: ["5432:5432"]
  env: {POSTGRES_PASSWORD: "dev", POSTGRES_DB: "myapp"}
```

### 2. Testing Environment

```bash
# AI: "Create a testing environment with Redis and PostgreSQL"
docker_compose_up:
  file: docker-compose.test.yml
  detach: true
```

### 3. Container Troubleshooting

```bash
# AI: "Debug why my container is failing"
1. docker_ps (check status)
2. docker_logs (view errors)
3. docker_exec (inspect inside)
```

### 4. Image Management

```bash
# AI: "Pull latest nginx and list all images"
1. docker_pull: {image: "nginx:latest"}
2. docker_images
```

### 5. Cleanup

```bash
# AI: "Stop all containers and clean up"
1. docker_ps: {all: true}
2. docker_stop: {container: "..."}
3. docker_compose_down: {volumes: true}
```

## Configuration

### Environment Variables

- `MCP_API_KEY`: API key for authentication (optional)

### Port Configuration

Default port: 8006
Can be changed via `--port` argument

## Error Handling

The server includes comprehensive error handling:

- Docker not installed: Returns clear error message
- Docker not running: Suggests starting Docker
- Container not found: Returns specific error
- Permission errors: Returns actionable error message
- Timeout handling: 30s default, 5min for pull/build

## Security Notes

1. **Container Isolation**: All containers run with Docker's default security settings
2. **Command Execution**: `docker_exec` runs commands via `sh -c` for security
3. **File Access**: No direct file system access - only via Docker volumes
4. **Authentication**: API key authentication can be enabled via `MCP_API_KEY`

## Limitations

- No interactive mode for docker_exec (single command execution only)
- docker_logs does not support follow mode (would block server)
- Maximum command timeout: 10 minutes for builds
- Requires Docker to be installed and running locally

## Troubleshooting

### Docker Not Found

```
Error: Docker is not installed. Please install Docker first.
```

**Solution**: Install Docker from https://docs.docker.com/get-docker/

### Docker Not Running

```
Error: Docker is installed but not running. Please start Docker.
```

**Solution**: Start Docker Desktop or Docker daemon

### Permission Denied

```
Error: permission denied while trying to connect to Docker daemon
```

**Solution**: Add user to docker group or run with appropriate permissions

### Port Already in Use

```
Error: Port 8006 is already in use
```

**Solution**: Use `--port` to specify different port or stop conflicting service

## Integration with GigaChat App

Add to Settings → MCP Servers:

- **Name**: Docker
- **Type**: HTTP
- **URL**: `http://localhost:8006/sse`
- **Auth**: Optional (if MCP_API_KEY is set)

Then ask AI:
- "Start a PostgreSQL database"
- "Show me running containers"
- "Stop the nginx container"
- "Build my Docker image from current directory"

## Development

### Adding New Tools

1. Create new class inheriting from `DockerTool`
2. Define `name`, `description`, and `input_schema`
3. Implement `execute()` method
4. Register in `create_server()`

### Testing

```bash
# Test Docker availability
docker version

# Test server
python -m docker.main --no-auth

# Test tool via curl
curl -X POST http://localhost:8006/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "docker_ps",
      "arguments": {}
    }
  }'
```

## Related Documentation

- [MCP Protocol](../README.md)
- [Shared Components](../shared/)
- [Launcher](../launcher.py)
- [Docker Documentation](https://docs.docker.com/)
