# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GigaChat Multiplatform Chat Application - a cross-platform AI chat app built with Kotlin Compose Multiplatform supporting Android and Desktop (JVM). Integrates with GigaChat AI (Sberbank) and Hugging Face models.

**New Feature (day-02)**: Structured JSON Response Mode - AI can respond in strict JSON format with question summary, detailed response, expert role, and unicode symbols. Includes toggle between JSON and formatted view.

**New Feature (day-03)**: Dialog Mode - Interactive requirement gathering through conversation. AI asks clarifying questions **one at a time** (strictly enforced) to collect all necessary information before providing a comprehensive result.

**New Feature (day-04)**: Different Reasoning Modes - Two new powerful reasoning approaches:
- **Step-by-Step Reasoning**: AI solves problems by breaking them down into clear, logical steps. Shows reasoning at each stage with structured format (Understanding, Given Information, Steps, Final Answer). Ideal for math, logic, and analytical questions.
- **Expert Panel Discussion**: AI simulates a panel of 3-4 diverse experts who discuss the topic from different perspectives, engage in realistic debate, and form a consensus conclusion. Perfect for business decisions, career advice, and topics requiring multiple viewpoints.

**New Feature (day-05)**: Preserve History on System Prompt Change & Structured XML Response Mode:
- **Preserve Chat History**: New setting that controls whether chat history is cleared or preserved when changing response modes. When enabled, switching modes only updates the system prompt without losing conversation context.
- **Structured XML Response**: New response mode similar to JSON but using XML format. AI responds with question_short, answer, responder_role, and unicode_symbols in valid XML structure.

**New Feature (day-06)**: Message Copying & Temperature Testing:
- **Copy Individual Messages**: Each message has a copy button (📋 icon) for one-click copying to clipboard
- **Copy Entire Conversation**: Button in TopAppBar to copy full chat history in formatted text
- **Cross-Platform Clipboard Support**: Implemented via expect/actual pattern for Android and Desktop
- **Temperature Testing Guide**: QUESTIONS.md file with 6 curated questions to demonstrate temperature parameter effects (0, 0.7, 1.2)

**New Feature (day-07)**: Response Metadata Display:
- **Execution Time**: Each AI response shows request execution time (ms/s/m format)
- **Token Usage**: Display of total tokens with breakdown (prompt + completion tokens)
- **AiResponse Model**: New domain model class to return response with metadata from repository
- **measureTimedValue**: Kotlin time measurement for accurate execution timing

**New Feature (day-08)**: UI/UX Improvements & Local Chat Storage:
- **Arrow Icons for Tokens**: Token breakdown now shows "↑42 + ↓114" (↑=prompt/input, ↓=completion/output)
- **Model Name in Header**: Current AI model name displayed under "AI Chat" title
- **Response Trimming**: AI responses automatically trimmed to remove leading/trailing whitespace
- **Local Chat Storage (SQLDelight)**: Persistent storage of chat sessions with full history recovery
- **Session Management**: Create, load, archive, unarchive, and delete chat sessions
- **Chat History Screen**: Browse all sessions with search, archive toggle, and session preview
- **Auto-save**: Messages automatically saved to local database as they are sent/received
- **Cross-platform Database**: SQLDelight with platform-specific drivers (Android SQLite, Desktop JDBC)

**New Feature (day-10)**: Visual Display of Compressed Chat History:
- **Compression Metadata**: ChatSession and ChatMessage models extended with compression tracking fields
- **CompressedHistoryBlock**: New UI component for visual display of compressed history
  - Expandable header showing "Compressed History (X messages)"
  - Undo button (← icon) to restore original session
  - Summary message always visible in highlighted box
  - Original messages shown with dimmed effect (alpha=0.7) when expanded
  - Animated expand/collapse transitions
  - "New Messages" separator between compressed and new messages
- **Lazy Loading**: Original compressed messages only loaded on first expansion
  - Reduces memory usage
  - Improves initial load performance
  - Graceful error handling with silent fallback
- **Compression Workflow**:
  - Old session automatically archived (not deleted) when compressing
  - New session marked as compressed with reference to original session
  - Compression point (timestamp) recorded for splitting messages
  - Summary message tagged with `isSummary=true` and `compressedMessageCount`
- **Undo Compression**: Fully reversible operation
  - Unarchives original session
  - Deletes compressed session
  - Restores full conversation history
  - Clears compression state from UI
