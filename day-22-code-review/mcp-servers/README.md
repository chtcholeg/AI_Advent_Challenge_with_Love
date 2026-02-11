# MCP Servers for AI Agent

Collection of MCP (Model Context Protocol) servers for the AI Agent project.

## Available Servers

### Git MCP Server (port 8010)

Git repository operations via MCP protocol.

**Features:**
- Repository status and history
- Branch management
- Commit operations
- File staging and changes
- Pull/push operations

**Documentation:** [git/README.md](git/README.md)

## Quick Start

### Installation

```bash
cd mcp-servers

# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install Git MCP server
pip install -e .
```

### Start Git Server

```bash
# Without authentication (recommended for local use)
python -m git.main --no-auth

# Or use the installed command
mcp-git --no-auth

# With custom repository path
mcp-git --repo-path /path/to/repo --no-auth
```

### Add to AI Agent

1. Launch the AI Agent application
2. Go to **Settings → MCP Servers**
3. Click **"Add Server"**
4. Enter server details:
   - Name: `Git`
   - URL: `http://localhost:8010/sse`
   - API Key: (leave empty for `--no-auth`)
5. Click **"Save"** and enable the server

## Configuration

### Environment Variables

```bash
# Git server settings
export GIT_REPO_PATH="/path/to/repository"  # Default: current directory
export HOST="0.0.0.0"                        # Default: 0.0.0.0
export PORT="8010"                           # Default: 8010

# Authentication (optional)
export MCP_API_KEY="your-secret-key"
export NO_AUTH="true"                        # Disable auth
```

### Command Line Options

```bash
python -m git.main --help

Options:
  --host HOST          Host to bind to (default: 0.0.0.0)
  --port PORT          Port to bind to (default: 8010)
  --no-auth            Disable authentication
  --repo-path PATH     Git repository path (default: current directory)
```

## Usage Examples

### With AI Agent

Once the Git MCP server is added and enabled in AI Agent:

**Check Repository Status:**
```
User: What's the current git status?
AI: [Uses git_status tool]
    Shows modified files, staged changes, and branch info
```

**View Commit History:**
```
User: Show me the last 10 commits
AI: [Uses git_log tool with max_count=10]
    Displays commit history with authors and messages
```

**Stage and Commit Changes:**
```
User: Stage all changes and commit with message "Add Git MCP server"
AI: [Uses git_add with paths=["."]
    [Uses git_commit with message="Add Git MCP server"]
    Files staged and committed successfully
```

**Create Feature Branch:**
```
User: Create a new branch called feature/new-tool
AI: [Uses git_checkout with branch="feature/new-tool", create=true]
    Branch created and checked out
```

## Architecture

### Project Structure

```
mcp-servers/
├── shared/                 # Shared MCP components
│   ├── __init__.py
│   └── models.py          # ToolResult, BaseTool
│
├── git/                   # Git MCP Server
│   ├── __init__.py
│   ├── config.py          # Configuration
│   ├── git_client.py      # Git command executor
│   ├── tools.py           # MCP tool definitions
│   ├── main.py            # FastAPI server
│   └── README.md          # Git server docs
│
├── requirements.txt       # Python dependencies
├── pyproject.toml         # Package configuration
└── README.md              # This file
```

### MCP Protocol Flow

```
AI Agent                Git MCP Server              Git Repository
   |                           |                           |
   |--[HTTP/SSE Connect]------>|                           |
   |<------[Endpoint URL]------|                           |
   |                           |                           |
   |--[tools/list]------------>|                           |
   |<------[Tool List]---------|                           |
   |                           |                           |
   |--[tools/call: git_status]>|--[git status]------------>|
   |                           |<------[Status Output]-----|
   |<------[Tool Result]-------|                           |
```

### Tool Interface

Each MCP tool implements:

```python
class GitTool(BaseTool):
    name: str              # Tool identifier
    description: str       # Description for AI
    input_schema: dict     # JSON Schema for parameters

    async def execute(self, arguments: dict) -> ToolResult:
        # Execute git command
        # Return result with content and error flag
```

## API Endpoints

All servers expose the following endpoints:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Server info and capabilities |
| `/health` | GET | Health check |
| `/tools` | GET | List available MCP tools |
| `/sse` | GET | SSE connection for MCP protocol |
| `/message?sessionId=<id>` | POST | Send MCP protocol messages |

## Security

### Authentication

**Development (Recommended):**
```bash
# Disable authentication for local development
python -m git.main --no-auth
```

**Production:**
```bash
# Enable API key authentication
export MCP_API_KEY="your-secret-key-here"
python -m git.main

# AI Agent will need to provide the key
```

### Repository Access

- Server only accesses configured repository path
- Cannot access files outside repository
- Runs with server process permissions
- Consider read-only operations for untrusted contexts

## Troubleshooting

### Server Won't Start

```bash
# Check if port is already in use
lsof -i :8010

# Use different port
python -m git.main --port 8011 --no-auth
```

### "Not a git repository" Error

```bash
# Ensure you're in a git repository
cd /path/to/your/git/repo
python -m git.main --no-auth

# Or specify repository path
python -m git.main --repo-path /path/to/repo --no-auth
```

### Connection Issues in AI Agent

```bash
# Test server is running
curl http://localhost:8010/health

# Check server logs
python -m git.main --no-auth  # Watch console output

# Verify URL in AI Agent settings
# URL should be: http://localhost:8010/sse
```

### Tool Execution Fails

```bash
# Check git is installed
git --version

# Check repository is accessible
cd <repo-path> && git status

# Check server logs for detailed errors
```

## Development

### Adding New Servers

To add a new MCP server following the same pattern:

1. Create new directory: `mcp-servers/myserver/`
2. Copy structure from `git/`:
   ```
   myserver/
   ├── __init__.py
   ├── config.py
   ├── my_client.py      # External service client
   ├── tools.py          # MCP tool definitions
   ├── main.py           # FastAPI server
   └── README.md
   ```

3. Import shared components:
   ```python
   from shared import ToolResult, BaseTool
   ```

4. Update `pyproject.toml`:
   ```toml
   [project.scripts]
   mcp-myserver = "myserver.main:main"
   ```

### Testing

```bash
# Test Git server health
curl http://localhost:8010/health

# List available tools
curl http://localhost:8010/tools

# Use AI Agent to test tool execution
# Enable server and ask AI to perform git operations
```

## Contributing

When adding new Git tools:

1. Add command method to `git_client.py`
2. Create tool class in `tools.py`
3. Register tool in `get_all_tools()`
4. Update documentation
5. Test with AI Agent

## References

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [Git Documentation](https://git-scm.com/doc)
- [AI Advent Challenge with Love](../../README.md)

## License

Part of the GigaChat Multiplatform Chat App project.
