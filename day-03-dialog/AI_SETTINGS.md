# AI Settings Configuration Guide

## Overview

The application now includes a comprehensive AI settings system that allows you to customize the GigaChat AI model's behavior in real-time.

## Available Settings

### 1. Structured JSON Response Mode
- **Type**: Toggle switch
- **Default**: OFF
- **Description**: When enabled, AI responds in strict JSON format
- **JSON Structure**:
  ```json
  {
    "question_short": "Brief question summary",
    "response": "Detailed answer",
    "responder_role": "Expert type (e.g., 'Software Engineer')",
    "unicode_symbols": "Relevant emoji (e.g., '💻🔧📱')"
  }
  ```
- **Features**:
  - Automatic JSON detection in messages
  - Toggle button to switch between JSON and formatted view
  - Formatted view displays:
    - Unicode symbols in large font
    - Question summary in italics
    - Detailed answer in italics
    - Expert role in italics
  - System prompt automatically manages AI response format
- **Use Cases**:
  - Structured data extraction
  - Consistent response formatting
  - Rich message display with emojis
  - Role-based expertise identification

### 2. Model
- **Current Value**: GigaChat (default)
- **Available Models**:
  - GigaChat (Sberbank)
  - Llama 3.2 3B Instruct (HuggingFace)
  - Meta Llama 3 70B Instruct (HuggingFace)
  - DeepSeek V3 (HuggingFace)
- **Description**: The AI model used for generating responses

### 3. Temperature
- **Range**: 0.0 - 2.0
- **Default**: 0.7
- **Description**: Controls the randomness of AI responses
  - **Lower values (0.0-0.5)**: More focused and deterministic responses
  - **Medium values (0.5-1.0)**: Balanced creativity and coherence
  - **Higher values (1.0-2.0)**: More creative and random responses
- **Use Cases**:
  - Code generation: 0.2-0.4
  - General conversation: 0.7-0.9
  - Creative writing: 1.0-1.5

### 4. Top P (Nucleus Sampling)
- **Range**: 0.0 - 1.0
- **Default**: 0.9
- **Description**: Controls diversity by limiting token selection
  - **Lower values (0.1-0.5)**: More focused, considers fewer tokens
  - **Medium values (0.5-0.8)**: Balanced diversity
  - **Higher values (0.8-1.0)**: More diverse, considers more tokens
- **Recommendation**: Keep between 0.85-0.95 for best results

### 5. Max Tokens
- **Range**: 1 - 8192
- **Default**: 2048
- **Description**: Maximum number of tokens in the AI's response
  - Includes both input and output tokens
  - Longer responses require higher values
  - Higher values increase API costs
- **Guidelines**:
  - Short answers: 256-512
  - Medium responses: 1024-2048
  - Long responses: 2048-4096

### 6. Repetition Penalty
- **Range**: 0.0 - 2.0
- **Default**: 1.0
- **Description**: Penalizes repeated tokens to reduce repetition
  - **Lower values (0.0-0.8)**: Allows more repetition
  - **Value of 1.0**: No penalty (neutral)
  - **Higher values (1.1-2.0)**: Strong penalty against repetition
- **Use Cases**:
  - Poetry/lyrics: 0.8-1.0 (repetition can be artistic)
  - Technical docs: 1.1-1.3 (avoid redundancy)
  - General chat: 1.0-1.2

## How to Access Settings

1. **Open the app**
2. **Click the Settings icon** (⚙️) in the top-right corner of the chat screen
3. **Adjust sliders** to change parameters
4. **Changes take effect immediately** for the next message
5. **Click Back arrow** (←) to return to chat

## Settings Screen Features

### Real-time Value Display
- Each slider shows the current value
- Values update as you drag the slider
- Format: Float values show 2 decimal places, integers show whole numbers

### Reset to Defaults
- Click the **Refresh icon** (↻) in the top-right corner
- All settings instantly reset to recommended defaults

### Parameter Descriptions
- Each setting includes a description
- Explains what the parameter does
- Helps you understand the effect on AI responses

## Recommended Presets

### Preset 1: Precise (Code/Technical)
```
Temperature: 0.3
Top P: 0.85
Max Tokens: 2048
Repetition Penalty: 1.2
```
Best for: Code generation, technical questions, precise answers

### Preset 2: Balanced (Default)
```
Temperature: 0.7
Top P: 0.9
Max Tokens: 2048
Repetition Penalty: 1.0
```
Best for: General conversation, Q&A, everyday use

### Preset 3: Creative
```
Temperature: 1.2
Top P: 0.95
Max Tokens: 4096
Repetition Penalty: 0.9
```
Best for: Creative writing, brainstorming, storytelling

### Preset 4: Factual
```
Temperature: 0.4
Top P: 0.8
Max Tokens: 1024
Repetition Penalty: 1.3
```
Best for: Factual information, summaries, concise answers

## Technical Implementation

### Architecture
- **AiSettings**: Data class holding all AI parameters
- **SettingsRepository**: Manages settings state with Kotlin Flow
- **SettingsScreen**: Compose UI for adjusting parameters
- **ChatRepository**: Applies settings when sending messages to API

### Validation
- All settings are automatically validated and clamped to valid ranges
- Invalid values are corrected before sending to API
- Ensures API compatibility and prevents errors

### Persistence
- Currently in-memory only (resets on app restart)
- Future: Can add persistent storage with DataStore or similar

## API Integration

Settings are sent to GigaChat API as part of the chat completion request:

```kotlin
{
  "model": "GigaChat",
  "messages": [...],
  "temperature": 0.7,
  "top_p": 0.9,
  "max_tokens": 2048,
  "repetition_penalty": 1.0
}
```

## Troubleshooting

### Settings Don't Seem to Work
1. Ensure you've sent a **new message** after changing settings
2. Old messages in the conversation use their original settings
3. Settings only affect **future messages**

### API Errors After Changing Settings
1. Click the **Reset button** (↻) to restore defaults
2. Check that values are within valid ranges (they should auto-validate)
3. Try reducing Max Tokens if you get "token limit" errors

### Responses Too Short
- Increase **Max Tokens** (try 2048 or higher)
- Decrease **Repetition Penalty** slightly

### Responses Too Random/Inconsistent
- Decrease **Temperature** (try 0.5-0.7)
- Decrease **Top P** (try 0.8-0.9)

### Responses Too Repetitive
- Increase **Repetition Penalty** (try 1.2-1.5)
- Increase **Temperature** slightly (try 0.8-1.0)

## Best Practices

1. **Start with defaults** and adjust incrementally
2. **Change one parameter at a time** to understand its effect
3. **Test with sample prompts** before important use
4. **Save mental notes** of settings that work well for specific tasks
5. **Use lower temperature** for factual/technical content
6. **Use higher temperature** for creative/brainstorming tasks

## Future Enhancements

Potential additions to settings system:
- [ ] Persistent storage of settings across app restarts
- [ ] Settings presets (save/load custom configurations)
- [ ] Per-conversation settings
- [ ] Advanced parameters (frequency_penalty, presence_penalty)
- [ ] Model selection (when multiple models available)
- [ ] Import/export settings as JSON
- [ ] Settings profiles for different use cases

## References

- [GigaChat API Documentation](https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/gigachat-api)
- [OpenAI API Parameters Guide](https://platform.openai.com/docs/api-reference/chat/create) (similar concepts)
- [Understanding Temperature and Top P](https://docs.cohere.com/docs/temperature)