- **API Integration**: Only summary + new messages sent to API
  - Original compressed messages not re-sent
  - Conversation context preserved through summary
  - Reduces token usage and improves efficiency
- **Database Enhancements**:
  - New columns: ChatSessionEntity (isCompressed, originalSessionId, compressionPoint)
  - New columns: ChatMessageEntity (isSummary, compressedMessageCount)
  - New queries: selectOriginalSession, updateCompressionInfo, selectBeforeCompressionPoint, selectAfterCompressionPoint
  - Full compression metadata persisted across sessions

**New Feature (day-10)**: Saving Chat History to Database:
- **Local SQLite Database**: SQLDelight stores all chat sessions and messages persistently
- **Auto-save Messages**: Every message automatically saved with timestamp, execution time, and token counts
- **Session Management**: Browse, restore, rename, archive, and search previous chat sessions
- **Database Schema**: ChatSessionEntity and ChatMessageEntity with proper relationships and indexes
- **Cross-platform Storage**: Android (internal app storage), Desktop (~/.ai-chat/chat.db)
- **Enhanced Compression**: Compression now creates new session instead of replacing old one
  - Old session preserved with all original messages
  - New session labeled "Continue: {old_title}" with summary as first message
  - Complete history accessible via Chat History screen

**New Feature (day-13)**: Telegram Channel Reminders with Local Tools:
- **Local Tool Integration**: Built-in tools executed directly in the app without external MCP servers
- **Telegram Channel Monitoring**: AI can set up periodic monitoring of Telegram channels with automatic summarization
- **Three Local Tools**:
  - `setup_reminder`: Configure periodic channel monitoring with customizable interval (10s-1h) and message count (1-30)
  - `update_reminder`: Modify active reminder parameters (channel, interval, message count)
  - `stop_reminder`: Stop active monitoring
- **Automatic Summarization**: New messages from monitored channels are automatically summarized using AI
- **Smart Deduplication**: Tracks last seen message ID to avoid re-processing the same messages
- **Persistent Configuration**: Reminder settings saved to SQLDelight database (ReminderEntity table)
- **Background Execution**: Coroutine-based tick loop runs at configured intervals independent of UI
- **ReminderStore (MVI)**: Dedicated state management for reminder lifecycle (Start, Stop, UpdateConfig, Activate)
- **REMINDER Message Type**: New message type for displaying channel summaries in chat
- **Session Linking**: Reminders can be linked to specific chat sessions for context preservation
- **Two-Level Tool System**: Local tools (in-app execution) + MCP tools (external servers) work seamlessly together

**New Feature (day-11)**: MCP (Model Context Protocol) Server Integration:
- **External Tool Integration**: Connect AI to external tools and data sources via MCP protocol
- **Multiple Transport Types**: Support for local (stdio) and remote (HTTP/SSE) MCP servers
- **Function Calling**: AI can discover and call MCP tools through GigaChat function calling API
- **Server Management UI**: Full-featured management screen for adding, editing, enabling/disabling servers
- **Persistent Storage**: MCP server configurations saved to SQLDelight database
- **Auto-Connect**: Enabled servers automatically connect on app startup
- **Tool Caching**: Discovered tools cached for performance
- **Cross-Platform**: Works on both Android and Desktop with platform-specific transports
- **Stub SDK Implementation**: Ready to swap with official MCP Kotlin SDK when available

**Reminder Components (NEW in day-13)**:
- `ReminderConfig`: Domain model for reminder configuration (channel, interval, messageCount, enabled, sessionId, lastSeenMessageId)
- `ReminderInterval`: Enum with predefined intervals (10s, 30s, 1m, 5m, 10m, 30m, 1h) and display names
- `ReminderLocalRepository`: Interface for persistent reminder storage (saveOrUpdate, getActive, getBySessionId, updateLastSeen, disable, delete)
- `ReminderLocalRepositoryImpl`: Implementation using SQLDelight queries on ReminderEntity table
- `LocalToolHandler`: Executes local tools in-app (setup_reminder, update_reminder, stop_reminder)
- `ReminderStore`: MVI store managing reminder lifecycle with background coroutine tick loop
- `ReminderState`: UI state (activeConfig, lastSummary, lastSummaryChannel, error)
- `ReminderIntent`: User actions (Start, Stop, UpdateConfig, Activate)
- `MessageType.REMINDER`: New message type for displaying channel summaries in chat UI

