# Project Management MCP Server

Python-based MCP server for project task management with AI-powered priority analysis.

## Overview

The Project Management MCP Server provides comprehensive task management capabilities for AI Agent, including:

- ✅ **Task CRUD** - Create, read, update, delete tasks
- 🔍 **Filtering & Search** - Filter by status, priority, assignee, labels
- 📊 **Dashboard & Metrics** - Project statistics, velocity, team workload
- 🤖 **AI Analysis** - GigaChat-powered priority analysis and recommendations
- 🔗 **Git Integration** - Link tasks with branches and commits

## Architecture

```
┌─────────────────────────────────────────────┐
│       Project Management MCP Server         │
│                                             │
│  ┌──────────────┐  ┌────────────────────┐ │
│  │ FastAPI/SSE  │  │   Data Storage     │ │
│  │   (HTTP)     │  │   (JSON Files)     │ │
│  └──────────────┘  └────────────────────┘ │
│                                             │
│  ┌──────────────┐  ┌────────────────────┐ │
│  │ AI Service   │  │ Dashboard Service  │ │
│  │ (GigaChat)   │  │   (Metrics)        │ │
│  └──────────────┘  └────────────────────┘ │
└─────────────────────────────────────────────┘
              ↓
        MCP Protocol
              ↓
          AI Agent
```

## Features

### Task Management

**Task Model:**
```json
{
  "id": "task_001",
  "project_id": "proj_001",
  "title": "Task title",
  "description": "Detailed description",
  "status": "in_progress",
  "priority": "high",
  "assignee": "developer1",
  "labels": ["feature", "ai"],
  "git_branch": "feature/my-feature",
  "git_commits": ["abc123"],
  "created_at": "2026-02-15T10:00:00Z",
  "updated_at": "2026-02-15T14:00:00Z",
  "due_date": "2026-02-20",
  "estimated_hours": 16,
  "spent_hours": 8
}
```

**Statuses:**
- `open` - New task
- `in_progress` - Being worked on
- `review` - Code review
- `done` - Completed
- `blocked` - Blocked by dependencies
- `cancelled` - Cancelled

**Priorities:**
- `low` - Low priority
- `medium` - Medium priority
- `high` - High priority
- `critical` - Critical/urgent

### Dashboard Metrics

**Project Dashboard:**
- Total, completed, in-progress, blocked tasks
- Completion rate (%)
- Status distribution
- Priority distribution
- Time tracking (estimated vs spent)
- Overdue tasks
- Critical tasks
- Recent activity
- Velocity (tasks per week)
- Estimated completion time

**Team Workload:**
- Tasks by assignee
- Open/In Progress/Completed breakdown
- Estimated vs spent hours
- Load score (weighted by priority)
- Utilization (%)

### AI-Powered Analysis

**Priority Analysis:**
- Analyzes all project tasks
- Identifies priority issues
- Suggests priority changes
- Recommends execution order
- Identifies risks and mitigation strategies
- Provides actionable insights

**Priority Suggestion (for new tasks):**
- Analyzes task description
- Considers project context
- Suggests priority level
- Estimates hours
- Suggests labels
- Confidence score

## API Reference

### Tools

#### create_task

Create a new task in the project.

**Input:**
```json
{
  "title": "Task title",
  "description": "Description",
  "priority": "medium",
  "assignee": "developer1",
  "labels": ["feature"],
  "due_date": "2026-02-20",
  "estimated_hours": 8
}
```

**Output:**
```json
{
  "success": true,
  "task": { /* task object */ },
  "message": "Task task_013 created successfully"
}
```

#### list_tasks

List tasks with optional filters.

**Input:**
```json
{
  "status": "open",
  "priority": "high",
  "assignee": "developer1",
  "label": "feature",
  "limit": 50
}
```

**Output:**
```json
{
  "success": true,
  "tasks": [ /* array of tasks */ ],
  "count": 5
}
```

#### get_task

Get task details by ID.

**Input:**
```json
{
  "task_id": "task_001"
}
```

**Output:**
```json
{
  "success": true,
  "task": { /* task object */ }
}
```

#### update_task

Update task fields including status, priority, assignee, time tracking, and git info.

**Input:**
```json
{
  "task_id": "task_001",
  "status": "in_progress",
  "priority": "high",
  "assignee": "developer1",
  "spent_hours": 4
}
```

**Updatable fields:**
- `status` - Task status (open, in_progress, review, done, blocked, cancelled)
- `priority` - Priority level (low, medium, high, critical)
- `title` - Task title
- `description` - Task description
- `assignee` - Assigned user
- `labels` - Array of labels
- `due_date` - Due date (YYYY-MM-DD)
- `estimated_hours` - Estimated hours
- `spent_hours` - Actual hours spent
- `git_branch` - Git branch name
- `git_commits` - Array of commit hashes

