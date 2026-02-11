# Integration Guide: Git MCP Server with AI Agent

This guide explains how to integrate the Git MCP Server with the AI Agent application.

## Prerequisites

- AI Agent application built and running
- Git MCP Server installed and running
- Basic understanding of MCP protocol

## Architecture Overview

```
┌─────────────────┐         HTTP/SSE         ┌──────────────────┐
│   AI Agent      │◄─────────────────────────┤  Git MCP Server  │
│   (Kotlin/KMP)  │                          │  (Python/FastAPI)│
└─────────────────┘                          └──────────────────┘
        │                                            │
        │ MCP Protocol Messages                      │
        │ (JSON-RPC 2.0)                             │
        │                                            │
        ▼                                            ▼
┌─────────────────┐                          ┌──────────────────┐
│  McpRepository  │                          │   Git Commands   │
│  McpClient      │                          │   (subprocess)   │
└─────────────────┘                          └──────────────────┘
```

## Step-by-Step Integration

### 1. Start Git MCP Server

```bash
cd mcp-servers
source venv/bin/activate  # If not already activated
python -m git.main --no-auth
```

The server will start on `http://localhost:8010` by default.

### 2. Verify Server is Running

```bash
# Test health
curl http://localhost:8010/health

# Expected response:
{
  "status": "healthy",
  "repository": "/path/to/current/repo"
}
```

### 3. Add Server in AI Agent UI

#### Option A: Using UI (Recommended)

1. Launch AI Agent application:
   ```bash
   cd ai-agent
   ./gradlew :ai-agent:run
   ```

2. Navigate to **Settings → MCP Servers**

3. Click **"Add Server"** button

4. Fill in the server details:
   - **Name:** `Git`
   - **Description:** `Git repository operations` (optional)
   - **URL:** `http://localhost:8010/sse`
   - **API Key:** (leave empty for `--no-auth`)

5. Click **"Save"**

6. Toggle the server to **Enabled** state

#### Option B: Using Database (Advanced)

If you need to add the server programmatically:

```kotlin
// In your code
val mcpRepository = get<McpRepository>()

val gitServer = McpServer(
    id = UUID.randomUUID().toString(),
    name = "Git",
    url = "http://localhost:8010/sse",
    apiKey = "", // Empty for no auth
    enabled = true,
    status = ConnectionStatus.DISCONNECTED
)

mcpRepository.addServer(gitServer)
```

### 4. Verify Connection

After adding the server:

1. Check the MCP Servers list in Settings
2. The Git server should show status: **Connected** (green)
3. If status is **Error** (red), check server logs

### 5. Test Tool Execution

In the AI Agent chat:

```
User: What's the git status?

Expected: AI calls git_status tool and displays repository status
```

## Available Tools

The Git MCP Server provides these tools:

### Read-Only Tools (Safe)

| Tool | Description | Example |
|------|-------------|---------|
| `git_status` | Show repository status | "What's the current status?" |
| `git_log` | Show commit history | "Show last 5 commits" |
| `git_diff` | Show changes | "What files changed?" |
| `git_branch_list` | List branches | "What branches exist?" |
| `git_show_commit` | Show commit details | "Show commit abc123" |
| `git_blame` | Show line authors | "Who wrote config.py line 42?" |

### Write Tools (Use with Caution)

| Tool | Description | Example |
|------|-------------|---------|
| `git_add` | Stage files | "Stage all changes" |
| `git_commit` | Create commit | "Commit with message 'Fix bug'" |
| `git_checkout` | Switch/create branch | "Switch to main branch" |
| `git_pull` | Pull from remote | "Pull latest changes" |
| `git_push` | Push to remote | "Push to origin" |

## Configuration Options

### Server Configuration

Edit `mcp-servers/git/config.py` or use environment variables:

```bash
# Repository path
export GIT_REPO_PATH="/path/to/repo"

# Server host/port
export HOST="0.0.0.0"
export PORT="8010"

# Authentication
export MCP_API_KEY="your-secret-key"
export NO_AUTH="false"

# Limits
export GIT_MAX_LOG_ENTRIES="100"
export GIT_MAX_DIFF_LINES="1000"
```

### AI Agent Configuration

The AI Agent's MCP client is configured in:
- `ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/data/repository/McpRepositoryImpl.kt`
- Database: `~/.ai-agent/mcp.db` (SQLDelight)

## Troubleshooting

### Server Not Connecting

**Symptom:** Server shows "Error" status in AI Agent

**Solutions:**

1. Check server is running:
   ```bash
   curl http://localhost:8010/health
   ```

2. Check URL in AI Agent settings (must be `http://localhost:8010/sse`)

