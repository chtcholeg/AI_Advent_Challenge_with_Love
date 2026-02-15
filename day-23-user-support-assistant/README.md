# GigaChat Multiplatform Chat Application (Day 23 - User Support Assistant)

A cross-platform chat application built with Kotlin Compose Multiplatform that integrates with GigaChat AI. The application runs on Android and Desktop (JVM).

## Updates in Day 23: User Support Assistant

This version adds a **User Support Assistant** - a specialized AI Agent mode that combines RAG (knowledge base search), CRM integration (user data & tickets), and empathetic communication to provide automated technical support.

### New in Day 23

**User Support Assistant** - AI-powered technical support with comprehensive tooling:
- 🤖 **RAG Integration**: Search solutions in FAQ and documentation
- 📊 **CRM MCP Server**: Access user data, tickets, and history
- 🧠 **Smart Search**: LLM-powered query expansion with morphological analysis
- 💬 **Empathetic Communication**: Specialized support prompt for user-friendly responses
- 🔍 **Dual Source Display**: Shows both RAG sources (docs) and CRM sources (tickets)
- 📝 **Ticket Management**: Update ticket status, add notes, escalate issues

See [support-docs/DAY_23_README.md](support-docs/DAY_23_README.md) for full documentation.

### Quick Start

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

### Key Features

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

**Dual Source Display:**
```
┌─ AI Response ─────────────────────────────────────────┐
│ Согласно документации [Источник 1], для работы        │
│ напоминаний необходимо настроить Telegram Bot Token.   │
│                                                         │
│ **Похожие проблемы:**                                  │
│ - ticket_007 [Источник 2]: решение в процессе         │
│                                                         │
│ ┌─ Источники (2) ▼ ────────────────────────────────┐  │
│ │ [1] LOCAL_TOOLS.md                                │  │
│ │   "Telegram reminders require send_telegram..."   │  │
│ │   Фрагмент 3/8 · Релевантность: 94%              │  │
│ │                                                    │  │
│ │ [2] ticket_007                                     │  │
│ │   "Не приходят напоминания в Telegram [...]"     │  │
│ │   Приоритет: medium | Категория: Integration     │  │
│ │   Релевантность: 85%                              │  │
│ └────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
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

### Example Session

```
User: /support Почему не работают напоминания в Telegram?

AI Agent:
1. Calls get_user("user_123")
2. Calls search_tickets("напоминания Telegram")
   → LLM expands: ["напоминания", "reminders", "Telegram", "уведомления", "notifications"]
3. RAG searches FAQ documents
4. Responds with:
   - Empathetic acknowledgment
   - Solution from [Источник 1] (docs)
   - Similar ticket [Источник 2] (CRM)
   - Step-by-step fix
5. Calls update_ticket_status(ticket_id, "resolved")
```

---

## Previous Updates

### Day 22: Code Review Assistant

Added **Code Review** capabilities with specialized bug detection:

#### Features

- **`/review-pr` command**: Automated code review for GitHub Pull Requests
- **Specialized Checklists**: Technology-specific bug detection (Kotlin Coroutines, Flow, MVI, Repository Pattern, Python Async, SQL, Config Security)
- **Critical Bug Detection**: Arithmetic errors, GlobalScope leaks, regression detection
- **Large PR Support**: Handles PRs >20000 lines with fallback method

**Example Usage:**
```
/review-pr 5          # Review GitHub PR #5
/review-pr            # Review local uncommitted changes
```

**Documentation:**
- [INTEGRATION_COMPLETED.md](INTEGRATION_COMPLETED.md) - Integration summary
- [SOLUTION_SUMMARY.md](SOLUTION_SUMMARY.md) - Bug detection improvements
- [FIX_LARGE_PR_REVIEW.md](FIX_LARGE_PR_REVIEW.md) - Large PR handling

### Day 21: Developer Assistant with Git MCP

Added **Git MCP Server** - a Python-based MCP server that enables AI Agent to work with Git repositories:

#### Features

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

**Example Usage:**
```
User: What's the git status?
AI: [Calls git_status] → Shows current branch, staged/unstaged files

User: Show last 5 commits
AI: [Calls git_log] → Displays commit history

