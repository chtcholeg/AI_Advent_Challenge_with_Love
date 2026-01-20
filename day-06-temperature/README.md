# GigaChat Multiplatform Chat Application (Day 6 - Message Copying & Temperature Testing)

A cross-platform chat application built with Kotlin Compose Multiplatform that integrates with GigaChat AI. The application runs on Android, Desktop (JVM), and Web (WasmJs).

## New in Day 6: Message Copying & Temperature Parameter Testing

This version introduces powerful message copying capabilities and comprehensive documentation for testing the Temperature parameter:

### Message Copying Features
Full clipboard integration across all platforms (Android, Desktop, Web):

#### Copy Individual Messages
- Each message now has a copy button (📋 icon) in the bottom-right corner
- One-click copying of any message (User, AI, or System)
- Works seamlessly on all platforms using native clipboard APIs

#### Copy Entire Conversation History
- New copy button in the top toolbar
- Copies the entire chat history in a formatted structure:
  ```
  User: [message]

  AI: [response]

  User: [next message]
  ...
  ```
- System messages are automatically excluded from the export
- Perfect for saving conversations, sharing with colleagues, or analysis

**Platform Implementation:**
- **Android**: Uses `android.content.ClipboardManager`
- **Desktop**: Uses `java.awt.Toolkit` and `StringSelection`
- **Web**: Uses modern `navigator.clipboard.writeText()` API

### Temperature Parameter Testing Guide
A comprehensive `QUESTIONS.md` file with 6 carefully designed questions to demonstrate the impact of the Temperature parameter (0, 0.7, 1.2):

1. **Creative naming** - Shows creativity vs predictability
2. **Story continuation** - Demonstrates narrative variety
3. **Abstract descriptions** - Tests metaphorical thinking
4. **Mathematical explanations** - Shows precision vs elaboration
5. **Synonym generation** - Demonstrates vocabulary diversity
6. **Creative problem-solving** - Tests "thinking outside the box"

Each question includes:
- Expected behavior for each temperature value
- Practical conclusions about when to use each setting
- Recommendations for optimal temperature based on task type

**📊 Real Experiment Results Available!**
See `TEMPERATURE_EXPERIMENT_RESULTS.md` for actual GigaChat responses at different temperatures with detailed analysis.

**Temperature Quick Guide (Experimentally Verified):**
- **0.0 - 0.3**: Mostly predictable, but NOT identical (math, technical docs)
- **0.5 - 0.9**: **OPTIMAL** - Balanced, versatile (most tasks, conversation) ✅
- **1.0 - 2.0**: High creativity with risks (brainstorming, experimental)

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
ПРGhbdt
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
- **GigaChat API**: AI chatbot backend

## Project Structure

