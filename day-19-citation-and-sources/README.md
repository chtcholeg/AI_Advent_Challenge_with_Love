# GigaChat Multiplatform Chat Application (Day 19 - Citation and Sources)

A cross-platform chat application built with Kotlin Compose Multiplatform that integrates with GigaChat AI. The application runs on Android and Desktop (JVM).

## Updates in Day 19: Citation and Sources (Clickable Web URLs)

This version introduces **Clickable Web Sources** - the ability to click on source references in AI Agent to open web pages directly in your browser. When you click on `[Источник N]` (Source N) in the RAG context, if the source is a web URL, it automatically opens in your default browser.

### New Features

#### Clickable Source References

- **Automatic URL Detection**: FileOpener automatically detects URLs (http:// or https://) and opens them in browser
- **Cross-Platform Support**: Works on both Desktop (Desktop.browse()) and Android (Intent.ACTION_VIEW)
- **Mixed Sources**: Support for both local files and web URLs in the same index
  - Local files → open in default editor
  - Web URLs → open in browser
- **Visual Indication**: Sources are displayed as blue, underlined, bold text (like standard hyperlinks)

#### New Indexing Commands

```bash
# Index single URL
./gradlew :shared:runIndexing --args="indexUrl 'https://example.com' ./index.db"

# Index multiple URLs
./gradlew :shared:runIndexing --args="indexUrls 'https://a.com' 'https://b.com' ./index.db"
```

#### Demo Script

**`demo-clickable-sources.sh`** - Quick demonstration script:
- Indexes Kotlin Coroutines documentation page
- Shows index statistics
- Performs test search
- Provides usage instructions

### Quick Start

```bash
# 1. Run demo (indexes Kotlin docs)
./demo-clickable-sources.sh

# 2. Launch AI Agent
./gradlew :ai-agent:run

# 3. Configure RAG in Settings:
#    - RAG Mode: ON
#    - Index Path: ./demo-web-index.db

# 4. Ask: "What is a coroutine scope in Kotlin?"
# 5. Click on [Источник 1] to open kotlinlang.org in browser
```

### Architecture

```
User clicks [Источник 1]
         ↓
MessageItem.kt: onSourceClick(source.filePath)
         ↓
AgentScreen.kt: FileOpener.openFile(path)
         ↓
FileOpener checks path prefix
         ↓
    ┌────────┴────────┐
    ↓                 ↓
http(s)://          file path
    ↓                 ↓
Desktop.browse()    Desktop.open()
or Intent           or FileProvider
```

### Implementation Files

| Component | Path |
|-----------|------|
| FileOpener (expect) | `ai-agent/src/commonMain/.../util/FileOpener.kt` |
| FileOpener (Desktop) | `ai-agent/src/desktopMain/.../util/FileOpener.desktop.kt` |
| FileOpener (Android) | `ai-agent/src/androidMain/.../util/FileOpener.android.kt` |
| Demo Script | `demo-clickable-sources.sh` |

### Documentation

New documentation files for Day 19:
- **CLICKABLE_SOURCES.md** - Detailed guide on using clickable sources
- **DAY_19_CHANGES.md** - Technical details of implementation changes
- **QUICKSTART_CLICKABLE_SOURCES.md** - 3-step quick start guide

### Use Cases

1. **Technical Documentation**: Index API docs and click sources to view online
2. **Blog Posts & Articles**: Index articles and jump to original sources
3. **Mixed Research**: Combine local files and web pages in single index
4. **Educational Content**: Index tutorial pages and navigate to them instantly

### Benefits

- **Seamless Navigation**: Click to view full context in browser
- **Source Verification**: Easy access to original sources for fact-checking
- **Enhanced Workflow**: No need to manually search for referenced pages
- **Backward Compatible**: Existing file-based indexes continue to work

---

## Previous Updates

### Day 18: RAG Reranking

Introduced **RAG Reranking** - a two-stage retrieval system that combines broad vector search with strict relevance filtering. When reranking is enabled, the agent first retrieves a large set of candidates, then applies similarity thresholds, score-gap detection, and final top-K filtering to keep only the most relevant document chunks.

### New Features

#### Two-Stage Retrieval Pipeline

- **Stage 1 (Broad Search)**: Retrieve a large set of candidates from the vector store with a soft threshold (default: top-K=10, threshold=0.3)
- **Stage 2a (Similarity Threshold)**: Filter out chunks below a strict similarity threshold (default: 0.5)
- **Stage 2b (Score-Gap Detection)**: Detect natural boundaries where similarity drops sharply between consecutive results (default gap: 0.15)
- **Stage 2c (Final Top-K)**: Limit the output to the top N most relevant chunks (default: 3)

#### Reranker Settings UI

When RAG is enabled, a new **Reranker / Relevance Filter** section appears in Settings:

| Parameter | Control | Default | Range |
|-----------|---------|---------|-------|
| Enable Reranker | Toggle | OFF | ON/OFF |
| Stage 1 -- Candidates (Top-K) | Slider | 10 | 3--30 |
| Stage 2 -- Similarity Threshold | Slider | 0.50 | 0.10--0.90 |
| Score Gap Threshold | Slider | 0.15 | 0.02--0.50 |
| Final Results (Top-K) | Slider | 3 | 1--10 |

#### Reranker Report

When reranking is active, each RAG query displays a detailed report showing Stage 1 candidates with scores, filtering statistics (removed by threshold, removed by gap), and final Stage 2 results.

### Architecture

```
User Query
    |
    v
Generate Embedding (EmbeddingService)
    |
    v
Stage 1: Vector Search (broad top-K retrieval, initialTopK=10, threshold=0.3)
    |
    v
Stage 2: Reranking
  |-- 2a: Similarity Threshold Filter (rerankerThreshold=0.5)
  |-- 2b: Score Gap Detection (scoreGapThreshold=0.15)
  |-- 2c: Final Top-K (ragFinalTopK=3)
    |
    v
Format Context & Inject into Prompt -> GigaChat/HuggingFace API
```

### Quick Start

```bash
# 1. Index documents (CLI via shared module)
export GIGACHAT_CLIENT_ID="..." GIGACHAT_CLIENT_SECRET="..."
./gradlew :shared:runIndexing --args="index ./docs ./index.db md txt pdf"

# 2. Or use the Indexer GUI with Ollama
./gradlew :indexer:run

# 3. Run the AI Agent with RAG + Reranking
./gradlew :ai-agent:run
# Then: Settings -> RAG Mode -> ON, set index path, enable Reranker
```

### Recommended Configurations

| Scenario | Initial Top-K | Threshold | Score Gap | Final Top-K |
|----------|:---:|:---:|:---:|:---:|
| Precise answers (narrow topic) | 10 | 0.60 | 0.15 | 2 |
| Balanced mode | 10 | 0.50 | 0.15 | 3 |
| Broad context (overview questions) | 20 | 0.35 | 0.10 | 5 |
| Small index (< 50 chunks) | 5 | 0.40 | 0.20 | 3 |

---

## Previous Updates

### Day 17: RAG Query (Retrieval-Augmented Generation)

Introduced **RAG Query** - the AI Agent can use indexed documents as context for answering questions. When RAG mode is enabled, the agent automatically searches the vector index for relevant document chunks and injects them into the conversation context.

- **RAG Mode Toggle**: Enable/disable RAG in agent settings with persistent configuration
- **Automatic Context Retrieval**: When RAG is ON, user queries are embedded and matched against the document index using cosine similarity (top-K=5, threshold=0.3)
- **Context Injection**: Retrieved document chunks are formatted and injected into the AI prompt as additional context
- **RAG_CONTEXT Message Type**: Retrieved chunks are displayed in the chat as a summary with similarity scores
- **Index Path Configuration**: Point the agent to any JSON index file created by the indexer
- **Dimension Mismatch Handling**: Graceful error handling when query embeddings don't match index dimensions

#### Document Indexer Application

**`indexer/`** - Standalone Compose Multiplatform desktop application for indexing documents using local Ollama embeddings:

- **File & Directory Indexing**: Support for MD, TXT, PDF files with recursive directory scanning
- **URL Indexing**: Index web pages by URL
- **Ollama Integration**: Uses local `nomic-embed-text` model (768-dimension vectors)
- **SQLite Storage**: Persistent index with `indexed_files` and `indexed_chunks` tables
- **Checksum Deduplication**: MD5-based skip of unchanged files on re-indexing
- **Progress Tracking**: Real-time indexing progress via Flow events
- **Semantic Search**: Cosine similarity search with ranked results
- **Statistics**: View total chunks, files, and index size

### Day 16: Document Indexing with Semantic Search

Introduced the **Document Indexing System** in the `shared/` module - a complete pipeline for loading documents, generating embeddings, and performing semantic search:

- **DocumentLoader** - Load documents from files and directories (MD, TXT, PDF)
- **TextChunker** - Split documents into chunks (3 strategies: characters, tokens, sentences)
- **EmbeddingService** - Generate embeddings via GigaChat API with batch processing
- **VectorStore** - In-memory vector store with JSON persistence and cosine similarity search
- **VectorStoreSqliteImpl** - SQLite-based vector storage alternative
- **DocumentIndexer** - Orchestrate full indexing pipeline with progress reporting
- **WebPageLoader** - Load and index web page content
- **PdfParser** - Extract text from PDFs (expect/actual with Apache PDFBox for Desktop)

### Day 15: Docker Environment Integration

This version introduces **Docker MCP Server** - connecting AI agents to real-world Docker infrastructure. The AI can now autonomously manage Docker containers, images, and orchestrate multi-service environments through Docker Compose.

### New Features

#### Docker MCP Server (NEW) 🐳

The **7th MCP server** (port 8006) provides comprehensive Docker management capabilities:

**Container Management (7 tools)**:
- `docker_ps` - List running containers with detailed status
- `docker_run` - Start containers with ports, volumes, environment variables
- `docker_stop` - Stop running containers gracefully
- `docker_start` - Start stopped containers
- `docker_restart` - Restart containers
- `docker_logs` - View container logs (configurable tail)
- `docker_exec` - Execute commands inside running containers

**Image Management (3 tools)**:
- `docker_images` - List available Docker images
- `docker_pull` - Pull images from Docker Hub/registries
- `docker_build` - Build images from Dockerfile

**Docker Compose (3 tools)**:
- `docker_compose_up` - Start multi-service applications
- `docker_compose_down` - Stop and remove services
- `docker_compose_ps` - List service status

### Key Capabilities

#### 1. Development Environment Automation
AI can set up complete development stacks:
- "Start a PostgreSQL database on port 5432"
- "Set up Redis for caching"
- "Start my docker-compose.yml services"

#### 2. Container Lifecycle Management
Full container control through conversation:
- "Show me all running containers"
- "Stop the nginx container"
- "Restart the API service"

#### 3. Debugging & Troubleshooting
AI-assisted container debugging:
- "Show me the last 100 logs from postgres"
- "Why is my container exiting?"
- "Execute ps aux in the web container"

#### 4. Image Operations
Build and manage Docker images:
- "Build my application with tag myapp:1.0"
- "Pull the latest nginx image"
- "List all available images"

#### 5. Multi-Service Orchestration
Docker Compose management:
- "Start all microservices from compose file"
- "Check status of compose services"
- "Stop everything and remove volumes"

### Real-World Use Cases

**Development Workflow**:
```
User: "Set up a full dev environment"
AI:
  1. docker_run (PostgreSQL)
  2. docker_run (Redis)
  3. docker_run (Nginx)
  4. docker_ps (verify all running)
Result: Complete stack ready in seconds
```

**Testing & CI/CD**:
```
User: "Run integration tests in Docker"
AI:
  1. docker_compose_up (test environment)
  2. docker_exec (run pytest)
  3. docker_logs (check results)
  4. docker_compose_down (cleanup)
Result: Isolated test execution
```

**Container Debugging**:
```
User: "My API container keeps crashing"
AI:
  1. docker_ps (check status)
  2. docker_logs (view errors)
  3. docker_exec (inspect processes)
  4. Provide diagnosis + solution
Result: Root cause identified
```

### Technical Implementation

**Architecture**:
```
AI (GigaChat) → MCP Protocol (HTTP/SSE) → Docker MCP Server → Docker Daemon → Containers
```

**Error Handling**:
- Docker availability check (installed + running)
- Timeout management (30s default, 5min for pulls/builds)
- Comprehensive error messages with actionable solutions
- Graceful handling of container not found, port conflicts, etc.

**Security**:
- Optional API key authentication via `MCP_API_KEY`
- Commands executed via safe subprocess with timeout
- No direct shell injection vulnerabilities
- Docker's native security boundaries maintained

**Performance**:
- Asynchronous tool execution
- Efficient subprocess management
- Minimal overhead (<10ms for most operations)
- Parallel operation support via launcher

### Integration with Existing MCP Ecosystem

Docker MCP Server seamlessly integrates with the existing 6 servers:

**Combined Workflows**:
1. **Weather + Docker**: "Start a weather API in Docker and fetch current weather"
2. **GitHub + Docker**: "Clone my repo and build a Docker image from it"
3. **FileOps + Docker**: "Create a Dockerfile and build the image"
4. **Telegram + Docker**: "Monitor channel and log updates to containerized DB"
5. **Currency + Docker**: "Start a currency conversion service in Docker"
6. **TimeService + Docker**: "Schedule container operations based on timezone"

**Launcher Support**:
```bash
# Start Docker with other servers
python launcher.py docker weather timeservice

# Start all 7 servers
python launcher.py --all --no-auth
```

### Configuration

**Server Details**:
- **Port**: 8006 (auto-assigned by launcher)
- **Transport**: HTTP with SSE (Server-Sent Events)
- **Authentication**: Optional (MCP_API_KEY)
- **Dependencies**: Docker installed and running

**GigaChat App Setup**:
1. Settings → MCP Servers → Add
2. Name: "Docker"
3. Type: HTTP
4. URL: `http://localhost:8006/sse`
5. Enable server

## Previous Updates (Day 14): MCP Composition

This version introduced **MCP Composition** - a comprehensive ecosystem of 6 MCP servers working together, plus a new AI Agent application module. Day 14 expanded from 2 servers to 6 specialized servers with centralized management, demonstrating the full power of Model Context Protocol integration.

### Features from Day 14

#### MCP Server Collection (6 Servers from Day 14)

Day 14 included these MCP servers:

1. **GitHub MCP** (port 8000) - Repository data and operations
2. **Telegram MCP** (port 8001) - Channel reader with monitoring
3. **Weather MCP** (port 8002) - Current weather & forecasts via Open-Meteo
4. **TimeService MCP** (port 8003) - Time in any timezone/city
5. **Currency MCP** (port 8004) - Exchange rates & conversion via ECB
6. **FileOps MCP** (port 8005) - Secure file system operations

**Day 15 adds**:
7. **Docker MCP** (port 8006) - Container and image management 🐳

All servers share common infrastructure (`shared/` module) with unified JSON-RPC 2.0 protocol, SSE transport, and consistent API endpoints.

#### Centralized Launcher System (NEW)

**`mcp-servers/launcher.py`** - Single point of control for all MCP servers:

```bash
python launcher.py                    # List all servers with port status
python launcher.py docker             # Start Docker server (NEW in Day 15)
python launcher.py docker weather     # Start multiple servers
python launcher.py --all --no-auth    # Start all 7 servers
python launcher.py --check            # Check port availability
```

Features:
- **Server Registry**: Centralized configuration with auto-generated port allocation
- **Port Management**: Check availability, detect conflicts, suggest alternatives
- **Parallel Execution**: Start multiple servers simultaneously with prefixed logs
- **Graceful Shutdown**: Ctrl+C stops all servers cleanly

#### AI Agent Module (NEW)

**`ai-agent/`** - Standalone Kotlin Compose Multiplatform application with ClaudeCode-like interface:

- MVI architecture (Store/State/Intent)
- Multiple AI models support (GigaChat, HuggingFace)
- MCP integration with UI management
- SQLDelight database for MCP server persistence
- Material Design 3 UI
- Cross-platform (Android + Desktop)
- Message types: USER, AI, TOOL_CALL, TOOL_RESULT, SYSTEM, ERROR

⚠️ **Status**: In development - basic structure created, compilation issues need fixing

#### New MCP Servers Documentation

**Weather Server** (port 8002):
- `get_current_weather` - Current conditions for any city worldwide
- `get_weather_forecast` - Multi-day forecast up to 16 days
- Data source: Open-Meteo API (free, no API key)

**TimeService Server** (port 8003):
- `get_current_time` - Current UTC time with detailed breakdown
- `get_time_in_timezone` - Time in specific IANA timezone (400+ supported)
- `get_time_in_city` - Auto-detect timezone from city name
- Data source: Python datetime + zoneinfo + Open-Meteo geocoding

**Currency Server** (port 8004):
- `get_exchange_rate` - Current rate between two currencies
- `convert_currency` - Convert amount with calculation details
- `get_latest_rates` - All rates for base currency
- Data source: European Central Bank via Frankfurter API (30+ currencies)

**FileOps Server** (port 8005):
- 8 tools for secure file operations: write, read, list, search, delete, create_dir, get_info
- Root directory isolation, path validation, size limits
- Glob pattern support, regex content search
- Configuration via environment variables

**Docker Server** (port 8006) - NEW in Day 15:
- 13 tools for Docker management: ps, run, stop, start, restart, logs, exec, images, pull, build
- Docker Compose support: up, down, ps
- Container lifecycle management with ports, volumes, environment variables
- Image building from Dockerfile
- Command execution inside containers
- Full error handling with Docker availability checks

#### Local Tool System (from Day 13)
- **In-App Execution**: Local tools run directly in the application without external MCP servers
- **Seamless Integration**: Local tools (in-app) and MCP tools (external servers) work together as unified function calling API
- **Three Built-in Tools**: `setup_reminder`, `update_reminder`, `stop_reminder` for Telegram channel monitoring
- **LocalToolHandler**: Dedicated handler class for local tool execution with validation and error handling

#### Telegram Channel Monitoring
- **Periodic Checks**: AI can set up automatic channel monitoring with customizable intervals (10 seconds to 1 hour)
- **Smart Summarization**: New messages are automatically summarized using AI with specialized system prompt
- **Deduplication**: Tracks last seen message ID to avoid re-processing the same content
- **Flexible Configuration**: Customize check interval (10s/30s/1m/5m/10m/30m/1h) and message count (1-30)
- **Background Execution**: Coroutine-based tick loop runs independently of UI at configured intervals
- **Session Linking**: Reminders can be linked to specific chat sessions for context preservation

#### Reminder Management
- **Natural Language Control**: Set up, modify, and stop reminders through conversational AI commands
  - "Monitor @durov channel every 30 seconds"
  - "Increase interval to 5 minutes"
  - "Stop monitoring"
- **Persistent Storage**: Reminder configurations saved to SQLDelight database (ReminderEntity table)
- **State Management**: Dedicated ReminderStore (MVI pattern) for reminder lifecycle management
- **REMINDER Message Type**: New message type specifically for displaying channel summaries in chat UI
- **Auto-Recovery**: Active reminders automatically restore on app restart

#### TelegramMCPServer Integration
- **Enhanced MCP Server**: Telegram MCP server provides `get_channel_messages` and `get_channel_info` tools
- **Authentication Flow**: One-time OTP setup creates persistent Telegram session
- **Public Channel Access**: Read messages from any public Telegram channel by username
- **Rich Metadata**: Messages include text (up to 500 chars), date, views, media flags, reply info
- **Rate Limiting**: Handles Telegram FloodWaitError gracefully with retry guidance

### Technical Implementation

#### New Components
- `ReminderConfig`: Domain model with channel, interval (enum), messageCount, enabled, sessionId, lastSeenMessageId
- `ReminderInterval`: Enum with predefined intervals and display names (10s to 1 hour)
- `ReminderLocalRepository` / `ReminderLocalRepositoryImpl`: Persistent reminder storage with SQLDelight
- `LocalToolHandler`: Handles local tool execution with full few-shot examples and parameter validation
- `ReminderStore`: MVI store managing reminder lifecycle with background tick loop
- `ReminderState` / `ReminderIntent`: State and intent definitions for reminder MVI pattern

#### Enhanced Function Calling
- **Two-Level Tool Routing**: ChatRepositoryImpl routes tool calls to LocalToolHandler (local) or McpRepository (MCP)
- **Merged Tool Definitions**: Local tools and MCP tools presented to AI as single unified function array
- **Few-Shot Learning**: Local tool definitions include example prompts and parameters for better AI understanding
- **Comprehensive Error Handling**: Tool execution errors returned as McpToolResult with detailed messages

#### Database Schema
```sql
ReminderEntity (
    id TEXT PRIMARY KEY,
    channel TEXT NOT NULL,
    intervalSeconds INTEGER NOT NULL DEFAULT 30,
    messageCount INTEGER NOT NULL DEFAULT 10,
    enabled INTEGER NOT NULL DEFAULT 1,
    sessionId TEXT,
    createdAt INTEGER NOT NULL,
    lastTriggeredAt INTEGER,
    lastSeenMessageId TEXT
)
```

### Use Cases

1. **News Monitoring**: "Monitor @techcrunch every 5 minutes and summarize the latest 5 posts"
2. **Personal Channels**: "Follow @durov every hour and give me updates on his last 10 messages"
3. **Research**: "Track @arxiv_cs_ai every 30 minutes for new papers (20 messages)"
4. **Dynamic Adjustment**: "Increase the check interval to 10 minutes" or "Switch to @python channel"
5. **Stop When Done**: "Stop monitoring the channel"

### Benefits
- **No External Dependencies**: Local tools work without setting up MCP servers
- **Lower Latency**: In-app execution eliminates network round-trips for tool execution
- **Simpler Configuration**: No need to configure stdio commands or HTTP endpoints for basic functionality
- **Unified Experience**: Local and MCP tools work identically from AI perspective
- **Background Intelligence**: AI monitors channels and provides summaries automatically while you work

## Previous Update (Day 13): Telegram Channel Reminders with Local Tools

Day 13 introduced **Local Tool Integration** - built-in tools that execute directly in the app without requiring external MCP servers. The flagship feature is **Telegram Channel Reminders** with automatic monitoring and AI-powered summarization.

### Key Features from Day 13
- **Three Local Tools**: `setup_reminder`, `update_reminder`, `stop_reminder`
- **Background Monitoring**: Coroutine-based tick loop for periodic channel checks
- **Smart Deduplication**: Tracks last seen message ID
- **Persistent Configuration**: ReminderEntity table in SQLDelight
- **Natural Language Control**: "Monitor @durov every 5 minutes"
- **Two-Level Tool System**: Local tools (in-app) + MCP tools (external servers)

## Previous Update (Day 12): MCP Stability & Bug Fixes

This version focused on **stability improvements and critical bug fixes** for MCP integration introduced in Day 11.

### Bug Fixes

#### SSE Connection Stability
- Fixed ChannelWriteException errors on client disconnect
- Proper handling of coroutine cancellation and connection closure
- Clean logs without ERROR spam for normal disconnections
- Changed keepalive loop from `while (true)` to `while (coroutineContext.isActive)`

#### Function Call Serialization
- Fixed JSON parsing and serialization issues
- `FunctionCall.arguments` changed from `String` to `JsonElement` (supports both object and string formats)
- Direct JsonElement passing through execution chain (no more `Map<String, Any>` serialization errors)
- Function results wrapped in JSON format: `{"result": "...", "is_error": false}`

#### SSE Timeout Configuration
- Fixed compilation error with timeout values
- Changed `HttpTimeout.Long.MAX_VALUE` (doesn't exist) to `36000000` (10 hours)
- Applied to both Android and Desktop SSE transport implementations

#### Error Handling
- Improved exception handling in MCP server connections
- Proper handling of connection errors and graceful degradation

### Files Changed
- `composeApp/src/commonMain/.../data/model/GigaChatFunction.kt`
- `composeApp/src/commonMain/.../data/repository/ChatRepositoryImpl.kt`
- `composeApp/src/commonMain/.../data/repository/McpRepository.kt`
- `composeApp/src/commonMain/.../data/repository/McpRepositoryImpl.kt`
- `composeApp/src/commonMain/.../data/mcp/McpClientManager.kt`
- `composeApp/src/androidMain/.../data/mcp/transport/SseTransportImpl.kt`
- `composeApp/src/desktopMain/.../data/mcp/transport/SseTransportImpl.kt`

## Previous Update (Day 11): MCP (Model Context Protocol) Server Integration

This version introduces **MCP Server Integration** - connect your AI to external tools and data sources through the Model Context Protocol. The AI can now call external tools during conversations to retrieve information or perform actions.

### MCP Features

#### External Tool Integration
- **Multiple Transport Types**: Support for local (stdio) and remote (HTTP/SSE) MCP servers
- **Function Calling**: AI can discover and call MCP tools through GigaChat function calling API
- **Server Management UI**: Full-featured management screen for adding, editing, enabling/disabling servers
- **Persistent Storage**: MCP server configurations saved to SQLDelight database
- **Auto-Connect**: Enabled servers automatically connect on app startup
- **Tool Caching**: Discovered tools cached for performance
- **Cross-Platform**: Works on both Android and Desktop with platform-specific transports

#### Server Types
- **Local (stdio)**: Run MCP servers as local processes (Desktop recommended)
- **Remote (HTTP)**: Connect to hosted MCP servers with optional authentication

#### Function Calling Flow
1. AI request sent with available MCP tools as functions
2. If AI wants to call a tool, finishReason = "function_call"
3. Tool executed via McpRepository
4. Result added to conversation history
5. Recursive API call to get final answer
6. Total execution time and tokens tracked across all calls

### Benefits of Day 11 Features
- **Extended Capabilities**: AI can read files, query databases, search web, and more
- **Flexible Integration**: Support both local and remote tool servers
- **Transparent Usage**: AI automatically decides when to use tools
- **Easy Management**: Add, edit, and manage servers through UI
- **Persistent Configuration**: Server settings survive app restarts

### MCP Components
- `McpServer`, `McpTool`, `McpToolResult` domain models
- `McpLocalRepository` for persistent server storage
- `McpClientManager` for connection orchestration
- `McpRepository` for business logic
- `McpTransportFactory` (expect/actual) for platform-specific transport creation
- `McpSettingsCard` brief status display in Settings
- `McpManagementScreen` full server management interface
- `McpServerDialog` for adding/editing server configurations

### Previous Features (from Day 10 - Saving History)

#### Local Database Storage
- **Auto-save**: Every message is automatically saved to local SQLite database
- **Session Management**: Chat sessions are organized and can be browsed
- **Session History**: View all previous conversations with timestamps
- **Search**: Find conversations by title or message content
- **Archive**: Mark conversations as archived without deleting
- **Metadata Preservation**: Execution time and token counts are saved with each message

#### Dialog Compression (Day 9)
- **Smart Summarization**: Automatically compresses conversations when message threshold is reached
- **Manual Compression**: Click create/edit button to compress at any time
- **New Session Creation**: Compression now creates a new session instead of deleting history
- **Context Preservation**: AI uses summary to continue conversation intelligently
- **Configurable Threshold**: Set message threshold from 4-50 (default: 8)

### Previous Features (from Day 8)

#### UI/UX Improvements
- Token breakdown with arrow icons: "↑42 + ↓114" (↑=input, ↓=output)
- Model name displayed in app header
- Automatic whitespace trimming in AI responses

#### Response Metadata Display (Day 7)
- Each AI response shows how long the API request took
- Format: milliseconds (ms) for fast responses, seconds (s) for typical responses
- Token usage display with breakdown: prompt tokens + completion tokens

### Previous Features (from Day 6)

#### Message Copying Features
Full clipboard integration across platforms (Android, Desktop):
- Copy individual messages with one click
- Copy entire conversation history in formatted text
- Cross-platform support using native clipboard APIs

#### Temperature Parameter Testing
Comprehensive documentation in `QUESTIONS.md` for testing temperature effects.

### Previous Features (from Day 5)

#### Preserve Chat History Setting
A toggle in Settings that controls what happens when switching between response modes:
- **OFF (default)**: Chat history is cleared when changing response modes
- **ON**: Chat history is preserved, only the system prompt changes

#### Structured XML Response Mode
A response mode similar to JSON, but using XML format:
- AI responds in strict XML format with question summary, answer, expert role, and unicode symbols
- Perfect for systems that prefer XML over JSON

### Previous Features (from Day 4)

#### Step-by-Step Reasoning Mode
When enabled, the AI solves problems by breaking them down into clear, logical steps. Perfect for:
- Mathematical calculations
- Logical puzzles
- Analytical questions
- Complex decision-making
- Learning and understanding processes

#### Expert Panel Discussion Mode
The AI simulates a panel of 3-4 diverse experts who discuss the topic from different perspectives and form a consensus. Perfect for:
- Getting multiple viewpoints on a topic
- Business decisions
- Technical architecture discussions
- Career advice
- Any topic requiring diverse expertise

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
day-19-citation-and-sources/
├── chat/                        # Main GigaChat chat application
│   └── src/
│       ├── commonMain/          # Shared code
│       │   └── kotlin/ru/chtcholeg/app/
│       │       ├── data/        # Data layer
│       │       │   ├── api/     # API implementations (GigaChat, HuggingFace)
│       │       │   ├── local/   # SQLDelight database + repositories
│       │       │   ├── mcp/     # MCP client manager
│       │       │   ├── tool/    # Local tool execution (Telegram reminders)
│       │       │   ├── model/   # DTOs
│       │       │   └── repository/
│       │       ├── domain/      # Business logic
│       │       │   ├── model/   # Domain models
│       │       │   └── usecase/
│       │       ├── presentation/ # UI layer (MVI)
│       │       │   ├── chat/    # Chat screen
│       │       │   ├── session/ # Session list
│       │       │   ├── reminder/ # Reminder management
│       │       │   ├── settings/ # Settings + MCP management
│       │       │   ├── components/
│       │       │   └── theme/
│       │       ├── di/          # Koin dependency injection
│       │       └── util/        # Utilities
│       ├── androidMain/         # Android-specific code
│       └── desktopMain/         # Desktop-specific code
│
├── ai-agent/                    # AI Agent app with RAG support
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/ru/chtcholeg/agent/
│       │       ├── data/        # AgentRepository, McpRepository, RagRepository
│       │       │   ├── api/     # GigaChat & HuggingFace APIs
│       │       │   ├── local/   # SQLDelight (McpServerEntity, Settings)
│       │       │   └── mcp/     # MCP client manager
│       │       ├── domain/      # AgentMessage, McpServer, RagMode, AiSettings
│       │       ├── presentation/ # AgentScreen (MVI), Settings
│       │       └── di/          # Koin DI
│       ├── androidMain/
│       └── desktopMain/
│
├── shared/                      # Shared services module
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/ru/chtcholeg/shared/
│       │       ├── data/        # API models, MCP SDK stub
│       │       │   └── mcp/     # MCP SDK stub implementation
│       │       └── domain/
│       │           ├── model/   # DocumentChunk, IndexedDocument, Model
│       │           └── service/ # DocumentIndexer, VectorStore, EmbeddingService,
│       │                        # TextChunker, DocumentLoader, PdfParser
│       ├── androidMain/         # Android expect/actual implementations
│       └── desktopMain/         # Desktop expect/actual implementations
│
├── indexer/                     # Document Indexer GUI (Day 17)
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/ru/chtcholeg/indexer/
│       │       ├── data/        # OllamaApi, IndexerLocalRepository
│       │       ├── domain/      # IndexedFile, IndexedChunk, SearchResult
│       │       │   └── service/ # DocumentIndexerService, OllamaEmbeddingService
│       │       ├── presentation/ # IndexerScreen (MVI), IndexerStore
│       │       └── di/          # Koin DI
│       └── desktopMain/         # Desktop entry point
│
├── docs/                        # Additional documentation
│   ├── OLLAMA.md                # Ollama setup guide
│   ├── OLLAMA_RU.md             # Ollama setup guide (Russian)
│   └── ARCHITECTURE_RU.md       # Indexing architecture (Russian)
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md                    # This file
├── USAGE.md                     # RAG Reranking usage examples (Russian)
└── CLAUDE.md                    # Technical documentation for Claude Code
```

## Prerequisites

Before you begin, ensure you have the following installed:

- **JDK 17 or higher**: Required for Kotlin and Gradle
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
cd /Users/shchepilov/AndroidStudioProjects/AI_Advent_Challenge_with_Love/day-19-citation-and-sources
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

### 3. Build the Project

```bash
./gradlew build
```

The BuildKonfig plugin will generate configuration constants from your `local.properties` file during the build process.

## Running the Application

### Chat App (Android)

#### Method 1: Command Line

```bash
# Install on connected device or emulator
./gradlew :chat:installDebug

# Run the app
adb shell am start -n ru.chtcholeg.app/.MainActivity
```

#### Method 2: Android Studio

1. Open the project in Android Studio
2. Ensure `local.properties` is configured with your credentials
3. Select an Android device or emulator
4. Click the **Run** button (or press `Shift + F10`)

### Chat App (Desktop)

```bash
./gradlew :chat:run
```

### AI Agent (Desktop)

```bash
./gradlew :ai-agent:run
```

### Document Indexer (Desktop, requires Ollama)

```bash
./gradlew :indexer:run
```

### Document Indexing CLI

```bash
export GIGACHAT_CLIENT_ID="..." GIGACHAT_CLIENT_SECRET="..."
./gradlew :shared:runIndexing --args="index ./docs ./index.db md txt pdf"
./gradlew :shared:runIndexing --args="search ./index.db 'query' 5"
./gradlew :shared:runIndexing --args="stats ./index.db"

# Index web URLs (NEW in Day 19)
./gradlew :shared:runIndexing --args="indexUrl 'https://example.com' ./index.db"
./gradlew :shared:runIndexing --args="indexUrls 'https://a.com' 'https://b.com' ./index.db"
```

### Clickable Sources Demo (Day 19)

Quick demonstration of clickable web sources:

```bash
# Run demo script (indexes Kotlin documentation)
./demo-clickable-sources.sh

# Launch AI Agent with RAG
./gradlew :ai-agent:run
# In Settings: RAG Mode ON, Index Path: ./demo-web-index.db
# Ask: "What is a coroutine scope in Kotlin?"
# Click [Источник 1] to open kotlinlang.org in browser
```

## Building Distributable Packages

### Android APK

```bash
# Debug APK
./gradlew :chat:assembleDebug

# Release APK (requires signing configuration)
./gradlew :chat:assembleRelease

# Output: chat/build/outputs/apk/
```

### Desktop Installers

```bash
# Package for current OS
./gradlew :chat:packageDistributionForCurrentOS

# Output:
# - macOS: chat/build/compose/binaries/main/dmg/
# - Windows: chat/build/compose/binaries/main/msi/
# - Linux: chat/build/compose/binaries/main/deb/
```

## Features

- Real-time chat with GigaChat AI and HuggingFace models
- **Clickable Web Sources** (NEW in Day 19):
  - Click on source references to open web pages in browser
  - Automatic URL detection (http/https)
  - Cross-platform support (Desktop + Android)
  - Mixed sources: local files + web URLs in same index
  - Visual hyperlink styling for sources
  - New indexing commands: `indexUrl` and `indexUrls`
- **RAG Reranking** (Day 18):
  - Two-stage retrieval: broad vector search + strict relevance filtering
  - Similarity threshold filter removes weakly relevant chunks
  - Score-gap detection finds natural boundaries in relevance scores
  - Configurable parameters via Settings UI (thresholds, top-K limits)
  - Detailed reranker report showing filtering statistics
- **RAG Query** (Day 17):
  - Retrieval-Augmented Generation in AI Agent
  - Automatic context retrieval from indexed documents
  - Cosine similarity search with configurable threshold
  - RAG_CONTEXT message type for chunk display
  - Persistent RAG settings (mode, index path)
- **Document Indexer GUI** (Day 17):
  - Standalone desktop app for indexing documents with Ollama
  - SQLite storage with checksum deduplication
  - File, directory, and URL indexing
  - Real-time progress tracking and semantic search
- **Document Indexing Pipeline** (Day 16):
  - DocumentLoader, TextChunker, EmbeddingService, VectorStore
  - PDF parsing with Apache PDFBox
  - Web page indexing
  - CLI for indexing and search
- **MCP Composition** (Day 14):
  - 7 MCP servers (GitHub, Telegram, Weather, TimeService, Currency, FileOps, Docker)
  - Centralized launcher for managing all servers
  - Unified architecture with shared components
  - AI Agent module for MCP demonstration
  - Multi-tool AI conversations combining multiple servers
- **Telegram Channel Reminders** (Day 13):
  - Automatic monitoring of Telegram channels with AI-powered summarization
  - Local tool execution (setup_reminder, update_reminder, stop_reminder)
  - Customizable check intervals (10s to 1 hour) and message count (1-30)
  - Smart deduplication using last seen message ID
  - Background coroutine tick loop for periodic execution
  - Persistent reminder configurations in SQLDelight database
  - Session linking for context preservation
  - REMINDER message type for displaying summaries
  - Natural language control through AI conversation
  - Integration with TelegramMCPServer for channel data fetching
- **Local Tool Integration** (NEW in Day 13):
  - Built-in tools that execute directly in the app without external servers
  - LocalToolHandler for in-app tool execution with validation
  - Seamless integration with MCP tools (unified function calling API)
  - Two-level tool routing (local vs external)
  - Few-shot learning examples for better AI understanding
- **MCP Server Integration** (Day 11):
  - Connect to external MCP servers (local stdio or remote HTTP)
  - AI can call tools from connected servers
  - Full server management UI
  - Persistent server configurations
  - Cross-platform support
- **Local Chat History** (Day 10):
  - SQLite database for persistent session storage
  - Auto-save every message with full metadata
  - Browse all previous chat sessions
  - Restore conversations at any time
  - Search sessions by title or content
  - Archive old conversations
- **Enhanced Dialog Compression** (Day 9):
  - Smart summarization at configurable message threshold
  - Manual compression via create/edit button
  - Creates new session instead of deleting history
  - Original conversations preserved in session history
- **UI/UX Improvements** (Day 8):
  - Token breakdown with arrow icons
  - Model name displayed in app header
  - Automatic whitespace trimming in AI responses
- **Response Metadata** (Day 7):
  - Execution time display for each AI response
  - Token usage breakdown (prompt + completion tokens)
- **Message Copying** (Day 6):
  - Copy individual messages with one click
  - Copy entire conversation history
  - Cross-platform clipboard support
- **Temperature Parameter Testing** (Day 6):
  - Comprehensive guide with test questions
- **Preserve Chat History on System Prompt Change** (Day 5)
- **Structured XML Response Mode** (Day 5)
- **Step-by-Step Reasoning Mode** (Day 4)
- **Expert Panel Discussion Mode** (Day 4)
- **Dialog Mode** - Interactive requirement gathering
- **Structured JSON Response Mode**
- Cross-platform support (Android, Desktop)
- Clean MVI architecture
- Conversation history management
- Error handling with retry functionality
- Loading indicators
- Message timestamps
- Clear chat functionality
- Configurable AI parameters (temperature, top-p, max tokens, repetition penalty)
- Multiple AI model support (GigaChat, Mistral, Gemma, Llama, DeepSeek, Qwen)

## Using Clickable Web Sources (Day 19)

### What is it?

When RAG mode is enabled and you ask questions, the AI Agent retrieves relevant chunks from indexed documents and displays source references like `[Источник 1]`, `[Источник 2]`. Starting from Day 19, clicking on these sources will:
- **Open web URLs** in your default browser (if source is http/https)
- **Open local files** in your default editor (if source is a file path)

### Quick Start

#### 1. Index a Web Page

```bash
# Option A: Use demo script (indexes Kotlin docs)
./demo-clickable-sources.sh

# Option B: Index manually
./gradlew :shared:runIndexing --args="indexUrl 'https://kotlinlang.org/docs/coroutines-guide.html' ./web-demo.db"

# Option C: Use GUI indexer
./gradlew :indexer:run
# Select "URL" mode, enter URL, click "Index"
```

#### 2. Configure AI Agent

```bash
./gradlew :ai-agent:run
```

In Settings (⚙️):
- **RAG Mode**: ON
- **Index Path**: `./demo-web-index.db` (or your custom index path)
- (Optional) Enable **Reranker** for better filtering

#### 3. Ask and Click

Ask a question related to indexed content:
```
What is a coroutine scope in Kotlin?
```

Click on `[Источник 1]` in the response → kotlinlang.org opens in browser! 🎉

### Mixed Sources (Files + URLs)

You can combine local files and web URLs in the same index:

```bash
# Index local documentation
./gradlew :shared:runIndexing --args="index ./docs ./mixed.db md txt pdf"

# Add web pages to the same index
./gradlew :shared:runIndexing --args="indexUrl 'https://kotlinlang.org/docs/' ./mixed.db"
./gradlew :shared:runIndexing --args="indexUrl 'https://developer.android.com/compose' ./mixed.db"
```

When you click sources:
- Local files → open in editor
- Web URLs → open in browser

### Platform Support

| Platform | URL Opening Method |
|----------|-------------------|
| **Desktop (JVM)** | `Desktop.browse()` with default browser |
| **Android** | `Intent.ACTION_VIEW` with browser app |

### Use Cases

1. **Technical Documentation**: Index API docs, click sources to view full pages
2. **Research Papers**: Index arxiv.org papers, navigate to originals
3. **Blog Articles**: Index tech blogs, jump to full articles
4. **Mixed Knowledge Base**: Combine company docs (files) + external resources (URLs)
5. **Tutorial Learning**: Index tutorial sites, click to see live examples

### Advanced: Indexing Multiple URLs

```bash
# Method 1: Multiple commands
./gradlew :shared:runIndexing --args="indexUrl 'https://site1.com' ./index.db"
./gradlew :shared:runIndexing --args="indexUrl 'https://site2.com' ./index.db"

# Method 2: indexUrls command (batch)
./gradlew :shared:runIndexing --args="indexUrls 'https://site1.com' 'https://site2.com' 'https://site3.com' ./index.db"
```

### Troubleshooting

**Sources not clickable?**
- Ensure RAG Mode is ON in Settings
- Verify Index Path is correct
- Sources should appear as blue, underlined, bold text

**URL doesn't open?**
- Check that URL starts with `http://` or `https://`
- On Desktop, verify `Desktop.isDesktopSupported()` returns true
- Check console logs for errors

**Want to support other protocols?**
- Extend `FileOpener.kt` to handle `ftp://`, `file://`, etc.
- See [DAY_19_CHANGES.md](./DAY_19_CHANGES.md) for implementation details

### Documentation

For more details, see:
- [CLICKABLE_SOURCES.md](./CLICKABLE_SOURCES.md) - Complete guide
- [QUICKSTART_CLICKABLE_SOURCES.md](./QUICKSTART_CLICKABLE_SOURCES.md) - 3-step quick start
- [DAY_19_CHANGES.md](./DAY_19_CHANGES.md) - Technical implementation details

---

## Using MCP Servers

### Quick Start (All 7 Servers)

```bash
# Navigate to MCP servers directory
cd mcp-servers

# Create and activate virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install all servers
pip install -e ".[all]"

# Start all 7 servers at once
python launcher.py --all --no-auth
```

Servers will start on ports 8000-8006. Add them in app Settings → MCP Servers:
- GitHub: http://localhost:8000/sse
- Telegram: http://localhost:8001/sse
- Weather: http://localhost:8002/sse
- TimeService: http://localhost:8003/sse
- Currency: http://localhost:8004/sse
- FileOps: http://localhost:8005/sse
- **Docker: http://localhost:8006/sse** (NEW in Day 15)

### Selective Server Start

```bash
# Start only Docker server (NEW in Day 15)
python launcher.py docker

# Start Docker with other servers
python launcher.py docker weather timeservice

# Check which ports are in use
python launcher.py --check

# List all available servers
python launcher.py
```

### Server Documentation

Each server includes comprehensive documentation in `mcp-servers/<server-name>/`:
- Usage examples
- Tool descriptions with parameters
- Configuration options
- API endpoint details

**Main Documentation:**
- [mcp-servers/README.md](mcp-servers/README.md) - All servers overview
- [mcp-servers/QUICKSTART.md](mcp-servers/QUICKSTART.md) - 5-minute quick start
- [mcp-servers/NEW_SERVERS.md](mcp-servers/NEW_SERVERS.md) - Weather, TimeService, Currency
- [mcp-servers/FILEOPS_SUMMARY.md](mcp-servers/FILEOPS_SUMMARY.md) - FileOps overview
- [MCP_GUIDE.md](MCP_GUIDE.md) - Integration guide for app developers
- **[DOCKER_MCP_GUIDE.md](DOCKER_MCP_GUIDE.md) - Docker integration guide (NEW Day 15)**

**Docker Server Documentation (NEW Day 15)**:
- [mcp-servers/docker/README.md](mcp-servers/docker/README.md) - Docker server overview
- [mcp-servers/docker/examples/USAGE_EXAMPLES.md](mcp-servers/docker/examples/USAGE_EXAMPLES.md) - 30+ usage scenarios
- [mcp-servers/docker/examples/docker-compose-example.yml](mcp-servers/docker/examples/docker-compose-example.yml) - Example compose file
- [mcp-servers/docker/examples/Dockerfile-example](mcp-servers/docker/examples/Dockerfile-example) - Example Dockerfile

## Using Telegram Channel Reminders

### Prerequisites

To use Telegram channel monitoring, you need to set up the TelegramMCPServer:

1. **Get Telegram API Credentials**:
   - Visit [https://my.telegram.org](https://my.telegram.org)
   - Log in with your Telegram account
   - Create a new application
   - Copy `App api_id` and `App api_hash`

2. **Set Up Telegram Session** (one-time):
   ```bash
   cd TelegramMCPServer
   pip install -r requirements.txt
   TELEGRAM_API_ID=your_id TELEGRAM_API_HASH=your_hash python setup_session.py
   ```
   Follow the prompts to enter your phone number and OTP code. This creates `telegram_session.session` file.

3. **Start the Telegram MCP Server**:
   ```bash
   TELEGRAM_API_ID=your_id TELEGRAM_API_HASH=your_hash python main.py --no-auth
   ```
   Server runs at `http://localhost:8000`

4. **Add Server to GigaChat App**:
   - Open Settings → MCP Servers → Add
   - Name: "Telegram MCP"
   - Type: HTTP
   - URL: `http://localhost:8000/sse`
   - Enable the server

### Using Reminders

Once the Telegram MCP server is connected, you can use natural language commands:

**Set up monitoring**:
- "Monitor @durov channel every 30 seconds"
- "Track @techcrunch every 5 minutes and give me the last 10 messages"
- "Follow @python channel hourly"

**Modify active reminder**:
- "Increase the check interval to 10 minutes"
- "Switch to @nodejs channel"
- "Get 20 messages instead of 10"

**Stop monitoring**:
- "Stop monitoring"
- "Turn off the reminder"

### How It Works

1. You ask AI to monitor a Telegram channel
2. AI calls `setup_reminder` local tool with your parameters
3. App starts a background coroutine that runs every N seconds
4. On each tick:
   - Fetches latest messages from Telegram via `get_channel_messages` MCP tool
   - Checks if messages are new (by comparing message IDs)
   - If new messages found, generates AI summary with specialized system prompt
   - Displays summary as REMINDER message in chat
5. Reminder configuration persists in database and survives app restarts

### Available Intervals

- 10 seconds (for testing/demos)
- 30 seconds
- 1 minute
- 5 minutes
- 10 minutes
- 30 minutes
- 1 hour

### Limitations

- Only public Telegram channels (channels with @username)
- One active reminder at a time
- Messages limited to 500 characters (full text available via Telegram)
- Telegram rate limits apply (FloodWaitError if too frequent)
- Requires TelegramMCPServer running locally or remotely

### Troubleshooting Reminders

**Reminder not starting**:
- Ensure TelegramMCPServer is running and connected
- Check that MCP server shows "Connected" status in Settings
- Verify channel username is correct (with or without @)
- Use GigaChat model (function calling not supported on HuggingFace)

**No summaries appearing**:
- Check if channel has new messages
- Reminder shows summaries only when new messages are detected
- Summaries appear as REMINDER messages in chat

**Session expired errors**:
- Run `python setup_session.py` again to re-authenticate
- Delete `telegram_session.session` and create new one

For detailed Telegram MCP server documentation, see `TelegramMCPServer/README.md`.

## Troubleshooting

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
- See MCP_GUIDE.md for detailed troubleshooting

### Android Build Fails

**Check:**
- Android SDK is installed and `ANDROID_HOME` is set
- Target SDK version matches your installed SDK
- Run `./gradlew :composeApp:dependencies` to check for conflicts

### Desktop Build Fails

**Check:**
- JDK version is 17 or higher: `java -version`
- `JAVA_HOME` is set correctly
- Try running with `--stacktrace` for more details

## Project Configuration

### Selecting Response Mode

The application features six distinct response modes accessible through a dropdown menu in Settings:

1. **Normal Mode** (default): Standard conversational AI responses
2. **Structured JSON Response**: AI returns data in strict JSON format
3. **Structured XML Response**: AI returns data in strict XML format
4. **Dialog Mode**: AI asks clarifying questions to gather information
5. **Step-by-Step Reasoning**: AI solves problems step by step
6. **Expert Panel Discussion**: AI simulates expert panel discussion

### Setting Up Telegram Channel Reminders

1. Set up and start TelegramMCPServer (see "Using Telegram Channel Reminders" section above)
2. Add Telegram MCP server in Settings → MCP Servers
3. Use natural language commands to AI:
   - "Monitor @channelname every 5 minutes"
   - "Track @durov hourly with last 15 messages"
4. AI will automatically set up the reminder
5. Summaries appear as REMINDER messages in chat

### Configuring MCP Servers

1. Open Settings from the chat screen
2. Tap on the **MCP Servers** card
3. Tap **+** to add a new server
4. Choose server type (Local or HTTP)
5. Enter configuration details
6. Enable the server to connect

### Changing AI Model

Go to Settings and select from available models:
- GigaChat / GigaChat Pro / GigaChat Max (Sberbank) - Supports function calling
- Mistral 7B Instruct (HuggingFace)
- Gemma 2 9B IT (HuggingFace)
- Llama 3.2 3B Instruct (HuggingFace)
- DeepSeek R1 (HuggingFace)
- Qwen3 8B (HuggingFace)

**Note:** Only GigaChat models support MCP tool calling.

### Adjusting AI Parameters

Edit settings in the app UI:
- Temperature (0.0-2.0): Controls randomness
- Top P (0.0-1.0): Nucleus sampling threshold
- Max Tokens (1-8192): Response length limit
- Repetition Penalty (0.0-2.0): Reduces repetition

## Development

### Adding New Features

1. **Data Layer**: Add models, API methods, repository methods
2. **Domain Layer**: Create use cases for business logic
3. **Presentation Layer**: Define new intents and update state
4. **UI Layer**: Create composable components

### Running Tests

```bash
./gradlew test
```

### Code Style

This project follows the [Official Kotlin Code Style](https://kotlinlang.org/docs/coding-conventions.html).

## License

This project is created for educational purposes.

## Contact

For issues or questions, please refer to the GigaChat API documentation:
- [GigaChat API Docs](https://developers.sber.ru/docs/ru/gigachat/api/overview)

## Documentation Files

This project includes the following documentation:

**Main:**
- **README.md** (this file) - Complete setup and usage guide
- **USAGE.md** - RAG Reranking usage examples and scenarios (Russian)
- **CLAUDE.md** - Technical documentation for Claude Code integration

**Day 19 (Clickable Sources):**
- **CLICKABLE_SOURCES.md** - Detailed guide on using clickable web sources
- **DAY_19_CHANGES.md** - Technical details of Day 19 implementation changes
- **QUICKSTART_CLICKABLE_SOURCES.md** - Quick start guide (3 steps)
- **demo-clickable-sources.sh** - Demo script for testing clickable sources

**Indexing & RAG:**
- **docs/ARCHITECTURE_RU.md** - Indexing architecture documentation (Russian)
- **docs/OLLAMA.md** - Ollama setup guide for local embeddings
- **docs/OLLAMA_RU.md** - Ollama setup guide (Russian)

**AI Agent Module:**
- **ai-agent/README.md** - AI Agent architecture and usage

## Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- UI powered by [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- AI by [GigaChat](https://developers.sber.ru/portal/products/gigachat)
- MCP protocol by [Model Context Protocol](https://modelcontextprotocol.io)

## Videos

- https://disk.yandex.ru/i/h19TykN5-sIvhA
