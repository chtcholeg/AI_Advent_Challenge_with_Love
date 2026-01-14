# Dialog Mode - Interactive Requirement Gathering

## Overview

Dialog Mode is a new feature introduced in Day 3 that transforms the AI into an intelligent requirements analyst. Instead of providing immediate responses, the AI engages in a structured conversation to gather all necessary information before delivering a comprehensive result.

## What is Dialog Mode?

Dialog Mode implements a conversational approach to requirement gathering where:
- The AI acts as a professional requirements analyst
- Questions are asked one at a time in a focused manner
- Context builds progressively through the dialogue
- The AI collects complete information before providing the final deliverable
- The conversation feels natural and professional

## Use Cases

Dialog Mode is particularly useful for:

### 1. Technical Specifications
**Scenario:** "I need a technical specification for a mobile app"

The AI will systematically gather:
- Project type and platform requirements
- Core features and functionality
- Target audience and user personas
- Technical constraints and preferences
- Integration requirements
- Security and compliance needs

### 2. Project Requirements
**Scenario:** "Help me define requirements for a new e-commerce feature"

The AI will ask about:
- Business objectives
- User workflows
- Data requirements
- Performance expectations
- Success metrics

### 3. System Architecture
**Scenario:** "I want to design a microservices architecture"

The AI will explore:
- Current system constraints
- Scalability requirements
- Technology preferences
- Team expertise
- Budget and timeline considerations

### 4. Documentation Planning
**Scenario:** "Create a user manual structure"

The AI will inquire about:
- Target audience technical level
- Product features to document
- Existing documentation
- Preferred format and style
- Maintenance requirements

## How It Works

### System Prompt Design

The Dialog Mode uses a specialized system prompt (`DIALOG_SYSTEM_PROMPT` in `AiSettings.kt`) that **strictly enforces** the AI to:

1. **Acknowledge the request** - Confirm understanding of the user's goal
2. **Ask EXACTLY ONE question at a time** - Never multiple questions in the same response
3. **Wait for answers** - Each question is followed by waiting for the user's response
4. **Build context progressively** - Use previous answers to determine the next most important question
5. **Recognize completion** - Identify when all necessary information has been gathered
6. **Deliver comprehensive results** - Provide a well-structured final deliverable

**Enhanced Enforcement in System Prompt:**
- Explicit instruction: "Ask EXACTLY ONE question per response - NEVER ask multiple questions"
- Forbidden patterns clearly defined (e.g., "What is X? What is Y?" is not allowed)
- Correct patterns demonstrated (only one question mark per response)
- Examples show proper one-question-at-a-time flow

### Conversation Flow Example

```
User: "I need a technical specification"

AI: "I'll help you create a technical specification. Let me gather the
     necessary details. First, what type of project is this for? (e.g.,
     mobile app, web application, API service, etc.)"

User: "Mobile app"

AI: "Great! What is the main purpose or goal of this mobile app?"

User: "Online shopping"

AI: "Excellent. Which platforms should the app support? (iOS, Android, or both?)"

User: "Both iOS and Android"

AI: "Perfect. What are the core features you want to include in the shopping app?"

User: "Product browsing, cart, checkout, user accounts"

AI: "Thank you. What payment methods should the app support?"

User: "Credit cards and PayPal"

... [dialogue continues] ...

AI: "Thank you for providing all the details! I now have everything needed.
     Here is your complete technical specification:

     # Mobile Shopping App - Technical Specification

     ## Project Overview
     - Platform: Cross-platform (iOS & Android)
     - Type: Mobile Application
     - Purpose: Online shopping platform

     ## Core Features
     1. Product Browsing
        - Category navigation
        - Search functionality
        - Product details view

     2. Shopping Cart
        - Add/remove items
        - Quantity adjustment
        - Price calculation

     [... detailed specification continues ...]"
```

## Implementation Details

### Code Components

1. **ResponseMode.kt** (`domain/model/`) - NEW
   - Enum defining three modes: NORMAL, STRUCTURED_JSON, DIALOG
   - Each mode has displayName and description
   - Mutually exclusive by design