**Output:**
```json
{
  "success": true,
  "task": { /* updated task with new values */ },
  "message": "Task task_001 updated successfully"
}
```

#### delete_task

Delete a task.

**Input:**
```json
{
  "task_id": "task_001"
}
```

**Output:**
```json
{
  "success": true,
  "message": "Task task_001 deleted successfully"
}
```

#### get_project_dashboard

Get project dashboard with statistics.

**Input:**
```json
{
  "project_id": "proj_001"
}
```

**Output:**
```json
{
  "success": true,
  "project_id": "proj_001",
  "timestamp": "2026-02-15T12:00:00Z",
  "summary": {
    "total_tasks": 12,
    "completed_tasks": 4,
    "in_progress_tasks": 1,
    "blocked_tasks": 1,
    "completion_rate": 33.33
  },
  "status_distribution": { /* counts by status */ },
  "priority_distribution": { /* counts by priority */ },
  "time_tracking": {
    "total_estimated_hours": 142,
    "total_spent_hours": 79,
    "efficiency": 179.75
  },
  "overdue_tasks": [ /* overdue task summaries */ ],
  "critical_tasks": [ /* critical task summaries */ ],
  "recent_activity": [ /* recent updates */ ],
  "velocity": {
    "tasks_per_week": 2.8,
    "estimated_completion": "3 weeks"
  }
}
```

#### get_team_workload

Get team workload by assignee.

**Input:**
```json
{
  "project_id": "proj_001"
}
```

**Output:**
```json
{
  "success": true,
  "project_id": "proj_001",
  "assignees": {
    "developer1": {
      "total_tasks": 5,
      "open_tasks": 2,
      "in_progress_tasks": 1,
      "completed_tasks": 2,
      "estimated_hours": 60,
      "spent_hours": 31,
      "load_score": 3.2,
      "utilization": 51.67,
      "tasks": [ /* task summaries */ ]
    }
  },
  "summary": {
    "total_assignees": 2,
    "most_loaded": "developer2",
    "least_loaded": "developer1"
  }
}
```

#### analyze_priorities

AI-powered priority analysis (requires GigaChat).

**Input:**
```json
{
  "project_id": "proj_001"
}
```

**Output:**
```json
{
  "success": true,
  "analysis": {
    "summary": "Overall priority analysis",
    "recommendations": [
      {
        "task_id": "task_001",
        "current_priority": "medium",
        "suggested_priority": "high",
        "reason": "Blocking other tasks",
        "urgency_score": 0.85
      }
    ],
    "execution_order": ["task_002", "task_001", "task_003"],
    "risks": [
      {
        "task_id": "task_001",
        "risk": "Risk description",
        "mitigation": "How to mitigate"
      }
    ],
    "insights": ["Insight 1", "Insight 2"]
  },
  "tasks_analyzed": 12
}
```

#### suggest_priority

AI-powered priority suggestion for new task (requires GigaChat).

**Input:**
```json
{
  "title": "Add dark theme support",
  "description": "Implement dark theme in UI"
}
```

**Output:**
```json
{
  "success": true,
  "suggestion": {
    "suggested_priority": "high",
    "confidence": 0.85,
    "reasoning": "UI feature with high user impact",
    "estimated_hours": 12,
    "labels": ["feature", "ui"],
    "dependencies": ["task_001"],
    "risks": "Potential CSS conflicts"
  }
}
```

## Configuration

### Environment Variables

```bash
# Server
PM_HOST=0.0.0.0                # Host to bind (default: 0.0.0.0)
PM_PORT=8012                   # Port to bind (default: 8012)
PM_NO_AUTH=true                # Disable authentication (default: true)
MCP_API_KEY=your_key           # API key for auth (if enabled)

# Data
PM_DATA_DIR=./pm/data          # Data directory (default: ./pm/data)

# AI Analysis (optional, for AI features)
GIGACHAT_CLIENT_ID=your_id     # GigaChat client ID
GIGACHAT_CLIENT_SECRET=secret  # GigaChat client secret
PM_USE_AI=true                 # Enable AI analysis (default: true)
```

### Data Files

**tasks.json** - Task database with sequential ID metadata:
```json
{
  "metadata": {
    "last_task_id": 14
  },
  "tasks": [
    {
      "id": "task_001",
      "project_id": "proj_001",
      ...
    }
  ]
}
```

**Note:** The server supports both old format (simple array) and new format (with metadata). Old format files are automatically migrated on first load. Use `python3 pm/migrate_tasks.py` to explicitly migrate existing data.

