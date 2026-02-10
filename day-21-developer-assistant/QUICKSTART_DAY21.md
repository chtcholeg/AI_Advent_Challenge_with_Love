# Day 21: Git MCP Server - Quick Start

Get your AI developer assistant with Git capabilities running in 5 minutes!

## What You Get

AI Agent that can:
- ✅ Check repository status
- ✅ View commit history
- ✅ Show file changes (diff)
- ✅ List branches
- ✅ Stage files and create commits
- ✅ Switch branches
- ✅ Pull/push changes

## Prerequisites

```bash
# Check Python (need 3.10+)
python3 --version

# Check Git
git --version

# You're in a Git repository
git status
```

## Step 1: Start Git MCP Server (2 minutes)

```bash
cd mcp-servers
./START.sh
```

Expected output:
```
🚀 Git MCP Server - Quick Start
✓ Python 3.x.x found
✓ Git x.x.x found
📦 Creating virtual environment...
✓ Dependencies installed
🎉 Setup complete!

Starting Git MCP Server...
Repository: /your/current/directory
URL: http://localhost:8010/sse
```

Server is now running! Keep this terminal open.

## Step 2: Add to AI Agent (2 minutes)

Open a **new terminal**:

```bash
# Start AI Agent
./gradlew :ai-agent:run
```

In AI Agent UI:
1. Click **Settings** → **MCP Servers**
2. Click **"Add Server"**
3. Fill in:
   - **Name:** `Git`
   - **URL:** `http://localhost:8010/sse`
   - **API Key:** *(leave empty)*
4. Click **"Save"**
5. Toggle **"Enabled"** switch

Wait 2-3 seconds. Status should show **"Connected"** ✅

## Step 3: Test It! (1 minute)

In AI Agent chat, try:

**Basic Status:**
```
You: What's the current git status?

AI: [Calls git_status]

On branch main
Your branch is up to date with 'origin/main'.
nothing to commit, working tree clean
```

**Commit History:**
```
You: Show me the last 3 commits

AI: [Calls git_log]

commit abc123...
Author: Your Name
Date: Today

    Your recent commit message
...
```

**List Branches:**
```
You: What branches do we have?

AI: [Calls git_branch_list]

Current branch: main

Branches:
* main
  feature/test
  dev
```

## Common Commands

**Check Status:**
- "What's the git status?"
- "Are there any uncommitted changes?"
- "Show me the current branch"

**View History:**
- "Show the last 5 commits"
- "Show commit history for the past week"
- "What did the last commit change?"

**View Changes:**
- "Show me what changed in README.md"
- "What files have been modified?"
- "Show unstaged changes"

**Branch Operations:**
- "List all branches"
- "Create a new branch called feature/test"
- "Switch to the dev branch"

**Stage & Commit:**
- "Stage all changes"
- "Stage the README.md file"
- "Commit with message 'Update documentation'"

**Advanced:**
- "Show who wrote line 42 of config.py"
- "Show details of commit abc123"
- "Pull latest changes from origin"

## Troubleshooting

### Server won't start

```bash
# Check prerequisites
python3 --version  # Need 3.10+
git --version

# Try manual start
cd mcp-servers
python3 -m venv venv
source venv/bin/activate
pip install -e .
python -m git.main --no-auth
```

### Can't connect from AI Agent

```bash
# Test server is running
curl http://localhost:8010/health

# Should return:
{"status":"healthy","repository":"..."}

# Check URL in AI Agent: http://localhost:8010/sse
# Make sure API Key field is empty
```

### Not a git repository

```bash
# Make sure you're in a git repo
cd /path/to/your/git/repo

# Restart server from that directory
cd /path/to/your/git/repo
cd path/to/mcp-servers
./START.sh

# Or specify repo path
python -m git.main --repo-path /path/to/repo --no-auth
```

## Next Steps

1. **Explore More Commands**
   - Ask AI to explain Git commands
   - Try different workflows
   - Let AI help with your commits

2. **Read Documentation**
   - [Full Guide](mcp-servers/QUICKSTART.md) - Detailed examples
   - [Integration](mcp-servers/INTEGRATION.md) - Advanced setup
   - [Git Server](mcp-servers/git/README.md) - Complete API

3. **Customize**
   - Set custom repository path
   - Enable authentication
   - Add more MCP servers

## Files Created

All the code is in `mcp-servers/`:
```
mcp-servers/
├── git/              # Git MCP Server implementation
├── shared/           # Shared MCP components
├── START.sh          # Quick start script (use this!)
├── requirements.txt  # Python dependencies
└── *.md              # Documentation
```

## What Just Happened?

You created:
1. **Python MCP Server** - Runs Git commands via MCP protocol
2. **11 Git Tools** - Status, log, diff, branches, commits, etc.
3. **FastAPI Server** - HTTP/SSE endpoints on port 8010
4. **AI Integration** - Connected to AI Agent via MCP

Now your AI can work with Git repositories! 🎉

## Support

Need help?
- Check [CHECKLIST.md](mcp-servers/CHECKLIST.md) for verification steps
- Read server logs (in terminal where START.sh runs)
- Test with: `curl http://localhost:8010/tools`
- See full docs in `mcp-servers/` directory

---

**Ready to build more?**

This is Day 21 of the AI Advent Challenge. The Git MCP Server is just the beginning - you can create MCP servers for:
- Build tools (gradle, npm, etc.)
- Code analysis
- Testing frameworks
- Package managers
- And much more!

Check [mcp-servers/README.md](mcp-servers/README.md) to learn how to add your own MCP servers.

Happy coding with your AI assistant! 🚀
