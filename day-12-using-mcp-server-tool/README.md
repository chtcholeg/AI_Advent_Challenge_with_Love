# GigaChat Multiplatform Chat Application (Day 12 - MCP Stability & Bug Fixes)

A cross-platform chat application built with Kotlin Compose Multiplatform that integrates with GigaChat AI. The application runs on Android and Desktop (JVM).

## Updates in Day 12: MCP Stability & Bug Fixes

This version focuses on **stability improvements and critical bug fixes** for MCP integration introduced in Day 11.

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
- Improved exception handling in SimpleMCPServer
- Global StatusPages handler now ignores expected ChannelWriteException
- Added specific handlers for CancellationException, IOException, ClosedReceiveChannelException
- Graceful degradation on connection errors

### Files Changed
- `SimpleMCPServer/src/main/kotlin/com/example/mcp/mcp/SseTransport.kt`
- `SimpleMCPServer/src/main/kotlin/com/example/mcp/Application.kt`
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
- **GigaChat API**: AI chatbot backend with function calling support

## Project Structure

```
day-11-connecting-mcp-server/
├── composeApp/
│   └── src/
│       ├── commonMain/          # Shared code
│       │   └── kotlin/
│       │       └── ru/chtcholeg/app/
│       │           ├── data/           # Data layer
│       │           │   ├── api/        # API implementations
│       │           │   ├── local/      # Local database
│       │           │   ├── mcp/        # MCP client (NEW in Day 11)
│       │           │   │   ├── stub/   # MCP SDK stub
│       │           │   │   └── McpClientManager.kt
│       │           │   ├── model/      # DTOs
│       │           │   └── repository/ # Repositories
│       │           ├── domain/         # Business logic
│       │           │   ├── model/      # Domain models
│       │           │   └── usecase/    # Use cases
│       │           ├── presentation/   # UI layer (MVI)
│       │           │   ├── chat/       # Chat screen
│       │           │   ├── session/    # Session list
│       │           │   ├── settings/   # Settings
│       │           │   │   └── mcp/    # MCP management (NEW in Day 11)
│       │           │   ├── components/ # Reusable UI
│       │           │   └── theme/      # Material Design 3
│       │           ├── di/             # Dependency injection
│       │           └── util/           # Utilities
│       ├── androidMain/         # Android-specific code
│       └── desktopMain/         # Desktop-specific code
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
├── README_RU.md
├── CLAUDE.md
├── DIFF_11-12.md          # Changelog between Day 11 and Day 12 (Bug Fixes)
├── MCP_GUIDE.md           # MCP server integration guide (NEW in Day 11)
└── QUESTIONS.md           # Temperature testing guide
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
cd /Users/shchepilov/AndroidStudioProjects/AI_Advent_Challenge_with_Love/day-11-connecting-mcp-server
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

### Android

#### Method 1: Command Line

```bash
# Install on connected device or emulator
./gradlew :composeApp:installDebug

# Run the app
adb shell am start -n ru.chtcholeg.app/.MainActivity
```

#### Method 2: Android Studio

1. Open the project in Android Studio
2. Ensure `local.properties` is configured with your credentials
3. Select an Android device or emulator
4. Click the **Run** button (or press `Shift + F10`)

### Desktop (JVM)

```bash
# Run the desktop application
./gradlew :composeApp:runDesktop
```

**Alternative:** Run from IntelliJ IDEA:
1. Open the project in IntelliJ IDEA
2. Ensure `local.properties` is configured with your credentials
3. Run the desktop main function (Shift + F10)

## Building Distributable Packages

### Android APK

```bash
# Debug APK
./gradlew :composeApp:assembleDebug

# Release APK (requires signing configuration)
./gradlew :composeApp:assembleRelease

# Output: composeApp/build/outputs/apk/
```

### Desktop Installers

```bash
# Package for current OS
./gradlew :composeApp:packageDistributionForCurrentOS

# Output:
# - macOS: composeApp/build/compose/binaries/main/dmg/
# - Windows: composeApp/build/compose/binaries/main/msi/
# - Linux: composeApp/build/compose/binaries/main/deb/
```

## Features

- Real-time chat with GigaChat AI and HuggingFace models
- **MCP Server Integration** (NEW in Day 11):
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
- Multiple AI model support (GigaChat, Llama, DeepSeek)

## Using MCP Servers

See [MCP_GUIDE.md](MCP_GUIDE.md) for detailed instructions on:
- Adding local and remote MCP servers
- Configuring server settings
- Popular MCP server examples
- Troubleshooting connection issues
- Best practices for security and performance

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

### Configuring MCP Servers

1. Open Settings from the chat screen
2. Tap on the **MCP Servers** card
3. Tap **+** to add a new server
4. Choose server type (Local or HTTP)
5. Enter configuration details
6. Enable the server to connect

### Changing AI Model

Go to Settings and select from available models:
- GigaChat (Sberbank) - Supports function calling
- Llama 3.2 3B Instruct (HuggingFace)
- Meta Llama 3 70B Instruct (HuggingFace)
- DeepSeek V3 (HuggingFace)

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

This project includes comprehensive documentation:

- **README.md** (this file) - Complete setup and usage guide
- **README_RU.md** - Complete setup and usage guide (Russian)
- **CLAUDE.md** - Technical documentation for Claude Code integration
- **DIFF_11-12.md** - Detailed changelog between Day 11 and Day 12 (Stability & Bug Fixes)
- **MCP_GUIDE.md** - MCP server integration guide (NEW in Day 11)
- **QUESTIONS.md** - Temperature parameter testing guide

## Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- UI powered by [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- AI by [GigaChat](https://developers.sber.ru/portal/products/gigachat)
- MCP protocol by [Model Context Protocol](https://modelcontextprotocol.io)

## Videos

- https://disk.yandex.ru/i/-xKKVH1NWKyaJw