2. **AiSettings.kt** (`domain/model/`)
   - Changed from two boolean fields to single `responseMode: ResponseMode` field
   - Defined enhanced `DIALOG_SYSTEM_PROMPT` with strict one-question-at-a-time enforcement
   - System prompt includes forbidden and correct patterns

3. **ChatRepositoryImpl.kt** (`data/repository/`)
   - Updated `sendMessage()` to use `when (responseMode)` instead of boolean checks
   - Clean mode selection: Dialog → DIALOG_SYSTEM_PROMPT, JSON → custom prompt, Normal → null
   - Clears conversation history when mode changes

4. **ChatStore.kt** (`presentation/chat/`)
   - Updated to track `currentResponseMode` instead of boolean
   - Different system messages for each mode
   - Filters and updates messages when mode changes

5. **SettingsScreen.kt** (`presentation/settings/`)
   - Replaced two switches with `ResponseModeSelector` dropdown component
   - Uses ExposedDropdownMenuBox for clean UI
   - Shows mode-specific descriptions dynamically
   - Ensures mutual exclusivity automatically

### System Prompt Strategy

The dialog system prompt includes:

- **Role Definition**: Establishes AI as a requirements analyst
- **Critical Rules**: Emphasizes one question at a time, context building
- **Process Guidelines**: Step-by-step approach to information gathering
- **Example Workflow**: Demonstrates expected conversation pattern
- **Completion Indicators**: Teaches AI when to provide final result

## Usage Instructions

### Enabling Dialog Mode

1. Open the application
2. Click the Settings icon (⚙️)
3. Find the "Response Mode" dropdown selector
4. Select "Dialog Mode" from the dropdown menu
5. A system message confirms activation in the chat
6. Return to chat and start your high-level request

### Best Practices

**For Users:**
- Start with a clear high-level goal
- Answer questions directly and concisely
- Provide specific details when asked
- Let the AI drive the conversation flow
- Wait for the AI to indicate completion

**For System Administrators:**
- Response modes (Normal, JSON, Dialog) are mutually exclusive through enum design
- No possibility of conflicting states or both modes active simultaneously
- Conversation history is automatically cleared when switching modes
- The selected mode persists across sessions via settings repository
- Works with all supported AI models (GigaChat, HuggingFace)
- UI enforces selection through dropdown menu (not toggles)

### Limitations

- Only one response mode can be active at a time (enforced by dropdown UI)
- Requires AI model with good instruction following capabilities
- Longer conversations in Dialog Mode consume more tokens than direct responses
- Some AI models may occasionally ask multiple questions despite strict prompting
- Question quality depends on the underlying AI model's capability

## Technical Architecture

### Mode Selection Logic

```kotlin
val systemPrompt = when (settings.responseMode) {
    ResponseMode.DIALOG -> AiSettings.DIALOG_SYSTEM_PROMPT
    ResponseMode.STRUCTURED_JSON -> settings.systemPrompt
    ResponseMode.NORMAL -> null
}
```

This ensures:
- Clean, explicit mode selection using enum
- Mutually exclusive by design (no conflicting states possible)
- Compiler-checked exhaustive when expression
- Easy to add new modes in the future

### State Management

- Settings stored in `SettingsRepository` (in-memory StateFlow)
- UI automatically updates via Compose state collection
- ChatRepository reacts to settings changes
- Conversation history cleared on mode switch to ensure clean context

## Future Enhancements

Potential improvements for Dialog Mode:

1. **Multi-turn planning** - Allow AI to plan entire conversation tree upfront
2. **Progress indicators** - Show user how many questions remain
3. **Question categories** - Group related questions for better UX
4. **Resume capability** - Save and resume incomplete dialogues
5. **Custom templates** - Pre-defined question flows for common use cases
6. **Export functionality** - Save dialogue transcript alongside final result

## Conclusion

Dialog Mode represents a significant enhancement to the application, enabling more sophisticated use cases that require detailed requirement gathering. By structuring the AI's approach to information collection, users can receive more comprehensive and tailored results for complex tasks like technical specifications and project planning.
