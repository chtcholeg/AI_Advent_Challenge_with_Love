# GigaChat Multiplatform Chat Application (Day 26 - Local LLM)

A cross-platform chat application built with Kotlin Compose Multiplatform that integrates with GigaChat AI and local LLMs via Ollama. The application runs on Android and Desktop (JVM).

## Updates in Day 26: Local LLM

This version adds **local LLM support via Ollama** — run large language models on your own machine, with no cloud API required.

### New in Day 26

**Local LLM via Ollama** - Full offline AI inference:
- **New Provider**: `Model.Api.OLLAMA` alongside existing GigaChat/HuggingFace
- **Qwen2.5 Models**: `OllamaQwen2_5_0_5B` (fast) and `OllamaQwen2_5_32B` (capable)
- **OllamaApi**: New API layer using OpenAI-compatible endpoint at `localhost:11434`
- **Tool Calling**: Function/tool call support with Ollama models
- **Status Indicator**: Real-time Ollama server status in Settings (green/red)
- **RAG Integration**: Ollama embeddings (`nomic-embed-text`) for document indexing
- **HTTP Client**: Extended timeouts (600s) and SSL bypass for local dev

### Quick Start

```bash
# 1. Install and start Ollama
brew install ollama  # macOS
ollama serve

# 2. Pull a model
ollama pull qwen2.5:0.5b   # lightweight
ollama pull qwen2.5:32b    # more capable

# 3. Run AI Agent
./gradlew :ai-agent:run

# 4. In Settings, select an Ollama model and use normally
```

### Ollama Architecture

```
User selects Ollama model in Settings
    ↓
AgentRepository checks model.api == OLLAMA
    ↓
OllamaApi.isAvailable() → health check at localhost:11434
    ↓
OllamaApiImpl POST /v1/chat/completions (OpenAI-compatible)
    ↓
Ollama server processes locally → response returned
    ↓
Tool calling / RAG / response modes work as usual
```

**Ollama Settings UI:**
- Green indicator: Ollama server is running
- Red indicator: Offline — shows `ollama serve` hint
- Model not loaded: shows `ollama pull <model>` hint

**Ollama Documentation:**
- [docs/OLLAMA.md](docs/OLLAMA.md) — Setup guide (English)
- [docs/OLLAMA_RU.md](docs/OLLAMA_RU.md) — Setup guide (Russian)

---

## Previous Updates

### Day 25: Real Task — JuriLytics + VPS Manager

Day 25 was a step from training exercises to real-world projects. Two independent applications were built:

**JuriLytics** — AI-powered legal document analyzer:
- **Multi-agent pipeline**: Classifier → parallel specialist agents (IP Rights, Finance, Obligations, Post-contract, Consumer Rights) → Verifier → Aggregator → Gap-checker
- **Smart classification**: Automatically selects relevant agents based on document type
- **CLI prototype** (`day-25-real-task/prototype/`): `.txt` support, parallel agents, Q&A mode
- **Full web app** (`day-25-real-task/web-app/`): `.txt`/`.pdf` upload, real-time SSE progress, GigaChat model selection, Q&A chat, analysis history, authentication with roles, rate limiting

**VPS Manager** — Web-based server configuration assistant:
- Web UI + FastAPI backend
- AI-guided VPS setup scenarios
- SSH connection management

See [day-25-real-task/README.md](day-25-real-task/README.md) for full documentation.

---

### Day 24: Team Assistant

Added **Team Assistant** - an intelligent project management assistant combining RAG, MCP servers (Git, CRM, PM), and AI-powered analysis.

**Key Features:**
- **PM MCP Server** (Port 8012): Task CRUD, filtering, dashboard metrics, AI priority analysis
- **Dashboard & Metrics**: Velocity, workload, completion tracking
- **Full Integration**: RAG (knowledge) + Git (code) + CRM (users) + PM (tasks)
- **Natural Interface**: `/task` command with intuitive subcommands

```bash
/task status              # Project dashboard
/task priorities          # AI priority analysis
/task create <desc>       # Create task with AI
/task list status=open    # Filter tasks
```

See [day-24-docs/DAY_24_README.md](day-24-docs/DAY_24_README.md) for full documentation.

**PM MCP Server tools:**
- `create_task`, `list_tasks`, `get_task`, `update_task`, `delete_task`
- `get_project_dashboard`, `get_team_workload`, `analyze_priorities`, `suggest_priority`