**MCP Components**:
- `McpServer`, `McpTool`, `McpToolResult` domain models
- `McpLocalRepository` for persistent server storage
- `McpClientManager` for connection orchestration
- `McpRepository` for business logic
- `McpTransportFactory` (expect/actual) for platform-specific transport creation
- `McpSettingsCard` brief status display in Settings
- `McpManagementScreen` full server management interface
- `McpServerDialog` for adding/editing server configurations

**Function Calling Flow (Enhanced in day-13)**:
1. AI request sent with available tools as functions (MCP tools + Local tools merged)
2. If AI wants to call a tool, finishReason = "function_call"
3. Tool execution routing:
   - Local tools (setup_reminder, update_reminder, stop_reminder) → LocalToolHandler (in-app execution)
   - MCP tools → McpRepository (external server execution)
4. Result added to conversation history
5. Recursive API call to get final answer
6. Total execution time and tokens tracked across all calls

**Bug Fixes (day-12)**: MCP Stability & Function Calling Fixes:
- **SSE Connection Stability**: Fixed ChannelWriteException errors on client disconnect
  - Proper handling of coroutine cancellation and connection closure
  - Clean logs without ERROR spam for normal disconnections
  - Changed keepalive loop from `while (true)` to `while (coroutineContext.isActive)`
- **Function Call Serialization**: Fixed JSON parsing and serialization issues
  - `FunctionCall.arguments` changed from `String` to `JsonElement` (supports both object and string formats)
  - Direct JsonElement passing through execution chain (no more `Map<String, Any>` serialization errors)
  - Function results wrapped in JSON format: `{"result": "...", "is_error": false}`
