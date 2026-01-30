# Docker MCP Server - Quick Start (5 minutes)

Get Docker MCP Server running and connected to your AI in 5 minutes.

## Prerequisites Check

```bash
# 1. Docker installed?
docker version
# Should show: Docker version 20.10+ or later

# 2. Python 3.10+?
python --version
# Should show: Python 3.10 or later
```

If missing, install:
- **Docker**: https://docs.docker.com/get-docker/
- **Python**: https://www.python.org/downloads/

## Setup (60 seconds)

```bash
# 1. Navigate to project
cd day-15-environment/mcp-servers

# 2. Create virtual environment
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# 3. Install all servers
pip install -e ".[all]"

# Done! ✓
```

## Start Server (10 seconds)

```bash
# Option A: Docker only
python launcher.py docker --no-auth

# Option B: Docker + other servers
python launcher.py docker weather timeservice --no-auth

# Option C: All 7 servers
python launcher.py --all --no-auth
```

**Output should show**:
```
🐳 Docker MCP Server starting on http://127.0.0.1:8006
   SSE endpoint: http://127.0.0.1:8006/sse
   Authentication: disabled
   Tools: 13
```

## Connect to GigaChat App (30 seconds)

1. Open GigaChat app
2. Go to **Settings**
3. Tap **MCP Servers** card
4. Tap **+** (Add Server)
5. Configure:
   ```
   Name:  Docker
   Type:  HTTP
   URL:   http://localhost:8006/sse
   Auth:  (leave empty)
   ```
6. **Enable** the server
7. Verify: **Status** shows `Connected ✓`

## Test It! (30 seconds)

### Test 1: List Containers
```
You: "Show me Docker containers"
AI: [Calls docker_ps, shows table]
```

### Test 2: Start PostgreSQL
```
You: "Start a PostgreSQL database"
AI: [Calls docker_run with postgres:15]
Result: PostgreSQL running on localhost:5432
```

### Test 3: View Logs
```
You: "Show me PostgreSQL logs"
AI: [Calls docker_logs for postgres container]
Result: Log output displayed
```

## Common First Commands

### Development Database
```
You: "I need a dev database"
AI: Starts PostgreSQL with:
  - Port: 5432
  - User: postgres
  - Password: dev123
```

### Redis Cache
```
You: "Start Redis for caching"
AI: Starts Redis on port 6379
```

### Web Server
```
You: "Start nginx on port 8080"
AI: Starts nginx container
```

### Check Status
```
You: "What Docker containers are running?"
AI: Shows table with all containers
```

### Stop Container
```
You: "Stop the postgres container"
AI: Stops container gracefully
```

## What You Can Do Now

### 🎯 Development
- "Set up a PostgreSQL database"
- "Start Redis and MongoDB"
- "Run nginx to serve my files"

### 🔧 Management
- "Show me all running containers"
- "Stop the API container"
- "Restart the web server"

### 🐛 Debugging
- "Show me logs from postgres"
- "Why is my container failing?"
- "Execute ps aux in the container"

### 🏗️ Building
- "Build a Docker image from my Dockerfile"
- "Pull the latest nginx image"
- "List all Docker images"

### 📦 Orchestration
- "Start my docker-compose.yml services"
- "Check status of compose services"
- "Stop all services"

## Troubleshooting

### Docker Not Running
**Error**: `Docker is installed but not running`

**Fix**:
```bash
# macOS/Windows: Start Docker Desktop
# Linux:
sudo systemctl start docker
```

### Port Already in Use
**Error**: `Port 8006 is already in use`

**Fix**:
```bash
# Use different port
python launcher.py docker --no-auth --port 8010

# Or stop conflicting service
lsof -i :8006
kill <PID>
```

### Server Won't Start
**Check**:
```bash
# 1. Python version
python --version  # Must be 3.10+

# 2. Dependencies installed
pip list | grep aiohttp

# 3. Port available
python launcher.py --check
```

### Container Won't Start
**Debug**:
```
You: "Why won't my container start?"
AI will:
  1. Check docker ps
  2. View logs
  3. Diagnose issue
```

## Next Steps

### 📚 Learn More
- [Full Documentation](DOCKER_MCP_GUIDE.md)
- [Usage Examples](mcp-servers/docker/examples/USAGE_EXAMPLES.md)
- [Docker Server README](mcp-servers/docker/README.md)

### 🚀 Try Advanced Features
- Docker Compose orchestration
- Multi-container applications
- Image building
- Command execution in containers

### 🔗 Combine with Other Servers
- Weather + Docker: Deploy weather API
- GitHub + Docker: Clone and build
- FileOps + Docker: Generate Dockerfiles

## Quick Reference

### Available Tools (13 total)

**Containers**:
- docker_ps, docker_run, docker_stop
- docker_start, docker_restart
- docker_logs, docker_exec

**Images**:
- docker_images, docker_pull, docker_build

**Compose**:
- docker_compose_up, docker_compose_down
- docker_compose_ps

### Common Parameters

**docker_run**:
```json
{
  "image": "postgres:15",
  "name": "my-db",
  "ports": ["5432:5432"],
  "env": {"POSTGRES_PASSWORD": "secret"}
}
```

**docker_logs**:
```json
{
  "container": "my-db",
  "tail": 50
}
```

**docker_exec**:
```json
{
  "container": "my-db",
  "command": "psql -U postgres -c 'SELECT version();'"
}
```

## Success Checklist

- [ ] Docker running (`docker version` works)
- [ ] Server started (see 🐳 Docker MCP Server message)
- [ ] GigaChat app connected (Status: Connected ✓)
- [ ] First test successful (docker_ps works)
- [ ] Container started (PostgreSQL or other)

**All checked?** 🎉 You're ready to go!

## Support

Having issues? Check:
1. Docker is running: `docker ps`
2. Server is running: `curl http://localhost:8006/sse`
3. Port is free: `python launcher.py --check`
4. Logs show no errors

Still stuck? Review:
- [Troubleshooting Guide](DOCKER_MCP_GUIDE.md#troubleshooting)
- [Full Documentation](DOCKER_MCP_GUIDE.md)

---

**Time to productivity**: ~5 minutes
**Tools available**: 13 Docker tools
**Ready for**: Development, Testing, Debugging, Deployment
