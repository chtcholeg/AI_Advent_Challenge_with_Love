# Docker MCP Server Integration Guide

Complete guide for integrating Docker MCP Server with AI applications.

## Overview

The Docker MCP Server connects AI agents to Docker, enabling autonomous container management, image operations, and Docker Compose orchestration. This allows AI to:

- Start and manage development databases
- Run test environments
- Deploy microservices
- Build and manage Docker images
- Execute commands in containers
- Monitor logs and debug issues

## Architecture

```
AI Application (GigaChat)
    ↓
MCP Protocol (HTTP/SSE)
    ↓
Docker MCP Server (port 8006)
    ↓
Docker Daemon (local)
    ↓
Containers & Images
```

## Installation

### Prerequisites

1. **Docker Installed**
   ```bash
   # Check Docker installation
   docker version

   # If not installed, visit:
   # https://docs.docker.com/get-docker/
   ```

2. **Python 3.10+**
   ```bash
   python --version
   ```

3. **MCP Infrastructure**
   ```bash
   cd day-15-environment/mcp-servers
   pip install -e ".[all]"
   ```

### Server Setup

**Option 1: Via Launcher (Recommended)**
```bash
cd day-15-environment/mcp-servers

# Start Docker server only
python launcher.py docker --no-auth

# Start with other servers
python launcher.py docker weather currency --no-auth

# Start all 7 servers
python launcher.py --all --no-auth
```

**Option 2: Direct Start**
```bash
cd day-15-environment/mcp-servers
python -m docker.main --port 8006 --no-auth
```

**Verify Server**
```bash
curl http://localhost:8006/sse
# Should see: MCP SSE endpoint
```

## GigaChat App Integration

### Add Docker Server

1. Open GigaChat app
2. Go to **Settings** → **MCP Servers**
3. Tap **+** (Add Server)
4. Configure:
   - **Name**: `Docker`
   - **Type**: `HTTP`
   - **URL**: `http://localhost:8006/sse`
   - **Auth**: Leave empty (if using --no-auth)
5. **Enable** the server
6. Verify **Status**: `Connected ✓`

### Available Tools

After connection, AI will have access to 13 Docker tools:

#### Container Management (7 tools)
- `docker_ps` - List containers
- `docker_run` - Start new container
- `docker_stop` - Stop container
- `docker_start` - Start stopped container
- `docker_restart` - Restart container
- `docker_logs` - View logs
- `docker_exec` - Execute commands

#### Image Management (3 tools)
- `docker_images` - List images
- `docker_pull` - Pull from registry
- `docker_build` - Build from Dockerfile

#### Docker Compose (3 tools)
- `docker_compose_up` - Start services
- `docker_compose_down` - Stop services
- `docker_compose_ps` - List services

## Usage Examples

### Basic Operations

#### Start a Database
```
User: "Start a PostgreSQL database on port 5432"

AI → docker_run:
  image: postgres:15
  name: dev-postgres
  ports: ["5432:5432"]
  env: {POSTGRES_PASSWORD: "dev123"}

Result: PostgreSQL running at localhost:5432
```

#### Check Running Containers
```
User: "What containers are running?"

AI → docker_ps: {}

Result: Table of containers with status
```

#### View Logs
```
User: "Show me the last 50 logs from postgres"

AI → docker_logs:
  container: dev-postgres
  tail: 50

Result: Last 50 log lines
```

### Development Environment

#### Setup Full Stack
```
User: "Set up a dev environment with PostgreSQL, Redis, and Nginx"

AI executes:
1. docker_run (PostgreSQL on 5432)
2. docker_run (Redis on 6379)
3. docker_run (Nginx on 8080)
4. docker_ps (verify all running)

Result: Complete dev stack running
```

#### Using Docker Compose
```
User: "Start my docker-compose.yml services"

AI → docker_compose_up:
  file: docker-compose.yml
  detach: true

Result: All services from compose file started
```

### Testing & CI/CD

#### Run Integration Tests
```
User: "Set up test environment"

AI → docker_compose_up:
  file: docker-compose.test.yml
  detach: true
  build: true

Result: Test environment ready
```

#### Execute Tests in Container
```
User: "Run pytest in the test container"

AI → docker_exec:
  container: test-runner
  command: "pytest tests/ -v"

Result: Test results displayed
```

### Debugging

#### Investigate Container Issues
```
User: "Why is my API container failing?"

AI process:
1. docker_ps → Check status
2. docker_logs → View errors
3. docker_exec → Inspect processes
4. Provide diagnosis

Result: Root cause identified
```

#### Health Check
```
User: "Is PostgreSQL healthy?"

AI → docker_exec:
  container: postgres
  command: "pg_isready -U postgres"

Result: Health status
```

## Advanced Scenarios

### Multi-Stage Build

```
User: "Build my application with multi-stage Dockerfile"

AI → docker_build:
  path: "."
  tag: "myapp:1.0"
  dockerfile: "Dockerfile.multi"

Result: Optimized image built
```

### Volume Management

```
User: "Start MongoDB with persistent storage"

AI → docker_run:
  image: mongo:7
  name: mongodb
  ports: ["27017:27017"]
  volumes: ["/data/mongo:/data/db"]

Result: MongoDB with persistent data
```

### Network Configuration

```
User: "Create isolated network for microservices"

Note: Custom networks require docker_network_create
(not yet implemented - use Docker Compose instead)

Workaround: Use docker-compose.yml with networks
```

## Best Practices

### 1. Container Naming

Always name containers for easy management:
```
✅ Good: name: "api-v1"
❌ Bad: no name (random generated)
```