```
day-06-temperature/
├── composeApp/
│   └── src/
│       ├── commonMain/          # Shared code
│       │   └── kotlin/
│       │       └── ru/chtcholeg/app/
│       │           ├── data/           # Data layer (API, models, repository)
│       │           ├── domain/         # Business logic (models, use cases)
│       │           ├── presentation/   # UI layer (MVI, components)
│       │           ├── di/             # Dependency injection
│       │           └── util/           # Utilities
│       │               └── ClipboardManager.kt (expect)  # NEW in Day 6
│       ├── androidMain/         # Android-specific code
│       │   └── kotlin/
│       │       └── ru/chtcholeg/app/
│       │           └── util/
│       │               └── ClipboardManager.android.kt (actual)  # NEW
│       ├── desktopMain/         # Desktop-specific code
│       │   └── kotlin/
│       │       └── ru/chtcholeg/app/
│       │           └── util/
│       │               └── ClipboardManager.desktop.kt (actual)  # NEW
│       └── wasmJsMain/          # Web-specific code
│           └── kotlin/
│               └── ru/chtcholeg/app/
│                   └── util/
│                       └── ClipboardManager.wasmJs.kt (actual)  # NEW
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
├── CLAUDE.md
├── DIFF_05-06.md          # NEW in Day 6 - Changelog
└── QUESTIONS.md           # NEW in Day 6 - Temperature testing guide
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

- Real-time chat with GigaChat AI and HuggingFace models
- **Message Copying** (NEW in Day 6):
  - Copy individual messages with one click
  - Copy entire conversation history
  - Cross-platform clipboard support (Android, Desktop, Web)
  - Formatted export for easy sharing and analysis
- **Temperature Parameter Testing** (NEW in Day 6):
  - Comprehensive guide with 6 test questions
  - Demonstrates impact of temperature on AI responses
  - Best practices for different use cases
- **Preserve Chat History on System Prompt Change** (Day 5):
  - Toggle to preserve conversation when switching response modes
  - System prompt updates without losing chat context
  - Configurable in Settings
- **Structured XML Response Mode** (Day 5):
  - AI responds in strict XML format
  - Includes question summary, answer, expert role, and symbols
  - Complements existing JSON mode for XML-preferring systems
- **Step-by-Step Reasoning Mode** - AI solves problems step-by-step:
  - Breaks down complex problems into logical steps
  - Shows reasoning at each step
  - Provides clear final answers with explanation
  - Ideal for math, logic, and analytical questions
- **Expert Panel Discussion Mode** - Simulated expert discussion:
  - 3-4 diverse experts with unique perspectives
  - Realistic debate and discussion
  - Consensus-based conclusions
  - Multiple viewpoints on any topic
- **Dialog Mode** - Interactive requirement gathering through conversation:
  - AI asks clarifying questions one at a time
  - Builds context progressively through dialogue
  - Collects all necessary information before providing final result
  - Perfect for creating technical specifications, project plans, and requirements
- **Structured JSON Response Mode** - AI responds in strict JSON format with:
  - Question summary
  - Detailed response
  - Expert role identification
  - Relevant unicode symbols
  - Toggle between JSON and formatted view
- Cross-platform support (Android, Desktop, Web)
- Clean MVI architecture
- Conversation history management
- Error handling with retry functionality
- Loading indicators
- Message timestamps
- Clear chat functionality
- Configurable AI parameters (temperature, top-p, max tokens, repetition penalty)
- Multiple AI model support (GigaChat, Llama, DeepSeek)

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

### Selecting Response Mode (Updated in Day 5)

The application now features six distinct response modes accessible through a dropdown menu in Settings:

1. Open Settings (gear icon)
2. Find "Response Mode" dropdown selector
3. Choose from six options:
   - **Normal Mode** (default): Standard conversational AI responses
   - **Structured JSON Response**: AI returns data in strict JSON format
   - **Structured XML Response** (NEW): AI returns data in strict XML format
   - **Dialog Mode**: AI asks clarifying questions to gather complete information
   - **Step-by-Step Reasoning**: AI solves problems step by step
   - **Expert Panel Discussion**: AI simulates expert panel discussion

### Preserve Chat History Setting (NEW in Day 5)

Located in Settings, this toggle controls behavior when changing response modes:

1. Open Settings (gear icon)
2. Find "Preserve Chat History" toggle below Response Mode selector
3. **OFF** (default): Changing response mode clears the chat
4. **ON**: Changing response mode preserves chat history, only updates system prompt

Use this when you want to:
- Continue a conversation with a different AI approach
- Keep context while switching between reasoning styles
- Experiment with different modes without losing chat history

**Response Mode Details:**

#### Normal Mode
- AI responds directly to your questions
- Conversational and natural interaction
- Best for general Q&A and casual conversation

#### Structured JSON Response Mode
- AI responds in strict JSON format with:
  - Question summary
  - Detailed response
  - Expert role
  - Unicode emoji symbols
- Click "Format" button to view beautifully formatted response
- Perfect for structured data extraction

#### Structured XML Response Mode (NEW in Day 5)
- AI responds in strict XML format with:
  - Question summary
  - Detailed answer
  - Expert role
  - Unicode emoji symbols
- Similar to JSON mode but for XML-preferring workflows
- Perfect for integration with XML-based systems

**Example XML Response:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<response>
  <question_short>How to make pizza?</question_short>
  <answer>Prepare dough with flour, water, yeast, salt. Let it rise, roll out, add toppings, bake at 250°C.</answer>
  <responder_role>Chef</responder_role>
  <unicode_symbols>🍕👨‍🍳🔥</unicode_symbols>
</response>
```

#### Dialog Mode (Recommended for Requirements Gathering)
- AI asks clarifying questions **one at a time** (never multiple questions at once)
- Builds context progressively through dialogue
- Collects all necessary information before providing final result
- Perfect for:
  - Creating technical specifications
  - Generating project requirements
  - Planning system architecture
  - Drafting detailed documentation
  - Gathering business requirements

#### Step-by-Step Reasoning Mode (NEW in Day 4)
- AI breaks down problems into numbered steps
- Shows reasoning at each stage
- Provides clear structure:
  - Understanding the Problem
  - Given Information
  - Step-by-Step Solution
  - Final Answer/Conclusion
- Perfect for:
  - Mathematical problems
  - Logical puzzles
  - Analytical questions
  - Learning complex concepts

**Example Step-by-Step Workflow:**
```
User: "If a train travels 120 km in 2 hours, how long will it take to travel 300 km?"

AI:
**Understanding the Problem:**
We need to find the time required for a train to travel 300 km.

**Given Information:**
- Distance traveled: 120 km
- Time taken: 2 hours
- New distance to calculate: 300 km

**Step 1: Calculate the train's speed**
Speed = 120 km / 2 hours = 60 km/h

**Step 2: Calculate time for new distance**
Time = 300 km / 60 km/h = 5 hours

**Final Answer:**
The train will take **5 hours** to travel 300 km.
```

#### Expert Panel Discussion Mode (NEW in Day 4)
- AI simulates 3-4 diverse experts discussing the topic
- Each expert provides unique perspective based on their background
- Includes realistic debate with agreements and disagreements
- Forms consensus conclusion incorporating multiple viewpoints
- Perfect for:
  - Business decisions
  - Career advice
  - Technical architecture choices
  - Any topic requiring diverse expertise

