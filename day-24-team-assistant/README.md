# GigaChat Multiplatform Chat Application (Day 24 - Team Assistant)

A cross-platform chat application built with Kotlin Compose Multiplatform that integrates with GigaChat AI. The application runs on Android and Desktop (JVM).

## Updates in Day 24: Team Assistant

This version adds a **Team Assistant** - an intelligent project management assistant that combines RAG (project knowledge), MCP servers (Git, CRM, PM), and AI-powered analysis to help teams manage tasks, analyze priorities, and track progress.

### New in Day 24

**Team Assistant** - Complete project management with AI insights:
- **Project Management MCP Server**: Task CRUD, filtering, dashboard metrics
- **AI Priority Analysis**: GigaChat-powered recommendations and insights
- **Dashboard & Metrics**: Velocity, workload, completion tracking
- **Full Integration**: RAG (knowledge) + Git (code) + CRM (users) + PM (tasks)
- **Natural Interface**: `/task` command with intuitive subcommands

See [day-24-docs/DAY_24_README.md](day-24-docs/DAY_24_README.md) for full documentation.

### Quick Start

```bash
# 1. Start all MCP Servers (Git + CRM + PM)
export GIGACHAT_CLIENT_ID="..." GIGACHAT_CLIENT_SECRET="..."
cd mcp-servers && ./START.sh

# 2. Run AI Agent
./gradlew :ai-agent:run

# 3. Add PM Server in Settings
URL: http://localhost:8012/sse

# 4. Use team management
/task status              # Project dashboard
/task priorities          # AI priority analysis
/task create <desc>       # Create task with AI
/task list status=open    # Filter tasks
```

### Key Features

**PM MCP Server (Port 8012):**
- `create_task` - Create tasks with AI-suggested priorities
- `list_tasks` - Filter by status, priority, assignee, label
- `get_task` - Detailed task information
- `update_task` - Update task fields
- `delete_task` - Delete a task
- `get_project_dashboard` - Full project metrics
- `get_team_workload` - Team capacity analysis
- `analyze_priorities` - AI-powered priority recommendations
- `suggest_priority` - AI analysis for new tasks

**Dashboard Metrics:**
```
Project Statistics:
- Status distribution (done, in_progress, open, blocked)
- Priority distribution (critical, high, medium, low)
- Completion rate (33.3% - 4/12 tasks)
- Velocity (2.8 tasks/week)
- Team workload (developer1: 3.2, developer2: 4.1)
- Overdue & critical tasks tracking
```

**AI Priority Analysis:**
```
AI Recommendations:
- Analyzes all project tasks
- Suggests priority changes with reasoning
- Identifies risks and mitigation strategies
- Recommends optimal execution order
- Provides actionable insights
```

---

## Previous Updates

### Day 23: User Support Assistant

Added **User Support Assistant** - a specialized AI Agent mode that combines RAG (knowledge base search), CRM integration (user data & tickets), and empathetic communication to provide automated technical support.

**Key Features:**
- **RAG Integration**: Search solutions in FAQ and documentation
- **CRM MCP Server**: Access user data, tickets, and history
- **Smart Search**: LLM-powered query expansion with morphological analysis
- **Empathetic Communication**: Specialized support prompt for user-friendly responses
- **Dual Source Display**: Shows both RAG sources (docs) and CRM sources (tickets)
- **Ticket Management**: Update ticket status, add notes, escalate issues

See [support-docs/DAY_23_README.md](support-docs/DAY_23_README.md) for full documentation.

**Quick Start:**

```bash
# 1. Start CRM MCP Server (requires GigaChat credentials)
export GIGACHAT_CLIENT_ID="..." GIGACHAT_CLIENT_SECRET="..."
cd mcp-servers && ./START.sh

# 2. Index FAQ documents for RAG
./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-knowledge.json md"

# 3. Run AI Agent
./gradlew :ai-agent:run

# 4. In chat, use support mode
/support Почему не работают напоминания в Telegram?
```

