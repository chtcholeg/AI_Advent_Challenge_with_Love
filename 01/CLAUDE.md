# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GigaChat Multiplatform Chat Application - a cross-platform AI chat app built with Kotlin Compose Multiplatform supporting Android, Desktop (JVM), and Web (WasmJs). Integrates with GigaChat AI (Sberbank) and Hugging Face models.

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
./gradlew :composeApp:runDesktop

# Web - Start development server at localhost:8080
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web - Production build
./gradlew :composeApp:wasmJsBrowserProductionWebpack
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
- `presentation/settings/`: AI parameter configuration UI
- `presentation/components/`: Reusable UI components (MessageList, MessageItem, MessageInput)
- `presentation/theme/`: Material Design 3 theming with dark mode support

**Domain Layer**:
- `domain/model/ChatMessage`: UI-focused message model with auto-generated IDs
- `domain/model/AiSettings`: AI parameter configuration with validation (temperature, topP, maxTokens, repetitionPenalty)
- `domain/model/Model`: Sealed interface for supported AI models (GigaChat, Llama variants, DeepSeek)
- `domain/usecase/SendMessageUseCase`: Input validation and delegation to repository

**Data Layer**:
- `data/api/`: API interfaces and implementations
  - `GigaChatApiImpl`: Sberbank GigaChat with OAuth 2.0 authentication
  - `HuggingFaceApiImpl`: Hugging Face router with rate limiting (1000ms delay)
- `data/model/`: Request/response DTOs for serialization
- `data/repository/`:
  - `ChatRepositoryImpl`: Maintains conversation history, routes to correct API based on selected model, manages GigaChat token expiration
  - `SettingsRepositoryImpl`: In-memory settings state with validation

### Dependency Injection (Koin)

**Setup**: `di/Koin.kt` defines the DI container with `initKoin()` function.

**Modules**:
- **App Module** (common): HttpClient (Ktor with JSON, logging, 30s timeout), API implementations, repositories, use cases, ChatStore
- **Platform Modules** (expect/actual): Android provides Application context, Desktop/WasmJs provide empty modules

**Key Singletons**:
- HttpClient: Shared across all platforms with content negotiation, logging, timeout config
- API implementations: GigaChatApi, HuggingFaceApi
- Repositories: ChatRepository, SettingsRepository

**Scoping**: ChatStore uses `CoroutineScope(Dispatchers.Default + SupervisorJob())` for async operations.

### Multi-Platform Structure

**Common Code** (`composeApp/src/commonMain`): 99% of app logic (UI, data, domain, DI)

**Platform-Specific Code**:
- `androidMain/`: Application class, MainActivity, Activity Compose integration
- `desktopMain/`: Desktop entry point (`main.kt` with Window configuration)
- `wasmJsMain/`: Web entry point with Canvas-based rendering, custom index.html handling

**Ktor Engines**:
- Android/Desktop: OkHttp
- Web: JS fetch API

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

| Component | Path |
|-----------|------|
| MVI Store | `presentation/chat/ChatStore.kt` |
| Chat Screen | `presentation/chat/ChatScreen.kt` |
| Settings Screen | `presentation/settings/SettingsScreen.kt` |
| Chat Repository | `data/repository/ChatRepositoryImpl.kt` |
| GigaChat API | `data/api/GigaChatApiImpl.kt` |
| Koin DI Setup | `di/Koin.kt` |
| Domain Models | `domain/model/{ChatMessage,AiSettings,Model}.kt` |
| Build Config | `composeApp/build.gradle.kts` |
| Main Entry (Desktop) | `desktopMain/kotlin/ru/chtcholeg/app/main.kt` |
| Main Entry (Android) | `androidMain/kotlin/ru/chtcholeg/app/MainActivity.kt` |

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
- Implement `actual` function in `androidMain`/`desktopMain`/`wasmJsMain`
- Example: `di/PlatformModule.kt`

## Common Issues

### Empty Credentials
**Symptom**: Build succeeds but app shows authentication errors
**Fix**: Ensure `local.properties` exists with valid credentials, then run `./gradlew clean build`

### Web Build Index.html Missing
**Symptom**: Web app shows blank page
**Fix**: Custom copy tasks `copyWasmIndexHtml` and `copyWasmIndexHtmlProduction` handle this automatically. Verify `src/wasmJsMain/resources/index.html` exists.

### Koin Injection Failures
**Symptom**: `NoBeanDefFoundException`
**Fix**: Ensure `initKoin()` is called before Compose UI initialization in all platform entry points

### GigaChat Token Expiration
**Symptom**: API calls fail after long idle time
**Fix**: ChatRepositoryImpl automatically re-authenticates. Check token expiry logic in `sendMessage()`.

## Configuration Locations

- **Android**: `composeApp/build.gradle.kts` android block (namespace, SDK versions, signing)
- **Desktop**: `composeApp/build.gradle.kts` compose.desktop block (mainClass, nativeDistributions, icons)
- **Dependencies**: `gradle/libs.versions.toml` (version catalog)
- **Gradle Settings**: `settings.gradle.kts` (repositories, plugin management)
- **Package Name**: `ru.chtcholeg.app` (defined in BuildKonfig and Android namespace)

## AI Model Settings

The app supports runtime configuration of AI parameters via SettingsScreen:
- **Temperature** (0.0-2.0): Controls randomness (0.7 default)
- **Top P** (0.0-1.0): Nucleus sampling for diversity (0.9 default)
- **Max Tokens** (1-8192): Response length limit (2048 default)
- **Repetition Penalty** (0.0-2.0): Reduces repetition (1.0 default)

See `AI_SETTINGS.md` for detailed parameter guidance and recommended presets.
