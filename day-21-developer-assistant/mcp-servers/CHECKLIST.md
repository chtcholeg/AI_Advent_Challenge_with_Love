# Git MCP Server - Installation Checklist

Use this checklist to verify your Git MCP Server installation.

## Prerequisites

- [ ] Python 3.10+ installed
  ```bash
  python3 --version
  # Should show: Python 3.10.x or higher
  ```

- [ ] Git installed
  ```bash
  git --version
  # Should show: git version 2.x.x
  ```

- [ ] You have a Git repository to work with
  ```bash
  cd /path/to/your/repo
  git status
  # Should not show "not a git repository" error
  ```

## Installation

- [ ] Navigate to mcp-servers directory
  ```bash
  cd mcp-servers
  ```

- [ ] Run quick start script
  ```bash
  ./START.sh
  ```

- [ ] Verify server started successfully
  - [ ] See "Git MCP Server Starting" message
  - [ ] See "Registered 11 Git MCP tools" message
  - [ ] See "Uvicorn running on http://0.0.0.0:8010"
  - [ ] No error messages in output

## Server Testing

Open a **new terminal** and run these tests:

- [ ] Test health endpoint
  ```bash
  curl http://localhost:8010/health
  # Expected: {"status":"healthy","repository":"..."}
  ```

- [ ] Test server info
  ```bash
  curl http://localhost:8010/
  # Expected: JSON with server info and capabilities
  ```

- [ ] Test tools list
  ```bash
  curl http://localhost:8010/tools | python3 -m json.tool
  # Expected: List of 11 tools with names and descriptions
  ```

- [ ] Run automated test script
  ```bash
  cd mcp-servers
  ./test_git_server.sh
  # Expected: "All tests passed! ✓"
  ```

## AI Agent Integration

- [ ] Start AI Agent
  ```bash
  ./gradlew :ai-agent:run
  # Wait for UI to load
  ```

- [ ] Navigate to MCP Settings
  - [ ] Click "Settings" in AI Agent
  - [ ] Click "MCP Servers" tab
  - [ ] See list of MCP servers

- [ ] Add Git MCP Server
  - [ ] Click "Add Server" button
  - [ ] Enter Name: `Git`
  - [ ] Enter URL: `http://localhost:8010/sse`
  - [ ] Leave API Key empty
  - [ ] Click "Save"

- [ ] Enable Git Server
  - [ ] Find "Git" in server list
  - [ ] Toggle switch to "Enabled"
  - [ ] Wait 2-3 seconds

- [ ] Verify Connection
  - [ ] Status shows "Connected" (green indicator)
  - [ ] No error messages in AI Agent console
  - [ ] Server logs show "New SSE connection" message

## Functional Testing

Test each category of Git operations:

### Read Operations

- [ ] Test git status
  ```
  User: What's the current git status?
  Expected: AI shows branch, modified files, staged changes
  ```

- [ ] Test git log
  ```
  User: Show me the last 3 commits
  Expected: AI shows commit hashes, authors, dates, messages
  ```

- [ ] Test git diff
  ```
  User: Show me what changed in README.md
  Expected: AI shows diff with +/- lines
  ```

- [ ] Test branch list
  ```
  User: What branches exist?
  Expected: AI lists branches with current branch marked
  ```

- [ ] Test show commit
  ```
  User: Show me the last commit details
  Expected: AI shows full commit info with changes
  ```

- [ ] Test blame
  ```
  User: Who wrote line 10 of main.py?
  Expected: AI shows commit and author for that line
  ```

### Write Operations (Optional - Use with Caution)

- [ ] Test git add
  ```
  User: Stage the changes in test.txt
  Expected: AI stages file successfully
  ```

- [ ] Test git commit
  ```
  User: Commit with message "Test commit"
  Expected: AI creates commit successfully
  ```

- [ ] Test git checkout
  ```
  User: Create and switch to branch test/mcp
  Expected: AI creates and checks out new branch
  ```

## Troubleshooting Checklist

If something doesn't work, check these:

### Server Issues

- [ ] Is Python 3.10+ installed?
- [ ] Is virtual environment activated?
  ```bash
  which python
  # Should show path to venv/bin/python
  ```
- [ ] Are dependencies installed?
  ```bash
  pip list | grep fastapi
  # Should show fastapi and uvicorn
  ```
- [ ] Is port 8010 available?
  ```bash
  lsof -i :8010
  # Should show nothing if port is free
  ```
- [ ] Is Git installed and in PATH?
  ```bash
  which git
  # Should show path to git executable
  ```

### Connection Issues

- [ ] Is server running?
  ```bash
  curl http://localhost:8010/health
  ```
- [ ] Is URL correct in AI Agent?
  - Must be: `http://localhost:8010/sse`
  - Not: `http://localhost:8010` (missing /sse)
- [ ] Is API Key field empty? (for --no-auth mode)
- [ ] Check server logs for error messages
- [ ] Try restarting both server and AI Agent

### Tool Execution Issues

- [ ] Is current directory a Git repository?
  ```bash
  cd /path/to/repo
  git status
  ```
- [ ] Or specify repository path:
  ```bash
  python -m git.main --repo-path /path/to/repo --no-auth
  ```
- [ ] Check Git command works directly:
  ```bash
  git log --max-count=5
  ```
- [ ] Check server logs for detailed error messages

## Success Criteria

Your installation is successful if:

- [x] Server starts without errors
- [x] Health check returns "healthy"
- [x] All 11 tools are listed
- [x] AI Agent shows "Connected" status
- [x] At least one read operation works (e.g., git status)
- [x] Server logs show tool execution requests
- [x] No error messages in either server or AI Agent

## Next Steps

Once everything works:

1. **Explore More Commands**
   - Try different Git operations
   - Ask AI to explain Git commands
   - Let AI help with your workflow

2. **Read Documentation**
   - [QUICKSTART.md](QUICKSTART.md) - Usage examples
   - [INTEGRATION.md](INTEGRATION.md) - Advanced integration
   - [git/README.md](git/README.md) - Full API reference

3. **Configure for Production**
   - Enable authentication
   - Set repository path
   - Configure limits

4. **Add More Servers**
   - Follow the same pattern
   - Create new MCP servers
   - Expand AI Agent capabilities

## Support

If you're stuck:

1. Check server logs (console output)
2. Check AI Agent logs
3. Review documentation files
4. Test with curl commands
5. Verify prerequisites
6. Try on a fresh Git repository

## Cleanup (Optional)

To remove the installation:

```bash
cd mcp-servers

# Stop server (Ctrl+C in server terminal)

# Remove virtual environment
rm -rf venv

# Remove Python cache
find . -type d -name __pycache__ -exec rm -rf {} +
find . -type f -name "*.pyc" -delete
```

To reinstall:
```bash
./START.sh
```

---

✅ **All checks passed?** You're ready to use Git MCP Server!

📚 **Next:** Read [QUICKSTART.md](QUICKSTART.md) for usage examples