**CRM MCP Server (Port 8011):**
- `get_user` - Retrieve user profile and tier information
- `get_user_tickets` - Fetch user's ticket history
- `search_tickets` - Smart search with LLM query expansion
- `update_ticket_status` - Update ticket status (open/in_progress/resolved/closed)
- `create_ticket` - Create new support tickets

**Smart Search with LLM:**
```
User query: "не работает авторизация"

LLM expands to:
- авторизация, authentication, логин, вход, login
- auth, креденшелы, credentials, токен, token
- Invalid credentials, authentication failed
```

### Support Workflow

```
User Question
    ↓
1. Identify user (CRM)
    → get_user
    → get_user_tickets
    ↓
2. Search for solution
    → RAG search (FAQ)
    → search_tickets (similar issues)
    ↓
3. Personalized response
    → Consider user tier
    → Reference history
    → Provide specific steps
    ↓
4. Update ticket
    → update_ticket_status
    → Add resolution notes
```

---

### Day 22: Code Review Assistant

Added **Code Review** capabilities with specialized bug detection:

- **`/review-pr` command**: Automated code review for GitHub Pull Requests
- **Specialized Checklists**: Technology-specific bug detection (Kotlin Coroutines, Flow, MVI, Repository Pattern, Python Async, SQL, Config Security)
- **Critical Bug Detection**: Arithmetic errors, GlobalScope leaks, regression detection
- **Large PR Support**: Handles PRs >20000 lines with fallback method

**Example Usage:**
```
/review-pr 5          # Review GitHub PR #5
/review-pr            # Review local uncommitted changes
```

### Day 21: Developer Assistant with Git MCP

Added **Git MCP Server** - a Python-based MCP server that enables AI Agent to work with Git repositories:

**11 Git Tools Available:**
- Read-only: `git_status`, `git_log`, `git_diff`, `git_branch_list`, `git_show_commit`, `git_blame`
- Write operations: `git_add`, `git_commit`, `git_checkout`, `git_pull`, `git_push`

**Quick Start:**
```bash
cd mcp-servers
./START.sh  # Automated setup and launch
```

**Integration:**
- Add server in AI Agent: Settings → MCP Servers
- URL: `http://localhost:8010/sse`
- No authentication required for local development

**Documentation:**
- [Quick Start Guide](mcp-servers/QUICKSTART.md) - 5-minute setup
- [Integration Guide](mcp-servers/INTEGRATION.md) - AI Agent integration
- [Git Server README](mcp-servers/git/README.md) - Full documentation

### Current Features Summary

- **Team Assistant** (Day 24): Project management with AI priority analysis and full integration
- **User Support Assistant** (Day 23): Automated technical support with RAG + CRM
- **Code Review** (Day 22): Automated PR review with specialized bug detection
- **Git MCP Server** (Day 21): AI assistant for Git repository operations
- **Clickable Web Sources** (Day 19): Click on source references to open web pages in browser
- **RAG Reranking** (Day 18): Two-stage retrieval with relevance filtering
- **RAG Query** (Day 17): Retrieval-Augmented Generation in AI Agent
- **Document Indexing** (Day 16): Full pipeline for loading, embedding, and searching documents
- **MCP Composition** (Days 11-15): 8 MCP servers with Docker integration
- **Telegram Reminders** (Day 13): Local tool integration with channel monitoring
- **Chat History** (Day 10): SQLite storage with session management
- **Dialog Compression** (Day 9): Smart summarization
- **Response Modes** (Days 4-5): Normal, JSON, XML, Dialog, Step-by-Step, Expert Panel

---

## Architecture

This project follows the MVI (Model-View-Intent) architecture pattern:

- **Model**: Immutable data models representing the application state
- **View**: Compose UI components that render the state
- **Intent**: User actions that trigger state changes
- **Store**: Central state management that processes intents and updates state

### Tech Stack

- **Kotlin Multiplatform**: Shared code across platforms
- **Compose Multiplatform**: UI framework
- **Ktor**: HTTP client for API calls
- **Kotlinx Serialization**: JSON serialization/deserialization
- **Kotlinx Coroutines**: Asynchronous programming
- **Koin**: Dependency injection
- **SQLDelight**: Cross-platform SQLite database
- **GigaChat API**: AI chatbot backend with function calling and embeddings
- **HuggingFace API**: Alternative model integration
- **Ollama**: Local embeddings for document indexing
- **Apache PDFBox**: PDF text extraction (Desktop)