---

### Day 23: User Support Assistant

Added **User Support Assistant** - automated technical support with RAG + CRM integration.

**Key Features:**
- **RAG Integration**: Search solutions in FAQ and documentation
- **CRM MCP Server** (Port 8011): Access user data, tickets, and history
- **Smart Search**: LLM-powered query expansion with morphological analysis
- **Dual Source Display**: Shows both RAG sources (docs) and CRM sources (tickets)
- **Ticket Management**: Update status, add notes, escalate issues

```bash
/support Почему не работают напоминания в Telegram?
```

See [support-docs/DAY_23_README.md](support-docs/DAY_23_README.md) for full documentation.

**CRM MCP Server tools:**
- `get_user`, `get_user_tickets`, `search_tickets`, `update_ticket_status`, `create_ticket`

---

### Day 22: Code Review Assistant

Added `/review-pr` command with specialized bug detection:
- **Specialized Checklists**: Technology-specific bug detection (Kotlin Coroutines, Flow, MVI, Repository Pattern, Python Async, SQL, Config Security)
- **Critical Bug Detection**: Arithmetic errors, GlobalScope leaks, regression detection
- **Large PR Support**: Handles PRs >20000 lines with fallback method

```bash
/review-pr 5    # Review GitHub PR #5
/review-pr      # Review local uncommitted changes
```

---

### Day 21: Developer Assistant with Git MCP

Added **Git MCP Server** - a Python-based MCP server for AI-assisted Git operations.

**11 Git Tools:**
- Read-only: `git_status`, `git_log`, `git_diff`, `git_branch_list`, `git_show_commit`, `git_blame`
- Write: `git_add`, `git_commit`, `git_checkout`, `git_pull`, `git_push`

```bash
cd mcp-servers && ./START.sh  # Start all servers
# Add in AI Agent Settings: http://localhost:8010/sse
```

---

### Current Features Summary

- **Local LLM** (Day 26): Ollama integration with Qwen2.5 models, offline inference
- **Real-world apps** (Day 25): JuriLytics (legal doc AI) + VPS Manager
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
- **Ollama**: Local LLM inference + embeddings for document indexing
- **Apache PDFBox**: PDF text extraction (Desktop)

## Project Structure

```
day-26-local-llm/
├── ai-agent/                    # AI Agent app with full integration
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/ru/chtcholeg/agent/
│       │       ├── data/        # AgentRepository, McpRepository, RagRepository
│       │       ├── domain/      # AgentMessage, CommandHandler, ToolExecutor
│       │       │   └── service/ # TicketSourceParser, SpecializedChecklists
│       │       └── presentation/ # AgentScreen (MVI), Settings (Ollama status UI)
│       ├── androidMain/         # HttpClientFactory (SSL bypass, extended timeouts)
│       └── desktopMain/         # HttpClientFactory (SSL bypass, extended timeouts)
├── shared/                      # Shared services module
│   └── src/commonMain/          # OllamaApi, Model (with OLLAMA provider), EmbeddingService
├── indexer/                     # Document Indexer GUI
│   └── src/commonMain/          # OllamaEmbeddingService (nomic-embed-text, 768-dim)
├── mcp-servers/                 # Python MCP servers
│   ├── git/                     # Git MCP Server (Day 21)
│   ├── crm/                     # CRM MCP Server (Day 23)
│   ├── pm/                      # PM MCP Server (Day 24)
│   └── START.sh                 # Quick start for all MCP servers
├── day-25-real-task/            # Day 25: JuriLytics + VPS Manager
│   ├── prototype/               # CLI prototype for legal doc analysis
│   ├── web-app/                 # Full web application (FastAPI + Vanilla JS)
│   └── vps-manager/             # VPS configuration manager
├── day-24-docs/                 # Team Assistant documentation
├── support-docs/                # Support knowledge base (Day 23)
├── docs/                        # Additional documentation (incl. OLLAMA.md)
└── README.md                    # This file
```

## Prerequisites

