# Git MCP Server

Git repository operations via Model Context Protocol (MCP).

## Overview

The Git MCP Server provides AI agents with the ability to interact with Git repositories through a standardized MCP interface. It exposes common Git operations as tools that can be called by AI systems.

## Features

### Read-Only Operations
- `git_status` - Show working tree status
- `git_log` - Show commit history
- `git_diff` - Show changes between commits or working tree
- `git_branch_list` - List all branches
- `git_show_commit` - Show details of a specific commit
- `git_blame` - Show what revision and author last modified each line

### Write Operations
- `git_add` - Stage files for commit
- `git_commit` - Create a new commit
- `git_checkout` - Switch branches or create new ones
- `git_pull` - Fetch and merge changes from remote
- `git_push` - Upload local commits to remote

## Installation

```bash
cd mcp-servers

# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install fastapi uvicorn
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `GIT_REPO_PATH` | Path to Git repository | Current directory |
| `HOST` | Server host | 0.0.0.0 |
| `PORT` | Server port | 8010 |
| `MCP_API_KEY` | API key for authentication | "" (disabled) |
| `NO_AUTH` | Disable authentication | false |
| `GIT_MAX_LOG_ENTRIES` | Max log entries | 100 |
| `GIT_MAX_DIFF_LINES` | Max diff lines | 1000 |

## Usage

### Start Server

```bash
# Without authentication (recommended for local development)
python -m git.main --no-auth

# With authentication
export MCP_API_KEY="your-secret-key"
python -m git.main

# Specify repository path
python -m git.main --repo-path /path/to/repo --no-auth

# Custom host and port
python -m git.main --host 127.0.0.1 --port 8010 --no-auth
```

### Add to AI Agent

In your ai-agent settings, add the Git MCP server:

1. Go to Settings → MCP Servers
2. Click "Add Server"
3. Enter details:
   - **Name:** Git
   - **URL:** `http://localhost:8010/sse`
   - **API Key:** (leave empty if using `--no-auth`)
4. Enable the server

### Example Interactions

#### Check Repository Status
```
User: What's the current git status?
AI: [Calls git_status]

    On branch main
    Your branch is up to date with 'origin/main'.

    Changes not staged for commit:
      modified:   README.md
```

#### View Recent Commits
```
User: Show me the last 5 commits
AI: [Calls git_log with max_count=5]

    commit abc123...
    Author: John Doe
    Date: 2024-02-09

        Add Git MCP server implementation
```

#### Create and Commit Changes
```
User: Stage all changes and commit with message "Update documentation"
AI: [Calls git_add with paths=["."]
    [Calls git_commit with message="Update documentation"]

    Files added to staging area
    Commit created successfully: Update documentation
```

#### Switch Branch
```
User: Create and switch to a new branch called feature/mcp-git
AI: [Calls git_checkout with branch="feature/mcp-git", create=true]

    Successfully created and checked out branch: feature/mcp-git
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Server info |
| `/health` | GET | Health check |
| `/tools` | GET | List available tools |
| `/sse` | GET | SSE connection for MCP |
| `/message?sessionId=...` | POST | Send MCP message |

## Tool Schemas

All tools follow the MCP tool interface with:
- `name`: Unique identifier
- `description`: Human-readable description for AI
- `inputSchema`: JSON Schema for parameters
- `execute()`: Async execution handler

Example tool structure:

```python
class GitStatusTool(GitTool):
    name = "git_status"
    description = "Show the working tree status"
    input_schema = {
        "type": "object",
        "properties": {
            "short": {"type": "boolean"}
        }
    }

    async def execute(self, arguments: dict) -> ToolResult:
        # Implementation
        pass
```

## Security Considerations

### Authentication
- Use API key authentication in production
- Set `MCP_API_KEY` environment variable
- Pass key in `Authorization` header: `Bearer your-key`

### Repository Access
- Server only accesses the configured repository path
- Cannot access files outside the repository
- All git operations run with server process permissions

### Safe Operations
- Read-only tools are safe by default
- Write operations (add, commit, push) should be used carefully
- Consider enabling authentication for write operations

## Troubleshooting

### "Not a git repository" Error
```bash
# Ensure you're running in a git repository
cd /path/to/your/repo
python -m git.main --no-auth

# Or specify repo path
python -m git.main --repo-path /path/to/repo --no-auth
```

### Connection Refused
```bash
# Check if server is running
curl http://localhost:8010/health

# Check firewall settings
# Ensure port 8010 is not blocked
```

### Authentication Failed
```bash
# Ensure API key matches
export MCP_API_KEY="your-key"

# Or disable authentication
python -m git.main --no-auth
```

## Development

### Project Structure
```
git/
├── __init__.py          # Package initialization
├── config.py            # Configuration and env vars
├── git_client.py        # Git command executor
├── tools.py             # MCP tool definitions
├── main.py              # FastAPI server
└── README.md            # This file
```

### Adding New Tools

1. Create tool class in `tools.py`:
```python
class MyGitTool(GitTool):
    name = "git_my_command"
    description = "Description for AI"
    input_schema = {...}

    async def execute(self, arguments: dict) -> ToolResult:
        # Implementation
        pass
```

2. Add to `get_all_tools()` in `tools.py`
3. Restart server

### Testing

```bash
# Test health endpoint
curl http://localhost:8010/health

# List tools
curl http://localhost:8010/tools

# Test with MCP client
# (Use ai-agent or another MCP client)
```

## License

Part of the GigaChat Multiplatform Chat App project.

## References

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Git Documentation](https://git-scm.com/doc)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