User: Create branch feature/new-tool
AI: [Calls git_checkout with create=true] → Branch created and checked out
```

**Documentation:**
- [Quick Start Guide](mcp-servers/QUICKSTART.md) - 5-minute setup
- [Integration Guide](mcp-servers/INTEGRATION.md) - AI Agent integration
- [Git Server README](mcp-servers/git/README.md) - Full documentation

### Current Features Summary

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
day-23-user-support-assistant/
├── chat/                        # Main GigaChat chat application
├── ai-agent/                    # AI Agent app with RAG + CRM support
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/ru/chtcholeg/agent/
│       │       ├── data/        # AgentRepository, McpRepository, RagRepository
│       │       ├── domain/      # AgentMessage, CommandHandler, ToolExecutor
│       │       │   └── service/ # TicketSourceParser (NEW in Day 23)
│       │       └── presentation/ # AgentScreen (MVI), Settings
├── shared/                      # Shared services module
├── indexer/                     # Document Indexer GUI
├── mcp-servers/                 # Python MCP servers
│   ├── git/                     # Git MCP Server (Day 21)
│   ├── crm/                     # CRM MCP Server (Day 23) - NEW
│   │   ├── main.py             # Server implementation
│   │   ├── config.py           # Configuration
│   │   ├── search_service.py  # Smart search with LLM (NEW)
│   │   └── data/
│   │       ├── users.json      # User database
│   │       └── tickets.json    # Ticket database
│   └── START.sh                # Quick start for all MCP servers
├── support-docs/               # Support knowledge base (NEW in Day 23)
│   ├── faq/                    # FAQ documents for RAG
│   │   ├── authentication.md
│   │   ├── installation.md
│   │   ├── mcp-servers.md
│   │   ├── features.md
│   │   └── errors.md
│   ├── config/
│   │   └── support-assistant-prompt.md  # System prompt
│   ├── DAY_23_README.md       # Day 23 documentation
│   ├── SETUP_GUIDE.md         # Setup instructions
│   └── TEST_SCENARIOS.md      # Test scenarios
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
cd /Users/shchepilov/AndroidStudioProjects/AI_Advent_Challenge_with_Love/day-23-user-support-assistant
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
pip install -r crm/requirements.txt
pip install -r git/requirements.txt

# Configure environment (optional - for smart search)
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

### Support Assistant Workflow

#### 1. Start MCP Servers

```bash
cd mcp-servers
./START.sh  # Starts Git (8010) and CRM (8011) servers
```

You should see:
```
============================================================
CRM MCP Server Starting
============================================================
Data directory: /path/to/mcp-servers/crm/data
Host: 0.0.0.0
Port: 8011
Authentication: DISABLED
Smart Search: ENABLED (LLM query expansion via GigaChat)
============================================================
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

**Chat App (Android):**
```bash
./gradlew :chat:installDebug
adb shell am start -n ru.chtcholeg.app/.MainActivity
```

**Chat App (Desktop):**
```bash
./gradlew :chat:run
```

**Document Indexer (Desktop):**
```bash
./gradlew :indexer:run
```

**Document Indexing CLI:**
```bash
export GIGACHAT_CLIENT_ID="..." GIGACHAT_CLIENT_SECRET="..."
./gradlew :shared:runIndexing --args="index ./docs ./index.db md txt pdf"
./gradlew :shared:runIndexing --args="search ./index.db 'query' 5"
./gradlew :shared:runIndexing --args="stats ./index.db"
```

## Using Support Assistant

### Example Scenarios

**Scenario 1: Authentication Issue**
```
User: /support Не могу войти в систему

AI Agent:
1. Identifies user via CRM
2. Searches tickets for similar issues
3. Searches FAQ for authentication docs
4. Responds with:
   - [Источник 1] docs/faq/authentication.md
   - [Источник 2] ticket_003 (similar case)
   - Step-by-step solution
5. Updates ticket status
```

**Scenario 2: Feature Question**
```
User: /support Как настроить MCP серверы?

AI Agent:
1. Searches FAQ (RAG)
2. Finds mcp-servers.md
3. Responds with setup instructions
4. References [Источник 1] with clickable link
```

### Available CRM Tools

| Tool | Description | Example |
|------|-------------|---------|
| `get_user` | Retrieve user profile | `get_user("user_123")` |
| `get_user_tickets` | Get user's tickets | `get_user_tickets("user_123")` |
| `search_tickets` | Smart search with LLM | `search_tickets("авторизация")` |
| `update_ticket_status` | Update status | `update_ticket_status("ticket_007", "resolved")` |
| `create_ticket` | Create new ticket | `create_ticket("user_123", "Bug report", ...)` |

### Smart Search Features

**Without LLM (basic word search):**
```
Query: "авторизация не работает"
Searches: ['авторизация', 'работает']
Found: 1 ticket
```

**With LLM (smart expansion):**
```
Query: "авторизация не работает"
LLM expands to:
- авторизация, authentication, логин, вход, login
- auth, креденшелы, credentials, токен, token
- Invalid credentials, authentication failed
Searches: 15 terms
Found: 5 tickets (sorted by relevance)
```

## Using MCP Servers

### Quick Start (All Servers)

```bash
# Navigate to MCP servers directory
cd mcp-servers

# Create and activate virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install all servers
pip install -e ".[all]"

# Start specific servers
./START.sh  # Git (8010) + CRM (8011)
```