**projects.json** - Project database:
```json
[
  {
    "id": "proj_001",
    "name": "Project Name",
    "description": "Description",
    "team": ["dev1", "dev2"],
    ...
  }
]
```

## Installation

### Dependencies

```bash
pip install fastapi uvicorn httpx pydantic python-dotenv
```

Or:

```bash
pip install -r requirements.txt
```

### Quick Start

```bash
# Navigate to mcp-servers
cd mcp-servers

# Activate virtual environment
source venv/bin/activate

# Start server
python -m pm.main --no-auth
```

### With Docker (TODO)

```bash
docker build -t pm-mcp-server .
docker run -p 8012:8012 \
  -v $(pwd)/data:/app/data \
  -e GIGACHAT_CLIENT_ID=your_id \
  -e GIGACHAT_CLIENT_SECRET=secret \
  pm-mcp-server
```

## Usage Examples

### Direct API Testing

```bash
# Check server status
curl http://localhost:8012/

# SSE connection
curl http://localhost:8012/sse
```

### With AI Agent

1. Add server in AI Agent Settings:
   - URL: `http://localhost:8012/sse`
   - Auth: empty (if PM_NO_AUTH=true)

2. Use /task command:
   ```
   /task list
   /task status
   /task priorities
   /task create New task description
   ```

### Programmatic Usage

```python
import httpx
import json

async def call_tool(tool_name, arguments):
    async with httpx.AsyncClient() as client:
        response = await client.post(
            "http://localhost:8012/message",
            json={
                "jsonrpc": "2.0",
                "id": "1",
                "method": "tools/call",
                "params": {
                    "name": tool_name,
                    "arguments": arguments
                }
            }
        )
        return response.json()

# List tasks
result = await call_tool("list_tasks", {"status": "open"})

# Create task
result = await call_tool("create_task", {
    "title": "New task",
    "priority": "high",
    "assignee": "developer1"
})

# Get dashboard
result = await call_tool("get_project_dashboard", {"project_id": "proj_001"})
```

## Development

### Project Structure

```
pm/
├── __init__.py
├── main.py               # FastAPI server
├── config.py             # Configuration
├── ai_service.py         # GigaChat AI service
├── dashboard_service.py  # Dashboard metrics
├── requirements.txt      # Dependencies
└── data/
    ├── tasks.json        # Task database
    ├── projects.json     # Project database
    └── sprints.json      # Sprint data (TODO)
```

### Adding New Features

1. **New tool:** Add to `tools/list` in `main.py`
2. **New metric:** Add to `DashboardService` in `dashboard_service.py`
3. **New AI analysis:** Add to `AiService` in `ai_service.py`

### Testing

```bash
# Unit tests (TODO)
pytest tests/

# Manual testing
python -m pm.main --no-auth
# In another terminal:
curl -X POST http://localhost:8012/message -H "Content-Type: application/json" -d '{...}'
```

## Troubleshooting

### Server won't start

**Problem:** `ModuleNotFoundError: No module named 'pm'`

**Solution:**
```bash
cd mcp-servers
export PYTHONPATH="${PYTHONPATH}:$(pwd)"
python -m pm.main --no-auth
```

### AI analysis not working

**Problem:** `AI analysis is disabled`

**Solution:**
1. Set environment variables:
   ```bash
   export GIGACHAT_CLIENT_ID="your_id"
   export GIGACHAT_CLIENT_SECRET="your_secret"
   export PM_USE_AI=true
   ```
2. Restart server

### Tasks not found

**Problem:** `No tasks found`

**Solution:**
1. Check data file exists:
   ```bash
   cat pm/data/tasks.json
   ```
2. Validate JSON:
   ```bash
   cat pm/data/tasks.json | jq .
   ```
3. Check file permissions

## Limitations

- No built-in authentication (use reverse proxy if needed)
- JSON file storage (not suitable for large scale)
- Single project focus (multi-project support TODO)
- No real-time notifications
- No task comments/attachments

## Roadmap

- [ ] Sprint management
- [ ] Story points support
- [ ] Multi-project support
- [ ] SQLite storage option
- [ ] Webhook notifications
- [ ] Task comments and attachments
- [ ] Time tracking integration
- [ ] GitHub Issues sync
- [ ] Advanced filtering (queries)
- [ ] Burndown chart data

## License

Educational purposes. Part of GigaChat Multiplatform Chat App project.

## See Also

- [Sequential ID Update](SEQUENTIAL_ID_UPDATE.md) - Documentation on sequential task IDs and status updates
- [Day 24 README](../../day-24-docs/DAY_24_README.md) - Team Assistant overview
- [Setup Guide](../../day-24-docs/SETUP_GUIDE.md) - Installation instructions
- [Test Scenarios](../../day-24-docs/TEST_SCENARIOS.md) - Testing guide
