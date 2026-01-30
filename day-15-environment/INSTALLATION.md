# Day 15 Installation Guide

Quick installation and verification guide for Docker MCP Server.

## Prerequisites

### 1. Docker
```bash
docker version
```

If not installed:
- **macOS**: https://docs.docker.com/desktop/install/mac-install/
- **Windows**: https://docs.docker.com/desktop/install/windows-install/
- **Linux**: https://docs.docker.com/engine/install/

### 2. Python 3.10+
```bash
python3 --version
```

Should show: Python 3.10.0 or higher

## Installation Steps

### 1. Navigate to Project
```bash
cd day-15-environment/mcp-servers
```

### 2. Create Virtual Environment
```bash
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

### 3. Install Dependencies
```bash
# Install all servers
pip install -e ".[all]"

# Or install specific servers
pip install -e ".[docker]"
```

### 4. Verify Installation
```bash
# Check launcher recognizes Docker
python launcher.py

# Should show:
# docker       8006     free       Docker container and image management
```

## Starting the Server

### Option 1: Docker Only
```bash
python launcher.py docker --no-auth
```

### Option 2: With Other Servers
```bash
python launcher.py docker weather timeservice --no-auth
```

### Option 3: All Servers
```bash
python launcher.py --all --no-auth
```

## Verification

### 1. Check Server Started
You should see:
```
🐳 Docker MCP Server starting on http://127.0.0.1:8006
   SSE endpoint: http://127.0.0.1:8006/sse
   Authentication: disabled
   Tools: 13
```

### 2. Test Endpoint
In another terminal:
```bash
curl http://localhost:8006/sse
```

Should respond with: `MCP SSE endpoint`

### 3. Run Test Script
```bash
cd docker
./test_docker_mcp.sh
```

Should show:
```
✅ Docker is installed and running
✅ Server is running on port 8006
✅ docker_ps tool works
✅ docker_images tool works
✅ Found 13 tools
🎉 All tests passed!
```

## GigaChat App Integration

1. Open GigaChat app
2. **Settings** → **MCP Servers** → **Add (+)**
3. Configure:
   ```
   Name: Docker
   Type: HTTP
   URL:  http://localhost:8006/sse
   Auth: (leave empty if using --no-auth)
   ```
4. **Enable** the server
5. Check **Status**: Should show `Connected ✓`

## First Test

Ask your AI:
```
"Show me Docker containers"
"Start a PostgreSQL database"
"List all Docker images"
```

## Project Structure

After installation, you should have:

```
day-15-environment/
├── mcp-servers/
│   ├── docker/              ✅ NEW
│   │   ├── main.py         (672 lines)
│   │   ├── README.md       (422 lines)
│   │   ├── __init__.py
│   │   ├── test_docker_mcp.sh
│   │   └── examples/
│   │       ├── USAGE_EXAMPLES.md
│   │       ├── docker-compose-example.yml
│   │       └── Dockerfile-example
│   ├── launcher.py         ✅ UPDATED (added Docker)
│   ├── github/
│   ├── telegram/
│   ├── weather/
│   ├── timeservice/
│   ├── currency/
│   ├── fileops/
│   └── shared/
├── DOCKER_MCP_GUIDE.md     ✅ NEW
├── DOCKER_QUICKSTART.md    ✅ NEW
├── DIFF_14-15.md           ✅ NEW
├── DAY_15_SUMMARY.md       ✅ NEW
└── README.md               ✅ UPDATED
```

## Troubleshooting

### "ModuleNotFoundError: No module named 'fastapi'"
**Solution**: Install dependencies
```bash
pip install -e ".[all]"
```

### "Docker is installed but not running"
**Solution**: Start Docker
```bash
# macOS/Windows: Start Docker Desktop
# Linux: sudo systemctl start docker
```

### "Port 8006 is already in use"
**Solution**: Use different port
```bash
python -m docker.main --port 8010 --no-auth
```

### "Permission denied connecting to Docker"
**Solution**: Add user to docker group
```bash
sudo usermod -aG docker $USER
# Then logout and login again
```

## What's Included

### 7 MCP Servers (Total)
1. GitHub (port 8000)
2. Telegram (port 8001)
3. Weather (port 8002)
4. TimeService (port 8003)
5. Currency (port 8004)
6. FileOps (port 8005)
7. **Docker (port 8006)** ← NEW

### 13 Docker Tools
- Container: ps, run, stop, start, restart, logs, exec
- Images: images, pull, build
- Compose: up, down, ps

### Documentation (5 files)
1. **DOCKER_MCP_GUIDE.md** - Complete integration guide
2. **DOCKER_QUICKSTART.md** - 5-minute quick start
3. **DIFF_14-15.md** - Detailed changelog
4. **DAY_15_SUMMARY.md** - Achievement summary
5. **mcp-servers/docker/README.md** - Server documentation

### Examples (30+ scenarios)
- Development workflows
- Testing patterns
- Debugging examples
- Production use cases
- Multi-server integrations

## Next Steps

### 1. Try Examples
```bash
cd mcp-servers/docker/examples
cat USAGE_EXAMPLES.md
```

### 2. Test Docker Compose
```bash
cd mcp-servers/docker/examples
docker-compose -f docker-compose-example.yml up -d
```

### 3. Ask AI
```
"Set up a development environment"
"Start PostgreSQL and Redis"
"Show me container logs"
```

### 4. Explore Integration
```
"Use Weather MCP to get data and save to containerized MongoDB"
"Clone GitHub repo and build Docker image"
"Monitor Telegram and log to Docker PostgreSQL"
```

## Documentation Links

- [Quick Start (5 min)](DOCKER_QUICKSTART.md)
- [Full Guide](DOCKER_MCP_GUIDE.md)
- [Usage Examples](mcp-servers/docker/examples/USAGE_EXAMPLES.md)
- [Server README](mcp-servers/docker/README.md)
- [Changelog](DIFF_14-15.md)
- [Summary](DAY_15_SUMMARY.md)

## Success Checklist

- [ ] Docker installed and running
- [ ] Python 3.10+ installed
- [ ] Dependencies installed
- [ ] Server starts without errors
- [ ] Test script passes
- [ ] GigaChat app connected
- [ ] First AI command works

**All checked?** 🎉 You're ready!

## Support

If you encounter issues:

1. Check [Troubleshooting](DOCKER_MCP_GUIDE.md#troubleshooting)
2. Run test script: `./test_docker_mcp.sh`
3. Verify Docker: `docker ps`
4. Check logs in server output
5. Review [FAQ](DOCKER_MCP_GUIDE.md)

---

**Installation Time**: ~5 minutes
**Tools Available**: 13 Docker + 45 other = 58 total
**Ready For**: Development, Testing, Debugging, Deployment
