# GigaChat Multiplatform Chat Application

A cross-platform chat application built with Kotlin Compose Multiplatform that integrates with GigaChat AI. The application runs on Android, Desktop (JVM), and Web (WasmJs).

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
- **GigaChat API**: AI chatbot backend

## Project Structure

```
AI_Advent_Challenge_6/
├── composeApp/
│   └── src/
│       ├── commonMain/          # Shared code
│       │   └── kotlin/
│       │       └── com/gigachat/app/
│       │           ├── data/           # Data layer (API, models, repository)
│       │           ├── domain/         # Business logic (models, use cases)
│       │           ├── presentation/   # UI layer (MVI, components)
│       │           ├── di/             # Dependency injection
│       │           └── util/           # Utilities
│       ├── androidMain/         # Android-specific code
│       ├── desktopMain/         # Desktop-specific code
│       └── wasmJsMain/          # Web-specific code
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
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
cd /Users/shchepilov/AndroidStudioProjects/AI_Advent_Challenge_6
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

The BuildKonfig plugin will generate configuration constants from your `local.properties` file during the build process. These constants are embedded into the application for all platforms (Android, Desktop, Web).

## Running the Application

### Android

#### Method 1: Command Line

```bash
# Install on connected device or emulator
./gradlew :composeApp:installDebug

# Run the app
adb shell am start -n com.gigachat.app/.MainActivity
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

### Web (WasmJs)

```bash
# Start development server
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

The application will open in your default browser at `http://localhost:8080`.

**Note:** All platforms use the same credentials from `local.properties`, which are embedded at build time via the BuildKonfig plugin.

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

### Web Build

```bash
# Production build
./gradlew :composeApp:wasmJsBrowserProductionWebpack

# Output: composeApp/build/dist/wasmJs/productionExecutable/
```

Deploy the contents of the output directory to your web server.

## Features

- Real-time chat with GigaChat AI
- Cross-platform support (Android, Desktop, Web)
- Clean MVI architecture
- Conversation history management
- Error handling with retry functionality
- Loading indicators
- Message timestamps
- Clear chat functionality

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
- If using Android Studio/IntelliJ, sync the Gradle project (File → Sync Project with Gradle Files)

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

### Network Errors

**Symptom:** Connection errors when sending messages

**Check:**
- Internet connection is active
- GigaChat API is accessible
- API credentials are valid
- Check API rate limits

### Koin Injection Errors

**Symptom:** `NoBeanDefFoundException` or similar

**Solution:**
- Ensure `initKoin()` is called before creating composables
- Check that all dependencies are properly defined in Koin modules
- Verify platform-specific modules are created correctly

## Project Configuration

### Changing GigaChat Model

Edit `composeApp/src/commonMain/kotlin/com/gigachat/app/data/api/GigaChatApi.kt`:

```kotlin
suspend fun sendMessage(
    accessToken: String,
    messages: List<Message>,
    model: String = "GigaChat"  // Change model here
): ChatResponse
```

### Adjusting AI Temperature

Edit `composeApp/src/commonMain/kotlin/com/gigachat/app/data/api/GigaChatApiImpl.kt`:

```kotlin
val request = ChatRequest(
    model = model,
    messages = messages,
    temperature = 0.7  // Adjust temperature (0.0 - 1.0)
)
```

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

## Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- UI powered by [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- AI by [GigaChat](https://developers.sber.ru/portal/products/gigachat)
