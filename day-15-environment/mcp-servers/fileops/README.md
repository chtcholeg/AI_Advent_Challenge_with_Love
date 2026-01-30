# FileOps MCP Server

File system operations MCP server providing tools for reading, writing, searching files and directories.

## Features

### Tools

1. **write_file** - Write content to a file
   - Parameters: `path`, `content`, `append` (optional)
   - Creates parent directories automatically
   - Supports both overwrite and append modes

2. **read_file** - Read content from a file
   - Parameters: `path`
   - Returns file content with metadata (size, line count)

3. **list_directory** - List files in a directory
   - Parameters: `path` (optional), `pattern` (optional)
   - Supports glob patterns for filtering

4. **search_files** - Search for files by name pattern
   - Parameters: `pattern`, `path` (optional), `recursive` (optional)
   - Uses glob syntax: `*.py`, `test_*.txt`, `**/*.js`

5. **search_content** - Search for text within files
   - Parameters: `query`, `path` (optional), `file_pattern` (optional), `case_sensitive`, `regex`
   - Supports both literal text and regex patterns
   - Shows matching lines with line numbers

6. **delete_file** - Delete a file
   - Parameters: `path`

7. **create_directory** - Create a directory
   - Parameters: `path`
   - Creates parent directories if needed

8. **get_file_info** - Get file/directory information
   - Parameters: `path`
   - Returns type, size, timestamps

## Security

- **Root directory isolation**: All operations are restricted to a configurable root directory
- **Path validation**: Prevents access outside root directory (no `../` escape)
- **Size limits**: Maximum file size configurable (default: 10MB)
- **Result limits**: Maximum search results configurable (default: 100)

## Configuration

Environment variables:

```bash
# Authentication
MCP_API_KEY=your_secret_key  # Required unless --no-auth

# Server
HOST=0.0.0.0                 # Bind address
PORT=8005                    # Bind port

# FileOps-specific
FILEOPS_ROOT_DIR=/path/to/root        # Root directory (default: current dir)
FILEOPS_MAX_FILE_SIZE=10485760        # Max file size in bytes (default: 10MB)
FILEOPS_MAX_SEARCH_RESULTS=100        # Max search results (default: 100)
```

## Running

### Via Launcher (Recommended)

```bash
# Start fileops server
python launcher.py fileops

# Start without authentication
python launcher.py fileops --no-auth

# Start all servers including fileops
python launcher.py --all
```

### Direct Execution

```bash
# With authentication (requires MCP_API_KEY)
python -m fileops.main

# Without authentication
python -m fileops.main --no-auth

# Custom port and root directory
python -m fileops.main --port 8005 --root-dir /tmp
```

### Installed Command

```bash
# After pip install -e ".[all]"
mcp-fileops --no-auth
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Server information |
| `/health` | GET | Health check (shows active sessions, root dir) |
| `/sse` | GET | SSE connection for MCP (requires auth if enabled) |
| `/message` | POST | Send MCP message (requires `sessionId` param) |
| `/tools` | GET | List available tools |
| `/docs` | GET | OpenAPI documentation |

## Usage Examples

### Write a file

```json
{
  "method": "tools/call",
  "params": {
    "name": "write_file",
    "arguments": {
      "path": "test.txt",
      "content": "Hello, World!"
    }
  }
}
```

### Read a file

```json
{
  "method": "tools/call",
  "params": {
    "name": "read_file",
    "arguments": {
      "path": "test.txt"
    }
  }
}
```

### Search files by pattern

```json
{
  "method": "tools/call",
  "params": {
    "name": "search_files",
    "arguments": {
      "pattern": "*.py",
      "recursive": true
    }
  }
}
```

### Search content in files

```json
{
  "method": "tools/call",
  "params": {
    "name": "search_content",
    "arguments": {
      "query": "def main",
      "file_pattern": "*.py",
      "case_sensitive": false
    }
  }
}
```

### List directory

```json
{
  "method": "tools/call",
  "params": {
    "name": "list_directory",
    "arguments": {
      "path": ".",
      "pattern": "*"
    }
  }
}
```

## Testing

```bash
# Check server status
curl http://localhost:8005/health

# List available tools
curl http://localhost:8005/tools

# With authentication
curl -H "X-API-Key: your_secret_key" http://localhost:8005/health
```

## Integration with GigaChat

Add to MCP server configuration in your app:

```kotlin
McpServer(
    id = "fileops",
    name = "FileOps",
    transport = McpTransport.HTTP,
    host = "localhost",
    port = 8005,
    apiKey = "your_secret_key",  // or null if --no-auth
    enabled = true
)
```

The AI will be able to use all file operations tools automatically through function calling.
