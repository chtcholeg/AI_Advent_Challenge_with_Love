# CLAUDE.md

Project guidance for Claude Code.

## Project Overview

**GigaChat Multiplatform Chat App** - Kotlin Compose Multiplatform (Android/Desktop) with GigaChat AI and Hugging Face integration.

**Modules**: `chat/` (main app), `ai-agent/` (MCP agent), `shared/` (API, models, indexing)

**Key Features**:
- Response modes: Normal, JSON, XML, Dialog, Step-by-Step, Expert Panel
- Local SQLDelight storage with session management and compression
- MCP server integration + local tools (Telegram reminders)
- Document indexing with semantic search (GigaChat embeddings)

## Build Commands

```bash
# Build
./gradlew build
./gradlew clean build  # after credential changes

# Run
./gradlew :chat:run                    # Desktop
./gradlew :chat:installDebug           # Android
./gradlew :ai-agent:run                # AI Agent

# Document Indexing CLI
export GIGACHAT_CLIENT_ID="..." GIGACHAT_CLIENT_SECRET="..."
./gradlew :shared:runIndexing --args="index ./docs ./index.json md txt"
./gradlew :shared:runIndexing --args="search ./index.json 'query' 5"
./gradlew :shared:runIndexing --args="stats ./index.json"

# Distribution
./gradlew :chat:assembleDebug
./gradlew :chat:packageDistributionForCurrentOS
```

## Credentials

`local.properties` (git-ignored):
```properties
gigachat.clientId=...
gigachat.clientSecret=...
huggingface.apiToken=...
```
Access via `BuildKonfig.GIGACHAT_CLIENT_ID`.

## Architecture

### MVI Pattern
- **ChatStore**: StateFlow orchestrator (`presentation/chat/ChatStore.kt`)
- **ChatIntent**: User actions (SendMessage, ClearChat, etc.)
- **ChatState**: Immutable UI state
- Flow: Intent → Store → Repository → API → State → UI

### Layers
| Layer | Components |
|-------|-----------|
| Presentation | `presentation/chat/`, `presentation/session/`, `presentation/settings/`, `presentation/components/` |
| Domain | `domain/model/` (ChatMessage, ChatSession, AiSettings, AiResponse), `domain/usecase/` |
| Data | `data/api/` (GigaChatApi, HuggingFaceApi), `data/repository/`, `data/local/` (SQLDelight) |

### Module Structure
```
chat/           → shared/
ai-agent/       → shared/
shared/         (API, models, indexing services)
```

### Key Patterns
- **expect/actual**: ClipboardManager, DatabaseDriverFactory, FileSystem
- **Multi-API**: Model.provider routes to GigaChat or HuggingFace
- **OAuth 2.0**: Auto re-auth on token expiry

## Key Files

| Component | Path |
|-----------|------|
| Chat MVI | `presentation/chat/{ChatStore,ChatIntent,ChatState,ChatScreen}.kt` |
| Session MVI | `presentation/session/{SessionListStore,SessionListScreen}.kt` |
| Repository | `data/repository/ChatRepositoryImpl.kt` |
| Local DB | `data/local/ChatLocalRepositoryImpl.kt` |
| SQL Schemas | `sqldelight/.../local/{ChatSession,ChatMessage,Reminder}.sq` |
| GigaChat API | `data/api/GigaChatApiImpl.kt` |
| DI | `di/Koin.kt` |
| MCP | `data/mcp/McpRepository.kt`, `McpManagementScreen.kt` |
| Local Tools | `data/tool/LocalToolHandler.kt` |
| Reminders | `presentation/reminder/ReminderStore.kt` |
| Indexing | `shared/.../domain/service/{DocumentIndexer,VectorStore,EmbeddingService}*.kt` |

## Development Guidelines

### Adding AI Models
1. Add to `domain/model/Model` sealed interface
2. Specify provider (GIGACHAT/HUGGINGFACE)
3. Update `Model.fromId()` and SettingsScreen

### Platform-Specific Code
Use expect/actual: declare in `commonMain`, implement in `androidMain`/`desktopMain`.

### Database Changes
1. Create/edit `.sq` in `sqldelight/`
2. Run `./gradlew generateCommonMainChatDatabaseInterface`
3. Update repository and Koin module

### Local Tools
1. Add to `LocalToolHandler.LOCAL_TOOL_DEFINITIONS`
2. Add name to `LocalToolHandler.TOOL_NAMES`
3. Implement in `LocalToolHandler.execute()`

## Common Issues

| Issue | Fix |
|-------|-----|
| Auth errors | Check `local.properties`, run `./gradlew clean build` |
| NoBeanDefFoundException | Ensure `initKoin()` called before UI |
| DB crash after schema change | `./gradlew clean`, check SQL syntax, delete `~/.ai-chat/chat.db` |
| Indexing credentials error | Set env vars or check local.properties |

## Configuration

| Config | Location |
|--------|----------|
| Android | `chat/build.gradle.kts` android block |
| Desktop | `chat/build.gradle.kts` compose.desktop block |
| Dependencies | `gradle/libs.versions.toml` |
| SQLDelight | `chat/build.gradle.kts` sqldelight block |
| Packages | `ru.chtcholeg.app` (chat), `ru.chtcholeg.agent` (agent), `ru.chtcholeg.shared` (shared) |

## AI Settings

Runtime config via SettingsScreen:
- Temperature (0.0-2.0, default 0.7)
- Top P (0.0-1.0, default 0.9)
- Max Tokens (1-8192, default 2048)
- Repetition Penalty (0.0-2.0, default 1.0)

## Documentation

| File | Description |
|------|-------------|
| QUICKSTART.md | Document indexing quick start |
| INDEXING.md | Complete indexing API reference |
| EXAMPLES.md | Usage examples |
| AI_SETTINGS.md | Parameter guidance |