### Available MCP Servers

Add them in app Settings → MCP Servers:
- **Git**: `http://localhost:8010/sse` (Day 21)
- **CRM**: `http://localhost:8011/sse` (Day 23) - NEW
- GitHub: `http://localhost:8000/sse`
- Telegram: `http://localhost:8001/sse`
- Weather: `http://localhost:8002/sse`
- TimeService: `http://localhost:8003/sse`
- Currency: `http://localhost:8004/sse`
- FileOps: `http://localhost:8005/sse`
- Docker: `http://localhost:8006/sse` (Day 15)

### CRM MCP Server Configuration

**Environment Variables:**
```bash
export GIGACHAT_CLIENT_ID="..."          # Required for smart search
export GIGACHAT_CLIENT_SECRET="..."      # Required for smart search
export CRM_USE_LLM_SEARCH=true           # Enable LLM query expansion (default: true)
export CRM_DATA_DIR="./crm/data"         # Data directory (default: ./crm/data)
export CRM_HOST="0.0.0.0"                # Host (default: 0.0.0.0)
export CRM_PORT=8011                     # Port (default: 8011)
export MCP_API_KEY=""                    # API key (default: empty = disabled)
```

**Data Files:**
- `crm/data/users.json` - User database (id, name, email, tier, created_at)
- `crm/data/tickets.json` - Ticket database (id, user_id, subject, description, status, priority, category, created_at, updated_at)

## Documentation Files

This project includes comprehensive documentation:

**Main:**
- **README.md** (this file) - Complete setup and usage guide
- **CLAUDE.md** - Technical documentation for Claude Code integration

**Day 23 - User Support Assistant:**
- **support-docs/DAY_23_README.md** - Day 23 overview
- **DAY_23_COMPLETED.md** - Implementation summary
- **SUPPORT_SOURCES_GUIDE.md** - Dual source display guide
- **support-docs/SETUP_GUIDE.md** - Setup instructions
- **support-docs/TEST_SCENARIOS.md** - Test scenarios
- **mcp-servers/crm/README.md** - CRM MCP Server documentation
- **mcp-servers/SMART_SEARCH.md** - Smart search documentation

**Day 22 - Code Review:**
- **INTEGRATION_COMPLETED.md** - Integration summary
- **SOLUTION_SUMMARY.md** - Bug detection improvements
- **FIX_LARGE_PR_REVIEW.md** - Large PR handling
- **QUICK_TEST_GUIDE.md** - Quick test guide

**Git MCP Server (Day 21):**
- **mcp-servers/README.md** - MCP servers overview
- **mcp-servers/QUICKSTART.md** - 5-minute quick start guide
- **mcp-servers/INTEGRATION.md** - AI Agent integration guide
- **mcp-servers/git/README.md** - Git server full documentation

**AI Agent Module:**
- **ai-agent/README.md** - AI Agent architecture and usage
- **ai-agent/CITATIONS_GUIDE.md** - Using source citations in RAG context
- **ai-agent/LOCAL_TOOLS.md** - Local tools documentation

**Indexing & RAG:**
- **docs/ARCHITECTURE_RU.md** - Indexing architecture documentation (Russian)
- **docs/OLLAMA.md** - Ollama setup guide for local embeddings
- **docs/OLLAMA_RU.md** - Ollama setup guide (Russian)

## Troubleshooting

### CRM Server Issues

**Smart search not working:**
- Ensure `GIGACHAT_CLIENT_ID` and `GIGACHAT_CLIENT_SECRET` are set
- Check server logs for "Smart Search: ENABLED"
- Verify credentials are valid

**Tickets not found:**
- Check `crm/data/tickets.json` exists and is valid JSON
- Verify ticket IDs match those in database
- Try with and without LLM search: `export CRM_USE_LLM_SEARCH=false`

**Server connection failed:**
- Ensure port 8011 is not in use: `lsof -i :8011`
- Check server logs for errors
- Verify MCP server is added in AI Agent Settings

### Gradle Sync Fails

```bash
./gradlew clean
./gradlew --refresh-dependencies
```

### Credentials Not Found or Empty

**Symptom:** Empty credentials or build errors related to missing configuration

**Solution:**
- Ensure `local.properties` exists in the project root directory
- Verify the property names are correct: `gigachat.clientId` and `gigachat.clientSecret`
- Check that the values are not empty in `local.properties`
- Run `./gradlew clean` and rebuild the project

### MCP Server Connection Issues

**Symptom:** Server shows "Error" status or tools aren't available

**Solution:**
- For local servers: Verify command and arguments are correct
- For HTTP servers: Check URL and authentication token
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

- https://disk.yandex.ru/i/a9ZcynEo0h7baA