- **SSE Timeout Configuration**: Fixed compilation error with timeout values
  - Changed `HttpTimeout.Long.MAX_VALUE` (doesn't exist) to `36000000` (10 hours)
  - Applied to both Android and Desktop SSE transport implementations
- **Error Handling**: Improved exception handling in MCP server connections
  - Proper handling of connection errors and graceful degradation

**Previous Feature (day-09)**: Dialog Compression:
- **Automatic Summarization**: Conversation is automatically compressed when message threshold is reached (optional)
- **Manual Compression**: Create/Edit button (Icons.Filled.Create) in ChatScreen toolbar triggers immediate compression
- **Configurable Threshold**: Message threshold configurable from 4 to 50 messages (default: 8)
- **New Session Creation**: Compression creates "Continue: {title}" session instead of replacing history
- **Setting**: `summarizationReplaceHistory` controls compression behavior

Response modes (Normal, JSON, XML, Dialog, Step-by-Step, Expert Panel) are mutually exclusive through dropdown menu selection.

## Build Commands

### Development Setup
```bash
# Initial build - loads credentials from local.properties and embeds them via BuildKonfig
./gradlew build

# Clean build (use when credentials change or build is corrupted)
./gradlew clean build
```

### Running the Application
```bash
# Android - Install and run on connected device/emulator
./gradlew :composeApp:installDebug
adb shell am start -n ru.chtcholeg.app/.MainActivity

# Desktop - Launch JVM application
./gradlew :composeApp:run

# Desktop - Launch in debug mode (attach debugger on port 5005)
./gradlew :composeApp:run -Pdebug          # Debugger optional
./gradlew :composeApp:run -Pdebug.suspend  # Wait for debugger to attach
```

### Distribution Packages
```bash
# Android APK
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:assembleRelease  # requires signing config

# Desktop installers (DMG/MSI/DEB based on current OS)
./gradlew :composeApp:packageDistributionForCurrentOS
```

### Testing
```bash
# Run all tests
./gradlew test

# Check dependencies
./gradlew :composeApp:dependencies
```

## Credentials Configuration

Credentials are managed via `local.properties` in the project root (git-ignored):

```properties
gigachat.clientId=your_client_id_here
gigachat.clientSecret=your_client_secret_here
huggingface.apiToken=your_token_here
```

These are loaded by `composeApp/build.gradle.kts` and embedded at build time via the BuildKonfig plugin. When credentials change, run `./gradlew clean build` to regenerate BuildKonfig constants.

**Location**: Build constants are generated in `ru.chtcholeg.app.BuildKonfig` and accessed via `BuildKonfig.GIGACHAT_CLIENT_ID`, etc.

## Architecture Overview

### MVI (Model-View-Intent) Pattern

**Core Components**:
- **ChatStore** (`presentation/chat/ChatStore.kt`): Central state orchestrator using `StateFlow<ChatState>`. Dispatches intents, manages async operations, handles errors.
- **ChatIntent** (`presentation/chat/ChatIntent.kt`): Sealed interface for user actions (`SendMessage`, `RetryLastMessage`, `ClearChat`).
- **ChatState** (`presentation/chat/ChatState.kt`): Immutable UI state (messages, loading, error).
- **ChatScreen** (`presentation/chat/ChatScreen.kt`): View layer that collects state and dispatches intents.

**Flow**: User Action → Intent → Store → Repository → API → State Update → UI Recompose

### Layer Structure

**Presentation Layer**:
- `presentation/chat/`: MVI components (Store, Intent, State, Screen)
- `presentation/session/`: Session list MVI components (SessionListStore, SessionListState, SessionListIntent, SessionListScreen) **(NEW in day-08)**
- `presentation/settings/`: AI parameter configuration UI
- `presentation/components/`: Reusable UI components (MessageList, MessageItem, MessageInput)
- `presentation/theme/`: Material Design 3 theming with dark mode support

**Domain Layer**:
- `domain/model/ChatMessage`: UI-focused message model with auto-generated IDs, MessageType enum (USER, AI, SYSTEM), plus metadata fields: executionTimeMs, promptTokens, completionTokens, totalTokens **(NEW in day-07)**
- `domain/model/ChatSession`: Session model with id, title, timestamps, modelName, isArchived, lastMessage, messageCount **(NEW in day-08)**
- `domain/model/AiResponse`: Data class containing response content and metadata (executionTimeMs, token counts) **(NEW in day-07)**
- `domain/model/ResponseMode`: Enum for AI response modes (NORMAL, STRUCTURED_JSON, STRUCTURED_XML, DIALOG, STEP_BY_STEP, EXPERT_PANEL) - mutually exclusive by design
- `domain/model/AiSettings`: AI parameter configuration with validation (temperature, topP, maxTokens, repetitionPenalty, responseMode, systemPrompt, preserveHistoryOnSystemPromptChange). Includes system prompts: DIALOG_SYSTEM_PROMPT, STEP_BY_STEP_SYSTEM_PROMPT, EXPERT_PANEL_SYSTEM_PROMPT, STRUCTURED_XML_SYSTEM_PROMPT
- `domain/model/Model`: Sealed interface for supported AI models (GigaChat, Llama variants, DeepSeek)
- `domain/model/StructuredResponse`: Data class for structured JSON responses with tryParse() and looksLikeStructuredResponse() methods
- `domain/usecase/SendMessageUseCase`: Input validation and delegation to repository, returns AiResponse **(UPDATED in day-07)**

**Data Layer**:
- `data/api/`: API interfaces and implementations
  - `GigaChatApiImpl`: Sberbank GigaChat with OAuth 2.0 authentication
  - `HuggingFaceApiImpl`: Hugging Face router with rate limiting (1000ms delay)
- `data/model/`: Request/response DTOs for serialization
- `data/repository/`:
  - `ChatRepository`: Interface returning AiResponse with metadata **(UPDATED in day-07)**
  - `ChatRepositoryImpl`: Maintains conversation history, routes to correct API based on selected model, manages GigaChat token expiration, measures execution time using measureTimedValue **(UPDATED in day-07)**
  - `SettingsRepositoryImpl`: In-memory settings state with validation
- `data/local/`: Local database storage **(NEW in day-08)**
  - `ChatDatabase`: SQLDelight generated database class
  - `ChatLocalRepository`: Interface for local CRUD operations on sessions and messages
  - `ChatLocalRepositoryImpl`: Implementation with Flow-based reactive queries
  - `DatabaseDriverFactory`: expect/actual for platform-specific SQLite drivers
  - SQL schemas: `ChatSession.sq`, `ChatMessage.sq` in `sqldelight/` directory

### Dependency Injection (Koin)

**Setup**: `di/Koin.kt` defines the DI container with `initKoin()` function.

**Modules**:
- **App Module** (common): HttpClient (Ktor with JSON, logging, 30s timeout), API implementations, repositories, use cases, ChatStore, SessionListStore, ChatDatabase, ChatLocalRepository **(UPDATED in day-08)**
- **Platform Modules** (expect/actual): Android provides Application context and DatabaseDriverFactory, Desktop provides DatabaseDriverFactory **(UPDATED in day-08)**

**Key Singletons**:
- HttpClient: Shared across all platforms with content negotiation, logging, timeout config
- API implementations: GigaChatApi, HuggingFaceApi
- Repositories: ChatRepository, SettingsRepository, ChatLocalRepository **(UPDATED in day-08)**
- Database: ChatDatabase (SQLDelight) **(NEW in day-08)**

**Scoping**: ChatStore and SessionListStore use `CoroutineScope(Dispatchers.Default + SupervisorJob())` for async operations.

### Multi-Platform Structure

**Common Code** (`composeApp/src/commonMain`): 99% of app logic (UI, data, domain, DI)

**Platform-Specific Code**:
- `androidMain/`: Application class, MainActivity, Activity Compose integration, DatabaseDriverFactory (AndroidSqliteDriver), ClipboardManager
- `desktopMain/`: Desktop entry point (`main.kt` with Window configuration), DatabaseDriverFactory (JdbcSqliteDriver), ClipboardManager

**Ktor Engines**:
- Android/Desktop: OkHttp

**Database Location** **(NEW in day-08)**:
- Android: Internal app storage (`chat.db`)
- Desktop: `~/.ai-chat/chat.db`

## Key Implementation Patterns

### Multi-API Support
The app supports multiple AI providers (GigaChat, Hugging Face) through:
1. `Model` sealed interface with `provider` property (GIGACHAT or HUGGINGFACE)
2. `ChatRepositoryImpl` routes messages based on `SettingsRepository.settings.model.provider`
3. API implementations share common interface signatures for seamless switching

### State Management
- Reactive settings via `StateFlow<AiSettings>` in SettingsRepository
- UI collects state with `collectAsState()` for automatic recomposition
- Unidirectional data flow: Intent → Store → State → UI

### Error Handling
- Try-catch in ChatStore with error state propagation to UI
- Retry functionality stores last user message for re-sending
- User-facing error messages with retry button

### Authentication
- GigaChat uses OAuth 2.0 with token caching in ChatRepositoryImpl
- Token expiration tracking (expires_at from API response)
- Automatic re-authentication on token expiry

## Important File Locations

| Component | Path                                                                                                          |
|-----------|---------------------------------------------------------------------------------------------------------------|
| MVI Store | `presentation/chat/ChatStore.kt` **(Enhanced in day-10)**                                                     |
| Chat Screen | `presentation/chat/ChatScreen.kt` **(Enhanced in day-10)**                                                    |
| Settings Screen | `presentation/settings/SettingsScreen.kt`                                                                     |
| **Session List Store** | `presentation/session/SessionListStore.kt` **(NEW in day-08)**                                                |
| **Session List Screen** | `presentation/session/SessionListScreen.kt` **(NEW in day-08)**                                               |
| Chat Repository | `data/repository/ChatRepositoryImpl.kt`                                                                       |
| **Local Repository** | `data/local/ChatLocalRepositoryImpl.kt` **(NEW in day-08, Enhanced in day-10, Enhanced in day-10)**           |
| **SQL Schemas** | `sqldelight/ru/chtcholeg/app/data/local/{ChatSession,ChatMessage}.sq` **(NEW in day-08, Enhanced in day-10)** |
| **Database Factory (expect)** | `data/local/DatabaseDriverFactory.kt` **(NEW in day-08)**                                                     |
| **Database Factory (Android)** | `androidMain/.../data/local/DatabaseDriverFactory.android.kt` **(NEW in day-08)**                             |
| **Database Factory (Desktop)** | `desktopMain/.../data/local/DatabaseDriverFactory.desktop.kt` **(NEW in day-08)**                             |
| **Compressed History Block** | `presentation/components/CompressedHistoryBlock.kt` **(NEW in day-10)**                                       |
| **Message List** | `presentation/components/MessageList.kt` **(Enhanced in day-10)**                                             |
| GigaChat API | `data/api/GigaChatApiImpl.kt`                                                                                 |
| Koin DI Setup | `di/Koin.kt`                                                                                                  |
| Domain Models | `domain/model/{ChatMessage,AiSettings,Model,ChatSession}.kt`                                                  |
| **AiResponse Model** | `domain/model/AiResponse.kt` **(NEW in day-07)**                                                              |
| **Clipboard (expect)** | `util/ClipboardManager.kt`                                                                                    |
| **Clipboard (Android)** | `androidMain/.../util/ClipboardManager.android.kt`                                                            |
| **Clipboard (Desktop)** | `desktopMain/.../util/ClipboardManager.desktop.kt`                                                            |
| Build Config | `composeApp/build.gradle.kts`                                                                                 |
| Main Entry (Desktop) | `desktopMain/kotlin/ru/chtcholeg/app/main.kt`                                                                 |
| Main Entry (Android) | `androidMain/kotlin/ru/chtcholeg/app/MainActivity.kt`                                                         |
| **Compression Testing Guide** | `COMPRESSION_TESTING.md` **(NEW in day-10)**                                                                  |
| **Compression Implementation** | `COMPRESSION_IMPLEMENTATION.md` **(NEW in day-10)**                                                           |
| **Reminder Store** | `presentation/reminder/ReminderStore.kt` **(NEW in day-13)**                                                  |
| **Reminder State** | `presentation/reminder/ReminderState.kt` **(NEW in day-13)**                                                  |
| **Reminder Intent** | `presentation/reminder/ReminderIntent.kt` **(NEW in day-13)**                                                 |
| **Reminder Config Model** | `domain/model/ReminderConfig.kt` **(NEW in day-13)**                                                          |
| **Reminder Local Repository** | `data/local/ReminderLocalRepository.kt` **(NEW in day-13)**                                                   |
| **Reminder Local Repository Impl** | `data/local/ReminderLocalRepositoryImpl.kt` **(NEW in day-13)**                                               |
| **Local Tool Handler** | `data/tool/LocalToolHandler.kt` **(NEW in day-13)**                                                           |
| **Reminder SQL Schema** | `sqldelight/ru/chtcholeg/app/data/local/Reminder.sq` **(NEW in day-13)**                                     |
| **Telegram MCP Server** | `TelegramMCPServer/` directory **(UPDATED in day-13)**                                                        |

## Development Guidelines

### Adding New AI Models
1. Add model variant to `domain/model/Model` sealed interface
2. Specify provider (GIGACHAT or HUGGINGFACE)
3. Add model identifier to `Model.fromId()` lookup
4. Update SettingsScreen dropdown if needed

### Adding New AI Parameters
1. Add field to `domain/model/AiSettings` with validation
2. Update `AiSettings.validated()` with range constraints
3. Add slider/input to SettingsScreen
4. Ensure API implementation sends parameter in request body

### Modifying API Behavior
- GigaChat authentication: `data/api/GigaChatApiImpl.authenticate()`
- Request formatting: `data/model/ChatRequest`
- Response parsing: `data/model/ChatResponse`
- Conversation history: `data/repository/ChatRepositoryImpl.conversationHistory`

### Platform-Specific Code
Use expect/actual pattern:
- Declare `expect` function in `commonMain`
- Implement `actual` function in `androidMain`/`desktopMain`
- Example: `di/PlatformModule.kt`, `util/ClipboardManager.kt`, `data/local/DatabaseDriverFactory.kt`

### Local Chat Storage (NEW in day-08)

The application uses SQLDelight for persistent local storage of chat sessions and messages.

**Database Schema:**
```
ChatSessionEntity                    ChatMessageEntity
├── id (PK, TEXT)                   ├── id (PK, TEXT)
├── title (TEXT)                    ├── sessionId (FK → ChatSessionEntity)
├── createdAt (INTEGER)             ├── content (TEXT)
├── updatedAt (INTEGER)             ├── isFromUser (INTEGER: 0/1)
├── modelName (TEXT)                ├── timestamp (INTEGER)
└── isArchived (INTEGER: 0/1)       ├── messageType (TEXT)
                                    ├── executionTimeMs (INTEGER?)
                                    ├── promptTokens (INTEGER?)
                                    ├── completionTokens (INTEGER?)
                                    └── totalTokens (INTEGER?)
```

**Key Components:**
- `ChatLocalRepository`: Interface defining CRUD operations for sessions and messages
- `ChatLocalRepositoryImpl`: Implementation using SQLDelight queries with Flow-based reactive data
- `DatabaseDriverFactory`: Platform-specific SQLite driver creation (expect/actual pattern)
- `SessionListStore`: MVI store for session list management
- `SessionListScreen`: UI for browsing, searching, and managing sessions

**Auto-save Flow:**
1. First message creates a new session with auto-generated title (first 50 chars of message)
2. Each sent/received message is saved to database via `ChatLocalRepository.saveMessage()`
3. Session timestamp updated on each new message
4. Messages persist across app restarts

**Session Management Intents:**
```kotlin
ChatIntent.CreateNewSession    // Start fresh chat
ChatIntent.LoadSession(id)     // Load existing session
ChatIntent.UpdateSessionTitle  // Rename session
```

**Adding New Database Tables:**
1. Create `.sq` file in `sqldelight/ru/chtcholeg/app/data/local/`
2. Define table schema and queries
3. Run `./gradlew generateCommonMainChatDatabaseInterface` to generate Kotlin code
4. Add mapper functions in repository implementation
5. Update Koin module if new repository needed

### Telegram Channel Reminders (NEW in day-13)

The application provides automatic monitoring of Telegram channels with AI-powered summarization.

**Database Schema:**
```
ReminderEntity
├── id (PK, TEXT)
├── channel (TEXT)                # Telegram channel username without @
├── intervalSeconds (INTEGER)     # Check interval in seconds
├── messageCount (INTEGER)        # Number of messages to fetch (1-30)
├── enabled (INTEGER: 0/1)        # Active status
├── sessionId (TEXT?)             # Linked chat session
├── createdAt (INTEGER)           # Creation timestamp
├── lastTriggeredAt (INTEGER?)    # Last execution timestamp
└── lastSeenMessageId (TEXT?)     # Last processed message ID for deduplication
```

**Key Components:**
- `ReminderConfig`: Domain model with validation (channel, interval, messageCount, enabled)
- `ReminderInterval`: Enum with predefined intervals (TEN_SECONDS=10, THIRTY_SECONDS=30, ONE_MINUTE=60, FIVE_MINUTES=300, TEN_MINUTES=600, THIRTY_MINUTES=1800, ONE_HOUR=3600)
- `ReminderLocalRepository`: Persistent storage interface (saveOrUpdate, getActive, getBySessionId, updateLastSeen, disable, delete)
- `LocalToolHandler`: Executes three local tools in-app without external MCP servers
- `ReminderStore`: MVI store with background coroutine tick loop for periodic execution

**Local Tools:**
1. **setup_reminder**: Configure monitoring with parameters:
   - `channel`: Telegram channel username (with or without @)
   - `interval_seconds`: Check interval (10, 30, 60, 300, 600, 1800, 3600)
   - `message_count`: Messages to fetch (1-30, default 10)

2. **update_reminder**: Modify active reminder:
   - `field`: Parameter to change (channel, interval_seconds, message_count)
   - `value`: New value as string (auto-parsed)

3. **stop_reminder**: Stop active monitoring (no parameters)

**Execution Flow:**
1. AI calls `setup_reminder` via function calling API
2. `LocalToolHandler.execute()` creates `ReminderConfig` and dispatches `ReminderIntent.Start`
3. `ReminderStore` saves config to database and starts background tick loop
4. Every interval seconds:
   - Fetch N messages from Telegram channel via MCP tool `get_channel_messages`
   - Check if maxId matches lastSeenMessageId (skip if duplicate)
   - Generate summary using AI with custom system prompt
   - Update lastSeenMessageId and lastTriggeredAt in database
   - Update ReminderState with summary (displayed as REMINDER message in chat)
5. User can update parameters or stop via AI commands

**Adding New Local Tools:**
1. Add tool definition to `LocalToolHandler.LOCAL_TOOL_DEFINITIONS` with:
   - name, description, parameters schema
   - fewShotExamples for few-shot learning
2. Add tool name to `LocalToolHandler.TOOL_NAMES` set
3. Implement handler method in `LocalToolHandler.execute()`
4. Local tools are automatically merged with MCP tools in `ChatRepositoryImpl.sendMessage()`
5. Tool routing in `executeFunctionCall()` checks `LocalToolHandler.TOOL_NAMES` first

**Integration with MCP:**
- Local tools (in-app) and MCP tools (external servers) work together seamlessly
- All tools presented to AI as functions in single array
- Tool execution routed by name: local tools → LocalToolHandler, MCP tools → McpRepository
- Reminder system uses MCP tool `get_channel_messages` from TelegramMCPServer for data fetching

### Response Metadata Display (NEW in day-07)

The application displays execution time and token usage for each AI response:

**Data Flow:**
1. `ChatRepositoryImpl.sendMessage()` uses `measureTimedValue` to time the API call
2. Returns `AiResponse` with content, executionTimeMs, and token counts from `ChatResponse.usage`
3. `ChatStore` creates `ChatMessage` with metadata fields populated
4. `MessageItem` displays metadata above the timestamp for AI messages

**Key Components:**
- `AiResponse`: Domain model containing response content and metadata
- `ChatMessage`: Extended with optional executionTimeMs, promptTokens, completionTokens, totalTokens fields
- `MessageItem`: UI displays formatted time and token breakdown

**Time Formatting:**
- < 1s: "342ms"
- 1s-60s: "2.1s"
- >= 60s: "1m 15s"

**Token Display:**
- "156 tokens (↑42 + ↓114)" shows total with arrow icons (↑=input, ↓=output) **(UPDATED in day-08)**
- Only displayed when API returns usage data

### Adding Clipboard Functionality
The clipboard functionality is implemented using expect/actual pattern:
1. **Common Interface** (`util/ClipboardManager.kt`):
   - Defines `expect object ClipboardManager` with `copyToClipboard(text: String)` function
2. **Android Implementation** (`ClipboardManager.android.kt`):
   - Uses `android.content.ClipboardManager` and `ClipData`
   - Requires Context from Koin DI
3. **Desktop Implementation** (`ClipboardManager.desktop.kt`):
   - Uses `java.awt.Toolkit.getDefaultToolkit().systemClipboard`
   - Uses `StringSelection` for clipboard content

To use clipboard in code:
```kotlin
ClipboardManager.copyToClipboard("Text to copy")
```

**MVI Integration:**
- Add `CopyMessage` and `CopyAllMessages` intents to `ChatIntent`
- Handle intents in `ChatStore.dispatch()`
- UI components dispatch intents when copy buttons are clicked
- Store methods call `ClipboardManager.copyToClipboard()` directly

## Common Issues

### Empty Credentials
**Symptom**: Build succeeds but app shows authentication errors
**Fix**: Ensure `local.properties` exists with valid credentials, then run `./gradlew clean build`

### Koin Injection Failures
**Symptom**: `NoBeanDefFoundException`
**Fix**: Ensure `initKoin()` is called before Compose UI initialization in all platform entry points

### GigaChat Token Expiration
**Symptom**: API calls fail after long idle time
**Fix**: ChatRepositoryImpl automatically re-authenticates. Check token expiry logic in `sendMessage()`.

### Database Schema Changes (NEW in day-08)
**Symptom**: App crashes on startup after modifying `.sq` files
**Fix**:
1. Run `./gradlew clean` to clear generated code
2. Check SQL syntax in `.sq` files
3. For schema migrations, consider adding migration queries or clearing app data during development
4. Desktop: Delete `~/.ai-chat/chat.db` to reset database

## Configuration Locations

- **Android**: `composeApp/build.gradle.kts` android block (namespace, SDK versions, signing)
- **Desktop**: `composeApp/build.gradle.kts` compose.desktop block (mainClass, nativeDistributions, icons)
- **Dependencies**: `gradle/libs.versions.toml` (version catalog)
- **Gradle Settings**: `settings.gradle.kts` (repositories, plugin management)
- **Package Name**: `ru.chtcholeg.app` (defined in BuildKonfig and Android namespace)
- **SQLDelight**: `composeApp/build.gradle.kts` sqldelight block (database name, package, srcDirs) **(NEW in day-08)**

## AI Model Settings

The app supports runtime configuration of AI parameters via SettingsScreen:
- **Temperature** (0.0-2.0): Controls randomness (0.7 default)
- **Top P** (0.0-1.0): Nucleus sampling for diversity (0.9 default)
- **Max Tokens** (1-8192): Response length limit (2048 default)
- **Repetition Penalty** (0.0-2.0): Reduces repetition (1.0 default)

See `AI_SETTINGS.md` for detailed parameter guidance and recommended presets.