- **JDK 17 or higher**: Required for Kotlin and Gradle
- **Python 3.8+**: Required for MCP servers and Day 25 apps
- **Android Studio**: For Android development and building
- **IntelliJ IDEA** (optional): Recommended for multiplatform development
- **Android SDK**: For Android builds (can be installed via Android Studio)
- **Ollama** (optional): Required for local LLM inference — [ollama.ai](https://ollama.ai)

## Getting GigaChat API Credentials

To use cloud AI features:

1. Visit [GigaChat Developer Portal](https://developers.sber.ru/portal/products/gigachat)
2. Sign up or log in to your account
3. Create a new application/project
4. Obtain your **Client ID** and **Client Secret**

> **Note:** For local LLM (Ollama), no API credentials are needed.

## Setup Instructions

### 1. Configure API Credentials

1. Copy the template file:
   ```bash
   cp local.properties.template local.properties
   ```

2. Edit `local.properties`:
   ```properties
   gigachat.clientId=your_actual_client_id
   gigachat.clientSecret=your_actual_client_secret
   ```

   The `local.properties` file is excluded from version control.

### 2. Set Up Ollama (for Local LLM)

```bash
# macOS
brew install ollama
ollama serve

# Pull models
ollama pull qwen2.5:0.5b    # lightweight (recommended to start)
ollama pull qwen2.5:32b     # more capable (requires ~20GB RAM)

# For document indexing embeddings
ollama pull nomic-embed-text
```

### 3. Set Up MCP Servers (optional)

```bash
cd mcp-servers
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt

export GIGACHAT_CLIENT_ID="your_client_id"
export GIGACHAT_CLIENT_SECRET="your_client_secret"
export CRM_USE_LLM_SEARCH=true
```

### 4. Build the Project

```bash
./gradlew build
```

## Running the Application

### Local LLM Workflow

```bash
# 1. Start Ollama
ollama serve

# 2. Run AI Agent
./gradlew :ai-agent:run

# 3. In Settings: select OllamaQwen2_5_0_5B or OllamaQwen2_5_32B
# Green indicator = server is running, red = offline
```

### Team Assistant Workflow

```bash
# 1. Start all MCP servers
export GIGACHAT_CLIENT_ID="..." GIGACHAT_CLIENT_SECRET="..."
cd mcp-servers && ./START.sh

# 2. Index project docs (optional, for RAG)
./gradlew :shared:runIndexing --args="index ./docs ./project-knowledge.json md txt"

# 3. Run AI Agent
./gradlew :ai-agent:run

# 4. In Settings: add PM (8012), CRM (8011), Git (8010) servers
/task status              # Project dashboard
/task priorities          # AI priority analysis
/task create <desc>       # Create task with AI-suggested priority
/task list status=open    # Filter tasks
```

### Support Assistant Workflow

```bash
# 1. Start MCP servers
cd mcp-servers && ./START.sh

# 2. Index FAQ docs
./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-knowledge.json md"

# 3. Run AI Agent
./gradlew :ai-agent:run

# 4. In Settings: RAG Mode ON, Index Path ./support-knowledge.json, CRM server 8011
/support Почему не работают напоминания в Telegram?
```

### JuriLytics (Day 25)

```bash
# CLI prototype
cd day-25-real-task/prototype
pip install -r requirements.txt
# Create .env with GIGACHAT_AUTHORIZATION_KEY=...
python analyze.py sample_contract.txt

# Full web app
cd day-25-real-task/web-app/backend
pip install -r requirements.txt
# Create .env with GIGACHAT_AUTHORIZATION_KEY, ADMIN_USERNAME, ADMIN_PASSWORD
uvicorn main:app --port 8001
# Open http://localhost:8001
```

### Other Applications

```bash
# Desktop Chat App
./gradlew :chat:run

# Document Indexer GUI (requires Ollama)
./gradlew :indexer:run

# Document Indexing CLI
export GIGACHAT_CLIENT_ID="..." GIGACHAT_CLIENT_SECRET="..."
./gradlew :shared:runIndexing --args="index ./docs ./index.json md txt pdf"
./gradlew :shared:runIndexing --args="search ./index.json 'query' 5"
./gradlew :shared:runIndexing --args="stats ./index.json"
```

## Available Commands (AI Agent)

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

| Server | URL | Description |
|--------|-----|-------------|
| **Git** | `http://localhost:8010/sse` | Git repository operations (Day 21) |
| **CRM** | `http://localhost:8011/sse` | User data & ticket management (Day 23) |
| **PM** | `http://localhost:8012/sse` | Project task management (Day 24) |

### MCP Server Configuration

**CRM Server (Port 8011):**
```bash
export GIGACHAT_CLIENT_ID="..."
export GIGACHAT_CLIENT_SECRET="..."
export CRM_USE_LLM_SEARCH=true     # LLM query expansion (default: true)
export CRM_DATA_DIR="./crm/data"
export CRM_PORT=8011
```

**PM Server (Port 8012):**
```bash
export GIGACHAT_CLIENT_ID="..."
export GIGACHAT_CLIENT_SECRET="..."
export PM_DATA_DIR="./pm/data"
export PM_PORT=8012
```

## Documentation Files

**Day 26 - Local LLM:**
- [docs/OLLAMA.md](docs/OLLAMA.md) — Ollama setup guide (English)
- [docs/OLLAMA_RU.md](docs/OLLAMA_RU.md) — Ollama setup guide (Russian)

**Day 25 - Real Task:**
- [day-25-real-task/README.md](day-25-real-task/README.md) — JuriLytics + VPS Manager overview

**Day 24 - Team Assistant:**
- [day-24-docs/DAY_24_README.md](day-24-docs/DAY_24_README.md) — Day 24 overview
- [day-24-docs/SETUP_GUIDE.md](day-24-docs/SETUP_GUIDE.md) — Setup instructions
- [mcp-servers/pm/README.md](mcp-servers/pm/README.md) — PM MCP Server documentation

**Day 23 - User Support Assistant:**
- [support-docs/DAY_23_README.md](support-docs/DAY_23_README.md) — Day 23 overview
- [mcp-servers/crm/README.md](mcp-servers/crm/README.md) — CRM MCP Server documentation
- [mcp-servers/SMART_SEARCH.md](mcp-servers/SMART_SEARCH.md) — Smart search documentation

**AI Agent Module:**
- [ai-agent/README.md](ai-agent/README.md) — AI Agent architecture and usage
- [ai-agent/CITATIONS_GUIDE.md](ai-agent/CITATIONS_GUIDE.md) — Source citations in RAG context
- [ai-agent/LOCAL_TOOLS.md](ai-agent/LOCAL_TOOLS.md) — Local tools documentation

**Indexing & RAG:**
- [docs/ARCHITECTURE_RU.md](docs/ARCHITECTURE_RU.md) — Indexing architecture (Russian)

**Git MCP Server:**
- [mcp-servers/QUICKSTART.md](mcp-servers/QUICKSTART.md) — 5-minute quick start
- [mcp-servers/git/README.md](mcp-servers/git/README.md) — Full documentation

## Troubleshooting

### Ollama Issues

**Ollama server not connecting:**
```bash
ollama serve             # Start the server
lsof -i :11434           # Check port usage
```

**Model not responding:**
```bash
ollama list              # Check installed models
ollama pull qwen2.5:0.5b # Re-pull if missing
```

**Slow inference:**
- Use `qwen2.5:0.5b` for faster responses
- Ensure sufficient RAM (0.5B: ~1GB, 32B: ~20GB)

### MCP Server Issues

**PM/CRM server not responding:**
```bash
lsof -i :8012            # Check port usage
```
Ensure `GIGACHAT_CLIENT_ID` and `GIGACHAT_CLIENT_SECRET` are set.

**CRM smart search not working:**
- Check logs for "Smart Search: ENABLED"
- Disable LLM search: `export CRM_USE_LLM_SEARCH=false`

### Gradle Issues

```bash
./gradlew clean
./gradlew --refresh-dependencies
rm -f ~/.ai-chat/chat.db  # If DB schema changed
```

**Credentials not found:**
- Verify `local.properties` exists with `gigachat.clientId` and `gigachat.clientSecret`
- Run `./gradlew clean build`

## License

This project is created for educational purposes.

## Contact

For issues, refer to the GigaChat API documentation:
- [GigaChat API Docs](https://developers.sber.ru/docs/ru/gigachat/api/overview)

## Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- UI powered by [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- AI by [GigaChat](https://developers.sber.ru/portal/products/gigachat)
- Local inference by [Ollama](https://ollama.ai)
- MCP protocol by [Model Context Protocol](https://modelcontextprotocol.io)

## Videos

- https://disk.yandex.ru/i/qpP_j6ELooImGg
