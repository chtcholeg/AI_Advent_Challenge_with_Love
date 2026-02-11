# Git MCP Server - Setup Instructions

Quick guide to get Git MCP Server working with AI Agent.

## 1. Start Git MCP Server

```bash
cd mcp-servers
./START.sh
```

Server will start on `http://localhost:8010`

## 2. Add to AI Agent

1. Run AI Agent:
   ```bash
   ./gradlew :ai-agent:run
   ```

2. In AI Agent UI:
   - Go to **Settings → MCP Servers**
   - Click **"Add Server"**
   - Enter:
     - **Name:** Git
     - **URL:** `http://localhost:8010/sse`
     - **API Key:** (leave empty)
   - Click **"Save"**
   - Toggle server to **Enabled**

3. Verify connection:
   - Server should show **Connected** status (green)

## 3. Use It!

Try these commands in AI Agent:

```
"What's the git status?"
"Show me the last 5 commits"
"What branches do we have?"
"Show diff of src/main.kt"
```

## Available Tools

### Read Operations (Safe)
- `git_status` - Repository status
- `git_log` - Commit history
- `git_diff` - File changes
- `git_branch_list` - List branches
- `git_show_commit` - Commit details
- `git_blame` - Line authorship

### Write Operations (Use with Caution)
- `git_add` - Stage files
- `git_commit` - Create commit
- `git_checkout` - Switch/create branch
- `git_pull` - Pull changes
- `git_push` - Push changes

## Troubleshooting

### Server won't start
```bash
# Check Python and Git are installed
python3 --version
git --version

# Reinstall dependencies
cd mcp-servers
rm -rf venv
./START.sh
```

### Can't connect from AI Agent
```bash
# Test server is running
curl http://localhost:8010/health

# Check URL in AI Agent: http://localhost:8010/sse
# Restart both server and AI Agent
```

### Tools not working
```bash
# Ensure you're in a git repository
cd /path/to/your/repo
git status

# Or specify repo when starting server
cd mcp-servers
python -m git.main --repo-path /path/to/repo --no-auth
```

## Documentation

- [Quick Start (5 min)](mcp-servers/QUICKSTART.md)
- [Full Documentation](mcp-servers/README.md)
- [Integration Guide](mcp-servers/INTEGRATION.md)
- [Git Server Details](mcp-servers/git/README.md)

## Examples

### Check status and commit
```
User: Check git status and if there are changes, stage them and commit

AI: [Calls git_status]
    [Sees changes]
    [Calls git_add with paths=["."]]
    [Calls git_commit with message="..."]

    Changes detected and committed successfully
```

### Create feature branch
```
User: Create a new branch called feature/mcp-git and switch to it

AI: [Calls git_checkout with branch="feature/mcp-git", create=true]

    Successfully created and checked out branch: feature/mcp-git
```

### Review changes
```
User: Show me what changed in the last commit

AI: [Calls git_log with max_count=1]
    [Calls git_show_commit with commit="HEAD"]

    Last commit: abc123
    Author: John Doe
    Message: Add Git MCP server

    [Shows full diff]
```

That's it! You're ready to use Git MCP Server as an AI developer assistant. 🚀