3. Check server logs for errors:
   ```bash
   python -m git.main --no-auth
   # Watch console output
   ```

4. Restart both server and AI Agent

### Tools Not Appearing

**Symptom:** Git server connected but no tools available

**Solutions:**

1. Verify tools are registered:
   ```bash
   curl http://localhost:8010/tools | python3 -m json.tool
   ```

2. Check AI Agent logs for tool listing errors

3. Reconnect the server:
   - Disable server in settings
   - Wait 2 seconds
   - Enable server again

### Tool Execution Fails

**Symptom:** Tool calls return errors

**Solutions:**

1. Check Git is installed:
   ```bash
   git --version
   ```

2. Verify repository is accessible:
   ```bash
   cd <repo-path>
   git status
   ```

3. Check permissions (especially for write operations)

4. Review server logs for detailed error messages

### Authentication Issues

**Symptom:** 401 Unauthorized errors

**Solutions:**

1. If using `--no-auth`, ensure API Key field is empty in AI Agent

2. If using authentication:
   - Verify `MCP_API_KEY` is set on server
   - Verify same key is entered in AI Agent settings
   - Key format: just the key value (no "Bearer " prefix)

## Advanced: Custom Repository

To work with a different repository:

### Temporary (Session Only)

```bash
# Start server with custom repo
python -m git.main --repo-path /path/to/other/repo --no-auth
```

### Permanent (Environment Variable)

```bash
# Add to ~/.bashrc or ~/.zshrc
export GIT_REPO_PATH="/path/to/repo"

# Restart server
python -m git.main --no-auth
```

### Per-Server Instance

Run multiple Git servers for different repositories:

```bash
# Server 1: Project A (port 8010)
GIT_REPO_PATH="/path/to/project-a" python -m git.main --no-auth --port 8010

# Server 2: Project B (port 8011)
GIT_REPO_PATH="/path/to/project-b" python -m git.main --no-auth --port 8011
```

Add both in AI Agent with different names:
- Git Project A: `http://localhost:8010/sse`
- Git Project B: `http://localhost:8011/sse`

## Security Best Practices

### Development

For local development, `--no-auth` is fine:
```bash
python -m git.main --no-auth
```

### Production

For production or shared environments:

1. **Enable Authentication:**
   ```bash
   export MCP_API_KEY="$(openssl rand -base64 32)"
   python -m git.main
   ```

2. **Use HTTPS (if remote):**
   - Put server behind reverse proxy (nginx, caddy)
   - Use SSL certificates
   - Update URL in AI Agent: `https://your-domain.com/sse`

3. **Restrict Operations:**
   - Consider read-only mode (remove write tools)
   - Use separate repository for testing
   - Run with limited user permissions

4. **Network Security:**
   - Bind to localhost only: `--host 127.0.0.1`
   - Use firewall rules
   - VPN for remote access

## Example: Complete Workflow

### Setup

```bash
# Terminal 1: Start Git MCP Server
cd mcp-servers
source venv/bin/activate
python -m git.main --no-auth

# Terminal 2: Start AI Agent
cd ai-agent
./gradlew :ai-agent:run
```

### In AI Agent UI

1. Add Git server (see step 3 above)
2. Enable the server
3. Wait for "Connected" status

### Usage

```
User: Check git status

AI: [Calls git_status]
On branch main
Your branch is up to date with 'origin/main'.
nothing to commit, working tree clean

User: Show last 3 commits

AI: [Calls git_log with max_count=3]
commit abc123...
Author: John Doe
...

User: Create branch feature/new-tool

AI: [Calls git_checkout with branch="feature/new-tool", create=true]
Switched to a new branch 'feature/new-tool'

User: Stage all changes and commit

AI: [Calls git_add with paths=["."]]
    [Calls git_commit with message="..."]
Files staged and committed successfully
```

## Monitoring

### Server Logs

Watch server logs in real-time:
```bash
python -m git.main --no-auth | tee git-mcp.log
```

### AI Agent Logs

Check AI Agent console output for:
- MCP connection events
- Tool execution requests
- Error messages

### Health Checks

Automated health monitoring:
```bash
# Simple script
while true; do
  curl -s http://localhost:8010/health || echo "Server down!"
  sleep 30
done
```

## Next Steps

- Explore other MCP servers (FileOps, Docker, etc.)
- Implement custom Git workflows
- Add authentication for production
- Create MCP server for your own services

## Support

For issues or questions:
- Check server logs
- Review AI Agent MCP client code
- Test with `curl` to isolate problems
- See [README.md](README.md) and [git/README.md](git/README.md)
