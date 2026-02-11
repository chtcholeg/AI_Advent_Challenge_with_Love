# Git MCP Server - Quick Start Guide

Get the Git MCP server running in 5 minutes!

## Prerequisites

- Python 3.10 or higher
- Git installed and in PATH
- A Git repository to work with

## Step 1: Installation (2 minutes)

```bash
# Navigate to mcp-servers directory
cd mcp-servers

# Create and activate virtual environment
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -e .
```

Expected output:
```
Successfully installed fastapi-0.104.0 uvicorn-0.24.0 ...
```

## Step 2: Start Server (1 minute)

```bash
# Start Git MCP server without authentication
python -m git.main --no-auth
```

Expected output:
```
============================================================
Git MCP Server Starting
============================================================
Repository path: /current/directory
Host: 0.0.0.0
Port: 8010
Authentication: DISABLED
============================================================
Registered 11 Git MCP tools
  - git_status: Show the working tree status...
  - git_log: Show commit logs...
  - git_diff: Show changes between commits...
  - git_branch_list: List all branches...
  - git_show_commit: Show information about a specific commit...
  - git_blame: Show what revision and author last modified...
  - git_add: Add file contents to the staging area...
  - git_commit: Record changes to the repository...
  - git_checkout: Switch branches or restore working tree...
  - git_pull: Fetch from and integrate with another repository...
  - git_push: Update remote refs along with associated objects...
INFO:     Started server process
INFO:     Uvicorn running on http://0.0.0.0:8010
```

## Step 3: Verify Server (30 seconds)

Open a new terminal and test:

```bash
# Check health
curl http://localhost:8010/health

# Expected response:
{"status":"healthy","repository":"/your/repo/path"}

# List available tools
curl http://localhost:8010/tools

# Expected: JSON with 11 git tools
```

## Step 4: Add to AI Agent (1 minute)

1. Open **AI Agent** application
2. Go to **Settings** → **MCP Servers**
3. Click **"Add Server"**
4. Fill in the form:
   - **Name:** `Git`
   - **URL:** `http://localhost:8010/sse`
   - **API Key:** *(leave empty)*
5. Click **"Save"**
6. **Enable** the server toggle

## Step 5: Test It! (30 seconds)

In AI Agent, try these commands:

**Example 1: Check Status**
```
You: What's the current git status?

AI: [Calls git_status]

On branch main
Your branch is up to date with 'origin/main'.

nothing to commit, working tree clean
```

**Example 2: View History**
```
You: Show me the last 3 commits

AI: [Calls git_log with max_count=3]

commit abc123...
Author: John Doe
Date: Thu Feb 9 15:30:00 2024

    Add Git MCP server

commit def456...
Author: Jane Smith
Date: Thu Feb 9 14:15:00 2024

    Update documentation
```

**Example 3: View Branches**
```
You: What branches do we have?

AI: [Calls git_branch_list]

Current branch: main

Branches:
* main
  feature/new-tool
  dev
```

## Common Use Cases

### Development Workflow

```
You: Stage all changes and commit with message "Fix bug in parser"

AI: [Calls git_add with paths=["."]
    [Calls git_commit with message="Fix bug in parser"]

Files added to staging area
Staged paths: .

Commit created successfully
Message: Fix bug in parser
[main abc123d] Fix bug in parser
 2 files changed, 15 insertions(+), 3 deletions(-)
```

### Branch Management

```
You: Create a new branch called feature/mcp-integration and switch to it

AI: [Calls git_checkout with branch="feature/mcp-integration", create=true]

Successfully created and checked out branch: feature/mcp-integration
Switched to a new branch 'feature/mcp-integration'
```

### Code Review

```
You: Show me the diff of src/main.py

AI: [Calls git_diff with file_path="src/main.py"]

diff --git a/src/main.py b/src/main.py
index abc123..def456 100644
--- a/src/main.py
+++ b/src/main.py
@@ -10,6 +10,8 @@
 def main():
+    # Initialize MCP server
+    init_mcp()
```

```
You: Who wrote line 42 of config.py?

AI: [Calls git_blame with file_path="config.py"]

abc123 (John Doe 2024-02-09 14:30:00) PORT = 8010
```

## Configuration Options

### Custom Repository

Work with a different repository:

```bash
python -m git.main --repo-path /path/to/other/repo --no-auth
```

### Custom Port

Use a different port:

```bash
python -m git.main --port 8011 --no-auth
```

Then update AI Agent URL to: `http://localhost:8011/sse`

### With Authentication

For production use:

```bash
# Set API key
export MCP_API_KEY="your-secret-key"

# Start with auth enabled
python -m git.main

# In AI Agent, enter the API key when adding the server
```

## Troubleshooting

### "Not a git repository"

```bash
# Make sure you're in a git repository
cd /path/to/your/git/repo

# Or specify repo path
python -m git.main --repo-path /path/to/repo --no-auth
```

### Port Already in Use

```bash
# Use a different port
python -m git.main --port 8011 --no-auth

# Don't forget to update URL in AI Agent
```

### Can't Connect from AI Agent

1. Check server is running:
   ```bash
   curl http://localhost:8010/health
   ```

2. Verify URL in AI Agent: `http://localhost:8010/sse`

3. Check server logs for errors

4. Try restarting both server and AI Agent

### Commands Not Working

1. Ensure Git is installed:
   ```bash
   git --version
   ```

2. Check repository is accessible:
   ```bash
   cd <repo-path>
   git status
   ```

3. Check server logs for detailed error messages

## Next Steps

- Read [git/README.md](git/README.md) for detailed documentation
- Explore all 11 available Git tools
- Configure authentication for production
- Add more MCP servers (weather, time, etc.)

## Tips

1. **Keep It Simple:** Start with read-only commands (status, log, diff)
2. **Be Specific:** Tell AI exactly what you want to see
3. **Chain Commands:** Ask AI to perform multiple git operations
4. **Check First:** Always check status before committing
5. **Use Branches:** Create feature branches for experimentation

## Support

- Check [README.md](README.md) for full documentation
- See [git/README.md](git/README.md) for Git-specific details
- Check server logs for debugging
- Test with `curl` commands to isolate issues

Enjoy using Git MCP server! 🚀