**Example Expert Panel Workflow:**
```
User: "Should I learn Python or JavaScript first?"

AI:
**Panel Introduction:**
Today's panel features experts in software education:

**Expert 1 - Senior Software Engineer:** "Alex Chen"
"Python for beginners - clean syntax, great for learning concepts..."

**Expert 2 - Web Development Lead:** "Sarah Miller"
"JavaScript gives immediate visual feedback when building websites..."

**Expert 3 - Career Coach:** "Michael Thompson"
"The answer depends on your goals - JS for web, Python for data science..."

**Consensus Conclusion:**
Choose Python for data science/AI, JavaScript for web development...
```

**Important:** The response modes are mutually exclusive - only one can be active at a time. Switching modes will automatically update the chat interface.

### Using Message Copying (NEW in Day 6)

The application provides two ways to copy messages:

#### Copy Individual Messages
1. Locate any message in the chat
2. Click the copy icon (📋) in the bottom-right corner of the message
3. The message content is now in your clipboard
4. Paste anywhere using Ctrl+V (Windows/Linux) or Cmd+V (macOS)

**Use cases:**
- Save interesting AI responses
- Quote specific parts of conversation
- Share individual insights

#### Copy Entire Conversation
1. Click the copy icon (📋) in the top toolbar (next to Settings and Clear buttons)
2. The entire conversation history is copied in formatted text
3. Paste into any text editor or document

**Output format:**
```
User: First question

AI: First response

User: Second question

AI: Second response
```

**Use cases:**
- Archive complete conversations
- Share full context with colleagues
- Analyze conversation patterns
- Create documentation from AI interactions
- Export for further processing

**Note:** The copy buttons are only enabled when there are messages in the chat. System messages are excluded when copying the entire conversation.

### Testing Temperature Parameter (NEW in Day 6)

To experiment with the Temperature parameter and see its effects:

1. Open Settings (⚙️ icon)
2. Adjust the Temperature slider (0.0 - 2.0)
3. Use the test questions from `QUESTIONS.md`:
   - **Temperature 0.0**: Deterministic, consistent answers
   - **Temperature 0.7**: Balanced creativity and reliability
   - **Temperature 1.2**: High creativity and variety
4. Copy responses using the copy buttons for comparison
5. Try asking the same question multiple times at different temperatures

**Recommended workflow:**
1. Set Temperature to 0.0
2. Ask a question from QUESTIONS.md
3. Copy the response
4. Change Temperature to 0.7
5. Ask the same question again
6. Copy and compare responses
7. Repeat with Temperature 1.2
8. Analyze the differences

See `QUESTIONS.md` for 6 curated questions designed to showcase temperature effects.

### Changing AI Model

Go to Settings (⚙️ icon) and select from available models:
- GigaChat (Sberbank)
- Llama 3.2 3B Instruct (HuggingFace)
- Meta Llama 3 70B Instruct (HuggingFace)
- DeepSeek V3 (HuggingFace)

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
- **CLAUDE.md** - Technical documentation for Claude Code integration
- **DIFF_05-06.md** - Detailed changelog between Day 5 and Day 6
- **QUESTIONS.md** - Temperature parameter testing guide with 6 curated questions
- **TEMPERATURE_EXPERIMENT_RESULTS.md** - Real experiment results and analysis 🆕

### 📊 Temperature Experiment Results 🆕🔥

**TWO SERIES of experiments** were conducted, revealing critical insights about GigaChat behavior!

The **TEMPERATURE_EXPERIMENT_RESULTS.md** file contains:
- **18 real experiments** (2 series × 9 experiments each)
- Real responses from GigaChat at different temperatures (0.0, 0.7, 1.2)
- **Critical discovery:** Results vary significantly between sessions!
- Comparative analysis of creativity vs practicality
- Practical recommendations based on actual experiments
- Evidence that **Temperature 0.7 is optimal** for most tasks

**🔥 Critical Findings:**

1. **Temperature 0.0: Conditional Determinism**
   - Series 1: All 3 responses were DIFFERENT
   - Series 2: 2 out of 3 responses were IDENTICAL
   - **Conclusion:** GigaChat CAN be deterministic, but it's not guaranteed

2. **Temperature 1.2: Paradox of High Temperature**
   - High temperature DOESN'T guarantee high creativity
   - Can produce very simple responses ("empty shelves")
   - Unpredictability is higher than expected

3. **Session Variability > Temperature Effect**
   - Responses vary MORE between sessions than within a session
   - Context, time, and model state significantly affect results
   - Multiple requests (3-5) recommended even at low temperatures

**Quick Summary:** See [EXPERIMENT_SUMMARY.md](./EXPERIMENT_SUMMARY.md) for a concise overview of all findings.

## Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- UI powered by [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- AI by [GigaChat](https://developers.sber.ru/portal/products/gigachat)

## Videos

### Day 1 & 2
- https://disk.yandex.ru/i/v697w0dF54mCfA

### Day 6 - Coming Soon
- Message copying demonstration
- Temperature parameter testing walkthrough