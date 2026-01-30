# FileOps MCP Server - Examples

## Running the Server

### Start with default settings

```bash
# Using launcher (recommended)
python launcher.py fileops --no-auth

# Direct execution
python -m fileops.main --no-auth

# Custom root directory
python -m fileops.main --no-auth --root-dir /tmp/my_workspace
```

### Start with authentication

```bash
# Set API key
export MCP_API_KEY=your_secret_key

# Start server
python launcher.py fileops

# Or direct
python -m fileops.main
```

## Tool Usage Examples

### 1. Write File

Create or overwrite a file:

```json
{
  "name": "write_file",
  "arguments": {
    "path": "notes/todo.txt",
    "content": "1. Buy groceries\n2. Call mom\n3. Finish project"
  }
}
```

Append to existing file:

```json
{
  "name": "write_file",
  "arguments": {
    "path": "notes/todo.txt",
    "content": "\n4. Go to gym",
    "append": true
  }
}
```

### 2. Read File

```json
{
  "name": "read_file",
  "arguments": {
    "path": "notes/todo.txt"
  }
}
```

**Response:**
```
File: notes/todo.txt
Size: 67 bytes, 4 lines
Absolute path: /workspace/notes/todo.txt

--- Content ---
1. Buy groceries
2. Call mom
3. Finish project
4. Go to gym
```

### 3. List Directory

List all files:

```json
{
  "name": "list_directory",
  "arguments": {
    "path": "."
  }
}
```

List with pattern:

```json
{
  "name": "list_directory",
  "arguments": {
    "path": "src",
    "pattern": "*.py"
  }
}
```

**Response:**
```
Directory: src
Pattern: *.py
Total entries: 3

Entries:
  📄 main.py (1024 bytes)
  📄 config.py (512 bytes)
  📄 utils.py (768 bytes)
```

### 4. Search Files by Name

Find all Python files:

```json
{
  "name": "search_files",
  "arguments": {
    "pattern": "*.py",
    "recursive": true
  }
}
```

Find test files in specific directory:

```json
{
  "name": "search_files",
  "arguments": {
    "pattern": "test_*.py",
    "path": "tests",
    "recursive": false
  }
}
```

**Response:**
```
Search pattern: *.py
Directory: .
Recursive: true
Found: 12 file(s)

Files:
  📄 src/main.py (1024 bytes)
  📄 src/config.py (512 bytes)
  📄 tests/test_main.py (2048 bytes)
  ...
```

### 5. Search Content in Files

Search for text:

```json
{
  "name": "search_content",
  "arguments": {
    "query": "TODO",
    "file_pattern": "*.py",
    "case_sensitive": false
  }
}
```

Search with regex:

```json
{
  "name": "search_content",
  "arguments": {
    "query": "def\\s+test_\\w+",
    "file_pattern": "test_*.py",
    "path": "tests",
    "regex": true
  }
}
```

Search in specific file types:

```json
{
  "name": "search_content",
  "arguments": {
    "query": "import asyncio",
    "file_pattern": "*.{py,pyi}",
    "case_sensitive": true
  }
}
```

**Response:**
```
Query: TODO
Directory: .
File pattern: *.py
Case sensitive: false
Regex: false
Files searched: 12
Files with matches: 3

📄 src/main.py (2 match(es))
   Line 45: # TODO: Add error handling
   Line 128: # TODO: Optimize performance

📄 tests/test_main.py (1 match(es))
   Line 23: # TODO: Add more test cases
```

### 6. Delete File

```json
{
  "name": "delete_file",
  "arguments": {
    "path": "temp/cache.json"
  }
}
```

**Response:**
```
Deleted file: temp/cache.json
Size: 1024 bytes
```

### 7. Create Directory

```json
{
  "name": "create_directory",
  "arguments": {
    "path": "output/reports/2024"
  }
}
```

**Response:**
```
Created directory: output/reports/2024
Absolute path: /workspace/output/reports/2024
```

### 8. Get File Info

```json
{
  "name": "get_file_info",
  "arguments": {
    "path": "config.json"
  }
}
```

**Response:**
```
Path: config.json
Absolute path: /workspace/config.json
Type: file
Size: 2048 bytes
Modified: 2024-01-15T10:30:45
Created: 2024-01-10T08:15:22
```

## Common Use Cases

### Use Case 1: Generate Report and Save

```python
# AI generates report content
report_content = "Sales Report 2024\n================\n..."

# Save to file
{
  "name": "write_file",
  "arguments": {
    "path": "reports/sales_2024.txt",
    "content": report_content
  }
}
```

### Use Case 2: Find and Update Config Files

```python
# Step 1: Find all config files
{
  "name": "search_files",
  "arguments": {
    "pattern": "*config*.json",
    "recursive": true
  }
}

# Step 2: Read specific config
{
  "name": "read_file",
  "arguments": {
    "path": "app/config.json"
  }
}

# Step 3: Update config (AI modifies content)
{
  "name": "write_file",
  "arguments": {
    "path": "app/config.json",
    "content": updated_config
  }
}
```

### Use Case 3: Search and Replace in Files

```python
# Step 1: Search for old API endpoint
{
  "name": "search_content",
  "arguments": {
    "query": "api.old-domain.com",
    "file_pattern": "*.py"
  }
}

# Step 2: Read each file and replace
{
  "name": "read_file",
  "arguments": {"path": "src/client.py"}
}

# Step 3: Write updated content
{
  "name": "write_file",
  "arguments": {
    "path": "src/client.py",
    "content": updated_content
  }
}
```

### Use Case 4: Organize Files

```python
# Step 1: List files
{
  "name": "list_directory",
  "arguments": {"path": "downloads"}
}

# Step 2: Create organized structure
{
  "name": "create_directory",
  "arguments": {"path": "downloads/images"}
}
{
  "name": "create_directory",
  "arguments": {"path": "downloads/documents"}
}

# Step 3: Would need additional "move_file" tool for completion
# (Could be added in future update)
```

### Use Case 5: Code Analysis

```python
# Find all Python files with specific imports
{
  "name": "search_content",
  "arguments": {
    "query": "^import\\s+requests",
    "file_pattern": "*.py",
    "regex": true
  }
}

# Find all TODO comments in codebase
{
  "name": "search_content",
  "arguments": {
    "query": "TODO|FIXME",
    "file_pattern": "*.{py,js,ts,java}",
    "regex": true,
    "case_sensitive": false
  }
}
```

## Integration with AI Chat

When integrated with GigaChat or other AI models, the AI can:

1. **Save conversation summaries** to files
2. **Search previous conversations** stored in text files
3. **Generate and save reports** based on user requests
4. **Analyze codebases** by searching files and content
5. **Organize documents** by creating directories and moving files

Example AI conversation:

**User:** "Save our conversation to a file"

**AI:** "I'll save the conversation to a file."
```json
{
  "name": "write_file",
  "arguments": {
    "path": "conversations/chat_2024_01_15.txt",
    "content": "[Full conversation content]"
  }
}
```

**User:** "Find all Python files that use asyncio"

**AI:** "I'll search for files using asyncio."
```json
{
  "name": "search_content",
  "arguments": {
    "query": "import asyncio",
    "file_pattern": "*.py",
    "case_sensitive": true
  }
}
```

## Security Notes

1. **Root Directory Isolation**: All file operations are restricted to the configured root directory. Attempts to access files outside will be rejected.

2. **Size Limits**: Files larger than the configured maximum (default 10MB) cannot be read or written.

3. **Authentication**: Use API key authentication in production environments (remove `--no-auth` flag).

4. **Path Validation**: The server validates all paths to prevent directory traversal attacks (`../` escaping).