## Project Structure

```
day-24-team-assistant/
├── ai-agent/                    # AI Agent app with full integration
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/ru/chtcholeg/agent/
│       │       ├── data/        # AgentRepository, McpRepository, RagRepository
│       │       ├── domain/      # AgentMessage, CommandHandler (with /task), ToolExecutor
│       │       │   └── service/ # TicketSourceParser, SpecializedChecklists
│       │       └── presentation/ # AgentScreen (MVI), Settings
├── shared/                      # Shared services module
├── indexer/                     # Document Indexer GUI
├── mcp-servers/                 # Python MCP servers
│   ├── git/                     # Git MCP Server (Day 21)
│   ├── crm/                     # CRM MCP Server (Day 23)
│   ├── pm/                      # PM MCP Server (Day 24)
│   │   ├── main.py             # FastAPI server
│   │   ├── config.py           # Configuration
│   │   ├── ai_service.py       # GigaChat AI analysis
│   │   ├── dashboard_service.py # Metrics calculation
│   │   └── data/
│   │       ├── tasks.json      # Task database
│   │       └── projects.json   # Project database
│   └── START.sh                # Quick start for all MCP servers (Git + CRM + PM)
├── day-24-docs/                # Team Assistant documentation
│   ├── DAY_24_README.md       # Day 24 overview
│   ├── SETUP_GUIDE.md         # Setup instructions
│   └── TEST_SCENARIOS.md      # Test scenarios
├── support-docs/               # Support knowledge base (Day 23)
├── docs/                       # Additional documentation
└── README.md                   # This file
```

## Prerequisites

Before you begin, ensure you have the following installed:

- **JDK 17 or higher**: Required for Kotlin and Gradle
- **Python 3.8+**: Required for MCP servers
- **Android Studio**: For Android development and building
- **IntelliJ IDEA** (optional): Recommended for multiplatform development
- **Android SDK**: For Android builds (can be installed via Android Studio)

## Getting GigaChat API Credentials

To use this application, you need GigaChat API credentials:

1. Visit [GigaChat Developer Portal](https://developers.sber.ru/portal/products/gigachat)
2. Sign up or log in to your account
3. Create a new application/project
4. Obtain your **Client ID** and **Client Secret**

## Setup Instructions

### 1. Clone or Download the Project

```bash
cd /path/to/day-24-team-assistant
```

### 2. Configure API Credentials

The application uses Gradle properties to manage API credentials securely.

1. Copy the template file:
   ```bash
   cp local.properties.template local.properties
   ```

2. Edit `local.properties` and add your GigaChat credentials:
   ```properties
   gigachat.clientId=your_actual_client_id
   gigachat.clientSecret=your_actual_client_secret
   ```

   **Important:** The `local.properties` file is automatically excluded from version control (listed in `.gitignore`), so your credentials will remain secure.

### 3. Set Up MCP Servers

```bash
cd mcp-servers

# Create and activate virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Configure environment (required for PM AI analysis and CRM smart search)
export GIGACHAT_CLIENT_ID="your_client_id"
export GIGACHAT_CLIENT_SECRET="your_client_secret"
export CRM_USE_LLM_SEARCH=true  # Enable smart search with LLM
```

### 4. Build the Project

```bash
./gradlew build
```

The BuildKonfig plugin will generate configuration constants from your `local.properties` file during the build process.

## Running the Application

### Team Assistant Workflow

#### 1. Start MCP Servers

```bash
cd mcp-servers
./START.sh  # Starts Git (8010), CRM (8011), and PM (8012) servers
```

You should see three servers starting on ports 8010, 8011, and 8012.

#### 2. Index Project Documentation (optional, for RAG)

```bash
./gradlew :shared:runIndexing --args="index ./docs ./project-knowledge.json md txt"
```

#### 3. Run AI Agent

```bash
./gradlew :ai-agent:run
```

#### 4. Configure Settings

In AI Agent Settings:
- **MCP Servers**: Add PM server at `http://localhost:8012/sse`
- **MCP Servers**: Add CRM server at `http://localhost:8011/sse`
- **MCP Servers**: Add Git server at `http://localhost:8010/sse`
- **RAG Mode**: ON (if using project documentation)
- **Index Path**: `./project-knowledge.json`

#### 5. Use Team Assistant

```
/task status              # Project dashboard with metrics
/task priorities          # AI priority analysis
/task create <desc>       # Create task with AI-suggested priority
/task list status=open    # Filter tasks
/task <task_id>           # View task details
```

### Support Assistant Workflow

#### 1. Start MCP Servers

```bash
cd mcp-servers
./START.sh
```

#### 2. Index Support Documentation

```bash
./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-knowledge.json md"
```

#### 3. Run AI Agent

```bash
./gradlew :ai-agent:run
```

#### 4. Configure Settings

In AI Agent Settings:
- **RAG Mode**: ON
- **Index Path**: `./support-knowledge.json`
- **MCP Servers**: Add CRM server at `http://localhost:8011/sse`

#### 5. Use Support Mode

```
/support Почему не работают напоминания в Telegram?
```

### Other Applications

**Document Indexer (Desktop):**
```bash
./gradlew :indexer:run
```

**Document Indexing CLI:**
```bash
export GIGACHAT_CLIENT_ID="..." GIGACHAT_CLIENT_SECRET="..."
./gradlew :shared:runIndexing --args="index ./docs ./index.json md txt pdf"
./gradlew :shared:runIndexing --args="search ./index.json 'query' 5"
./gradlew :shared:runIndexing --args="stats ./index.json"
```

## Available Commands

| Command | Description |
|---------|-------------|
| `/help [topic]` | Get help about the project |
| `/task status` | Project dashboard with metrics |
| `/task priorities` | AI priority analysis |
| `/task create <desc>` | Create task with AI suggestions |
| `/task list [filters]` | List tasks with optional filters |
| `/task <task_id>` | View task details |
| `/support <question>` | User support assistant with RAG + CRM |
| `/review-pr [number]` | Code review for PR or local changes |

## Available MCP Servers

Add them in app Settings → MCP Servers:

| Server | URL | Description |
|--------|-----|-------------|
| **Git** | `http://localhost:8010/sse` | Git repository operations (Day 21) |
| **CRM** | `http://localhost:8011/sse` | User data & ticket management (Day 23) |
| **PM** | `http://localhost:8012/sse` | Project task management (Day 24) |

### CRM MCP Server Configuration

**Environment Variables:**
```bash
export GIGACHAT_CLIENT_ID="..."          # Required for smart search
export GIGACHAT_CLIENT_SECRET="..."      # Required for smart search
export CRM_USE_LLM_SEARCH=true           # Enable LLM query expansion (default: true)
export CRM_DATA_DIR="./crm/data"         # Data directory (default: ./crm/data)
export CRM_HOST="0.0.0.0"                # Host (default: 0.0.0.0)
export CRM_PORT=8011                     # Port (default: 8011)
```

**Data Files:**
- `crm/data/users.json` - User database
- `crm/data/tickets.json` - Ticket database

### PM MCP Server Configuration

**Environment Variables:**
```bash
export GIGACHAT_CLIENT_ID="..."          # Required for AI priority analysis
export GIGACHAT_CLIENT_SECRET="..."      # Required for AI priority analysis
export PM_DATA_DIR="./pm/data"           # Data directory (default: ./pm/data)
export PM_HOST="0.0.0.0"                 # Host (default: 0.0.0.0)
export PM_PORT=8012                      # Port (default: 8012)
```

**Data Files:**
- `pm/data/tasks.json` - Task database
- `pm/data/projects.json` - Project database

## Documentation Files

**Main:**
- **README.md** (this file) - Complete setup and usage guide
- **CLAUDE.md** - Technical documentation for Claude Code integration

**Day 24 - Team Assistant:**
- [day-24-docs/DAY_24_README.md](day-24-docs/DAY_24_README.md) - Day 24 overview
- [day-24-docs/SETUP_GUIDE.md](day-24-docs/SETUP_GUIDE.md) - Setup instructions
- [day-24-docs/TEST_SCENARIOS.md](day-24-docs/TEST_SCENARIOS.md) - Test scenarios
- [mcp-servers/pm/README.md](mcp-servers/pm/README.md) - PM MCP Server documentation

**Day 23 - User Support Assistant:**
- [support-docs/DAY_23_README.md](support-docs/DAY_23_README.md) - Day 23 overview
- [support-docs/SETUP_GUIDE.md](support-docs/SETUP_GUIDE.md) - Setup instructions
- [support-docs/TEST_SCENARIOS.md](support-docs/TEST_SCENARIOS.md) - Test scenarios
- [mcp-servers/crm/README.md](mcp-servers/crm/README.md) - CRM MCP Server documentation
- [mcp-servers/SMART_SEARCH.md](mcp-servers/SMART_SEARCH.md) - Smart search documentation

**Git MCP Server (Day 21):**
- [mcp-servers/README.md](mcp-servers/README.md) - MCP servers overview
- [mcp-servers/QUICKSTART.md](mcp-servers/QUICKSTART.md) - 5-minute quick start guide
- [mcp-servers/INTEGRATION.md](mcp-servers/INTEGRATION.md) - AI Agent integration guide
- [mcp-servers/git/README.md](mcp-servers/git/README.md) - Git server full documentation

**AI Agent Module:**
- [ai-agent/README.md](ai-agent/README.md) - AI Agent architecture and usage
- [ai-agent/CITATIONS_GUIDE.md](ai-agent/CITATIONS_GUIDE.md) - Using source citations in RAG context
- [ai-agent/LOCAL_TOOLS.md](ai-agent/LOCAL_TOOLS.md) - Local tools documentation

**Indexing & RAG:**
- [docs/ARCHITECTURE_RU.md](docs/ARCHITECTURE_RU.md) - Indexing architecture documentation (Russian)
- [docs/OLLAMA.md](docs/OLLAMA.md) - Ollama setup guide for local embeddings

## Troubleshooting

### MCP Server Issues

**PM server not responding:**
- Ensure port 8012 is not in use: `lsof -i :8012`
- Check that `GIGACHAT_CLIENT_ID` and `GIGACHAT_CLIENT_SECRET` are set for AI analysis
- Check server logs for errors

**CRM smart search not working:**
- Ensure `GIGACHAT_CLIENT_ID` and `GIGACHAT_CLIENT_SECRET` are set
- Check server logs for "Smart Search: ENABLED"
- Try disabling LLM search: `export CRM_USE_LLM_SEARCH=false`

**Server connection failed:**
- Check server logs for errors
- Verify MCP server URL is correctly added in AI Agent Settings
- Ensure the virtual environment is activated before running servers

### Gradle Issues

**Gradle sync fails:**
```bash
./gradlew clean
./gradlew --refresh-dependencies
```

**Credentials not found:**
- Ensure `local.properties` exists in the project root directory
- Verify the property names: `gigachat.clientId` and `gigachat.clientSecret`
- Run `./gradlew clean` and rebuild the project

**Database issues after schema change:**
```bash
./gradlew clean
# Delete old database if needed:
rm -f ~/.ai-chat/chat.db
```

### MCP Server Connection Issues

**Symptom:** Server shows "Error" status or tools aren't available

**Solution:**
- For HTTP servers: Check URL and verify server is running
- Ensure GigaChat model is selected (function calling not supported on HuggingFace)
- Check server logs for errors

## License

This project is created for educational purposes.

## Contact

For issues or questions, please refer to the GigaChat API documentation:
- [GigaChat API Docs](https://developers.sber.ru/docs/ru/gigachat/api/overview)

## Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- UI powered by [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- AI by [GigaChat](https://developers.sber.ru/portal/products/gigachat)
- MCP protocol by [Model Context Protocol](https://modelcontextprotocol.io)

## Videos

- https://disk.yandex.ru/i/HXnTNSqMzrUdHg