### 2. Port Mapping

Use consistent port mapping:
```
✅ Good: ["8080:80"]  # host:container
❌ Bad: ["80:80"]     # may conflict
```

### 3. Environment Variables

Don't hardcode secrets:
```
✅ Good: env: {DB_PASS: os.getenv('DB_PASS')}
❌ Bad: env: {DB_PASS: "hardcoded123"}
```

### 4. Volume Persistence

Use volumes for important data:
```
✅ Good: volumes: ["/data:/var/lib/data"]
❌ Bad: no volumes (data lost on restart)
```

### 5. Cleanup

Always clean up after testing:
```
docker_compose_down with volumes: true
```

## Troubleshooting

### Docker Not Running

**Error**: `Docker is installed but not running`

**Solution**:
```bash
# macOS/Windows
# Start Docker Desktop

# Linux
sudo systemctl start docker
```

### Port Already in Use

**Error**: `Port 5432 is already in use`

**Solution**:
```bash
# Find process using port
lsof -i :5432

# Stop conflicting container
AI: "Stop container using port 5432"
```

### Permission Denied

**Error**: `permission denied while connecting to Docker daemon`

**Solution**:
```bash
# Add user to docker group
sudo usermod -aG docker $USER

# Logout and login again
```

### Server Not Responding

**Error**: Server connection failed

**Solution**:
```bash
# Check if server is running
curl http://localhost:8006/sse

# Restart server
python launcher.py docker --no-auth
```

### Container Exits Immediately

**Issue**: Container starts then stops

**Debug Process**:
```
AI: "Why did my container exit?"
1. docker_ps -a (show all including stopped)
2. docker_logs (view error messages)
3. Fix issue and restart
```

## Security Considerations

### Authentication

**Development** (no auth):
```bash
python launcher.py docker --no-auth
```

**Production** (with auth):
```bash
export MCP_API_KEY="your-secret-key"
python launcher.py docker
```

### Container Security

1. **Use Official Images**: Always prefer verified images
2. **Limit Privileges**: Don't run as root if possible
3. **Network Isolation**: Use Docker networks
4. **Resource Limits**: Set memory/CPU limits (future feature)
5. **Read-Only Volumes**: Use `:ro` flag when possible

### Example Secure Container

```
AI → docker_run:
  image: nginx:alpine  # Official image
  name: web
  ports: ["8080:80"]
  volumes: ["./html:/usr/share/nginx/html:ro"]  # Read-only
  env: {NGINX_PORT: "80"}  # No secrets
```

## Performance Tips

### 1. Use Image Cache

Pull images before starting:
```
AI: "Pull postgres:15 and redis:7 images"
→ docker_pull for each
→ Faster starts later
```

### 2. Prune Unused Resources

Periodically clean up:
```
docker system prune -a --volumes
# (Manual command, not via MCP yet)
```

### 3. Use Alpine Images

Prefer lightweight images:
```
✅ nginx:alpine (5MB)
❌ nginx:latest (100MB+)
```

### 4. Multi-Stage Builds

Use multi-stage Dockerfiles:
```dockerfile
FROM node:18 AS builder
# Build stage

FROM node:18-alpine
# Runtime stage (smaller)
```

## Limitations

Current limitations of Docker MCP Server:

1. **No Interactive TTY**: Can't use interactive commands
2. **No Log Streaming**: Can't follow logs in real-time
3. **No Custom Networks**: Use Docker Compose for networks
4. **No Resource Limits**: Can't set memory/CPU limits yet
5. **Local Only**: Only manages local Docker daemon
6. **No Image Removal**: Can't delete images via tools
7. **No Volume Management**: Use Docker Compose for volumes

## Future Enhancements

Planned features:

- [ ] `docker_network_create` - Custom networks
- [ ] `docker_rm` - Remove containers
- [ ] `docker_rmi` - Remove images
- [ ] `docker_volume_create` - Manage volumes
- [ ] Resource limits (memory, CPU)
- [ ] Health check integration
- [ ] Container stats monitoring
- [ ] Remote Docker daemon support
- [ ] Docker Swarm support

## Testing the Server

### Manual Testing

```bash
# Test Docker availability
docker version

# Start server
python -m docker.main --no-auth

# Test tool (in another terminal)
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

### Integration Testing

```python
# test_docker_mcp.py
import requests

def test_docker_ps():
    response = requests.post(
        "http://localhost:8006/tools/call",
        json={
            "method": "tools/call",
            "params": {
                "name": "docker_ps",
                "arguments": {}
            }
        }
    )
    assert response.status_code == 200
    assert "result" in response.json()
```

## Examples Repository

See `mcp-servers/docker/examples/` for:

- `docker-compose-example.yml` - Multi-service setup
- `Dockerfile-example` - Sample Dockerfile
- `USAGE_EXAMPLES.md` - 30+ usage scenarios

## Related Documentation

- [Docker MCP Server README](mcp-servers/docker/README.md)
- [Main MCP Documentation](mcp-servers/README.md)
- [Launcher Guide](mcp-servers/launcher.py)
- [Docker Official Docs](https://docs.docker.com/)

## Support & Contribution

For issues or questions:
1. Check troubleshooting section
2. Review Docker logs: `docker logs <container>`
3. Test Docker CLI: `docker ps`
4. Verify server: `curl http://localhost:8006/sse`

## Changelog

### Version 1.0.0 (Day 15)
- Initial release
- 13 Docker tools
- Container management
- Image operations
- Docker Compose support
- Comprehensive error handling
- Examples and documentation
