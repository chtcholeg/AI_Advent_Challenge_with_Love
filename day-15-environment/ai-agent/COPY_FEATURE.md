# Copy Feature for AI Agent

Added clipboard functionality to copy messages in AI Agent application.

## Features Added

### 1. Copy Individual Messages
Each message now has a copy button (📋 icon) on the right side.

**Location**: Right side of every message
**Icon**: ContentCopy (📋)
**Color**: Gray (#606060)
**Action**: Copies message text to clipboard

**Usage**:
- Click the copy icon next to any message
- Message content is copied including prefix (>, [tool], etc.)

### 2. Copy All Messages
New button in the top toolbar to copy entire conversation.

**Location**: Toolbar, between "Reload" and "Clear" buttons
**Icon**: ContentCopy (📋)
**Color**: Green (#A9DC76) when enabled, Gray (#404040) when disabled
**State**: Enabled only when messages exist

**Format**:
```
> User message

AI response

[tool] Tool call details

[result] Tool result

> Next user message
...
```

## Implementation Details

### Files Modified

1. **MessageItem.kt**
   - Changed from `Column` to `Row` layout
   - Added `IconButton` with `ContentCopy` icon
   - Wrapped text content in `Column` with `weight(1f)`
   - Copy button size: 28dp, icon: 14dp

2. **AgentScreen.kt**
   - Added import for `Icons.Default.ContentCopy`
   - Added "Copy All" button in toolbar
   - Button appears between "Reload tools" and "Clear chat"
   - Formats all messages with prefixes before copying

### UI Layout Changes

#### Before (MessageItem):
```
[Prefix] Message text
  Metadata
```

#### After (MessageItem):
```
[Row]
  [Column - weight(1f)]
    [Prefix] Message text
      Metadata
  [Copy Button]
```

#### AgentScreen Toolbar:
```
Old: [Reload] [Clear] [Settings]
New: [Reload] [Copy All] [Clear] [Settings]
```

## User Experience

### Copy Single Message
1. User sees message in chat
2. Small copy icon (📋) appears on the right
3. Click icon → message copied to clipboard
4. Can paste anywhere (Ctrl+V / Cmd+V)

### Copy All Messages
1. User has conversation with AI
2. Green copy icon appears in toolbar (when messages exist)
3. Click icon → entire conversation copied
4. Format preserves prefixes and structure
5. Can paste into text editor, docs, etc.

## Technical Details

### ClipboardManager
- Already implemented using expect/actual pattern
- Android: Uses `android.content.ClipboardManager`
- Desktop: Uses `java.awt.Toolkit.getDefaultToolkit().systemClipboard`
- Both platforms supported

### Message Prefix Mapping
```kotlin
USER         → "> "
AI           → ""
TOOL_CALL    → "[tool] "
TOOL_RESULT  → "[result] "
SYSTEM       → "[system] "
ERROR        → "[error] "
```

### Copy All Format
Messages joined with double newline (`\n\n`) for readability:
```
> First user message

AI response text

[tool] Function call

[result] Function result

> Second user message

AI response
```

## Color Scheme

### Individual Copy Button
- Idle: `#606060` (gray)
- Size: 28dp button, 14dp icon
- Minimal visual impact

### Copy All Button
- Enabled: `#A9DC76` (green)
- Disabled: `#404040` (dark gray)
- Size: 32dp button, 18dp icon
- Matches other toolbar buttons

## Benefits

1. **Quick Reference**: Copy AI responses for documentation
2. **Share Conversations**: Send full chat to colleagues
3. **Error Reporting**: Copy error messages for debugging
4. **Tool Results**: Copy function outputs for analysis
5. **Code Snippets**: Copy AI-generated code easily

## Usage Scenarios

### Scenario 1: Copy AI Code
```
User: "Write a hello world in Kotlin"
AI: [generates code]
→ Click copy icon → Paste in IDE
```

### Scenario 2: Share Debugging Session
```
User: Has conversation debugging issue
→ Click "Copy All"
→ Paste in bug report / email
```

### Scenario 3: Save Tool Results
```
AI: Calls docker_ps tool
Result: Container list
→ Click copy on result message
→ Save for later reference
```

### Scenario 4: Document AI Interaction
```
User: Long conversation with AI
→ Click "Copy All"
→ Paste in documentation
→ Shows full decision process
```

## Keyboard Shortcuts (Future)

Potential future enhancements:
- `Ctrl/Cmd + C` on selected message
- `Ctrl/Cmd + Shift + C` for copy all
- `Ctrl/Cmd + K` to copy last AI response

## Testing

### Manual Testing Steps

1. **Test Individual Copy**:
   - Start chat
   - Send message
   - Click copy icon on user message
   - Paste → Verify "> " prefix included
   - Click copy on AI response
   - Paste → Verify no prefix, just text

2. **Test Copy All**:
   - Have conversation (3+ messages)
   - Click green copy icon in toolbar
   - Paste → Verify format with prefixes
   - Check double newlines between messages

3. **Test Empty State**:
   - Clear chat
   - Verify "Copy All" button is disabled (gray)
   - Verify individual copy icons exist on messages

4. **Test Error Messages**:
   - Trigger error
   - Copy error message
   - Verify "[error] " prefix included

5. **Test Tool Messages**:
   - Trigger tool call
   - Copy tool call message
   - Verify "[tool] " prefix
   - Copy result
   - Verify "[result] " prefix

## Platform-Specific Notes

### Android
- Copy buttons work with touch
- System toast may appear: "Copied to clipboard"
- Works with Android clipboard manager

### Desktop
- Copy buttons work with mouse click
- Silent copy (no toast)
- Works with system clipboard
- Can paste into any application

## Future Enhancements

1. **Visual Feedback**: Show "Copied!" toast/snackbar
2. **Selective Copy**: Checkbox to select multiple messages
3. **Copy as Markdown**: Format with markdown syntax
4. **Copy as JSON**: Export in structured format
5. **Copy with Metadata**: Include timestamps, tokens
6. **Rich Text Copy**: Preserve syntax highlighting

## Compatibility

- ✅ Android: Fully supported
- ✅ Desktop: Fully supported
- ✅ Works in both light/dark modes
- ✅ Accessible (icon buttons with descriptions)
- ✅ Keyboard navigation compatible

## Code Quality

- Clean separation of concerns
- Reuses existing ClipboardManager
- Minimal changes to existing code
- No breaking changes
- Backward compatible

## Performance

- Copy operation is instant (O(n) for "Copy All")
- No memory leaks
- Icons cached by Compose
- Minimal layout impact

## Conclusion

The copy feature adds essential functionality for:
- Developer workflows
- Documentation
- Debugging
- Sharing
- Reference

Implementation is clean, performant, and follows existing patterns in the codebase.

---

**Implementation Date**: Day 15
**Lines Changed**: ~50 lines across 2 files
**New Dependencies**: None (reuses existing ClipboardManager)
**Status**: ✅ Complete and tested
