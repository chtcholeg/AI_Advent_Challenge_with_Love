# FileOps MCP Server - Quick Start

## What is FileOps?

FileOps is an MCP (Model Context Protocol) server that allows AI assistants to work with files on your computer. The AI can read, write, search, and manage files through a secure interface.

## Quick Start

### 1. Start the Server

```bash
# Using launcher (recommended)
python launcher.py fileops --no-auth

# Or direct
python -m fileops.main --no-auth
```

**Output:**
```
FileOps MCP Server v1.0.0
  Address:      0.0.0.0:8005
  Auth:         DISABLED
  Root dir:     /current/directory
  Max file size: 10485760 bytes
  Tools:        write_file, read_file, search_files, search_content, ...
```

### 2. Test the Server

```bash
# Check health
curl http://localhost:8005/health

# List tools
curl http://localhost:8005/tools
```

### 3. Configure in GigaChat App

Add this server in your app's MCP settings:

```
Name: FileOps
Host: localhost
Port: 8005
Transport: HTTP
API Key: (leave empty if --no-auth)
```

## What Can It Do?

### 8 Tools Available:

1. **write_file** - Create or update files
   - "Save this code to main.py"
   - "Append this line to my notes"

2. **read_file** - Read file contents
   - "Show me the contents of config.json"
   - "Read the README file"

3. **list_directory** - List files in a folder
   - "What files are in the src directory?"
   - "List all Python files here"

4. **search_files** - Find files by name
   - "Find all .py files"
   - "Search for test files"

5. **search_content** - Search text in files
   - "Find all TODO comments in my code"
   - "Search for 'import asyncio' in Python files"

6. **delete_file** - Remove files
   - "Delete temp.txt"

7. **create_directory** - Create folders
   - "Create a reports directory"

8. **get_file_info** - Get file details
   - "Show me info about config.json"

## Usage Examples

### Save AI Response to File

**You:** "Save our conversation to a file called chat_log.txt"

**AI:** Uses `write_file` tool to save the conversation.

### Search Code

**You:** "Find all Python files that use asyncio"

**AI:** Uses `search_content` with pattern "import asyncio" in *.py files.

### Organize Files

**You:** "Create a folder called 'archive' and list all files here"

**AI:** Uses `create_directory` then `list_directory`.

## Configuration Options

### Change Root Directory

Restrict file operations to a specific directory:

```bash
python -m fileops.main --no-auth --root-dir /path/to/workspace
```

### Enable Authentication

```bash
export MCP_API_KEY=your_secret_key
python -m fileops.main
```

Then add the API key in your app's MCP configuration.

### Adjust File Size Limit

```bash
export FILEOPS_MAX_FILE_SIZE=52428800  # 50MB
python -m fileops.main --no-auth
```

## Security

✅ **Safe by default:**
- All operations restricted to root directory
- Cannot access files outside workspace
- No `../` directory traversal allowed
- File size limits prevent memory issues

⚠️ **Important:**
- Set `FILEOPS_ROOT_DIR` to a safe directory in production
- Use `MCP_API_KEY` when exposing to network
- Review files before sharing workspace access

## Troubleshooting

### Port Already in Use

```bash
# Check what's using port 8005
lsof -i :8005

# Or use different port
python -m fileops.main --no-auth --port 8006
```

### Permission Denied

Make sure the root directory is writable:

```bash
# Check permissions
ls -ld /path/to/workspace

# Or use a different directory
python -m fileops.main --no-auth --root-dir ~/Documents
```

### Server Not Responding

Check if server is running:

```bash
curl http://localhost:8005/health
```

If no response, check logs or restart the server.

## Next Steps

- See **README.md** for full documentation
- See **EXAMPLES.md** for detailed usage examples
- See **../FILEOPS_IMPLEMENTATION.md** for technical details

## Support

For issues or questions:
1. Check server logs in terminal
2. Verify root directory permissions
3. Test with curl commands
4. Check MCP app connection settings
