# Visual Guide - Copy Feature

## UI Layout

### Before (Old Layout)
```
┌─────────────────────────────────────────┐
│ AI Agent · 13 tools     [🔄][🗑️][⚙️] │ ← Toolbar
├─────────────────────────────────────────┤
│                                         │
│ > User message                          │ ← No copy button
│                                         │
│ AI response                             │ ← No copy button
│   342ms · 156 tokens (↑42 ↓114)        │
│                                         │
│ [tool] docker_ps                        │ ← No copy button
│                                         │
│ [result] Container list                 │ ← No copy button
│                                         │
└─────────────────────────────────────────┘
```

### After (New Layout)
```
┌─────────────────────────────────────────┐
│ AI Agent · 13 tools [🔄][📋][🗑️][⚙️] │ ← Toolbar + Copy All
├─────────────────────────────────────────┤
│                                         │
│ > User message                     [📋] │ ← Copy button
│                                         │
│ AI response                        [📋] │ ← Copy button
│   342ms · 156 tokens (↑42 ↓114)        │
│                                         │
│ [tool] docker_ps                   [📋] │ ← Copy button
│                                         │
│ [result] Container list            [📋] │ ← Copy button
│                                         │
└─────────────────────────────────────────┘
```

## Button Details

### Individual Copy Button
```
┌────────────────────────────────────┐
│ [Text Message Content]        [📋] │
│   metadata                         │
└────────────────────────────────────┘
     ↑                            ↑
     └─ Message text          Copy icon
                              (28dp button,
                               14dp icon,
                               gray color)
```

### Copy All Button (Toolbar)
```
Toolbar Layout:
┌─────────────────────────────────────────┐
│ AI Agent · 13 tools                     │
│                                         │
│     [🔄]    [📋]    [🗑️]    [⚙️]      │
│   Reload  CopyAll  Clear  Settings     │
│   (blue)  (green)  (red)   (gray)      │
└─────────────────────────────────────────┘

States:
  [📋] Green (#A9DC76)  ← When messages exist
  [📋] Gray  (#404040)  ← When no messages (disabled)
```

## Color Scheme

### Individual Copy Buttons
```
Normal State:
  Icon: #606060 (Gray)
  Size: 14dp

Hover (Desktop):
  Icon: Same gray
  Background: Subtle highlight (system default)
```

### Copy All Button
```
Enabled State (has messages):
  Icon: #A9DC76 (Green)
  Size: 18dp
  Label: "Copy all"

Disabled State (no messages):
  Icon: #404040 (Dark Gray)
  Size: 18dp
  Label: "Copy all"
```

## Message Layout Comparison

### Old Layout (Column only)
```
Column {
  Text(prefix + content)
  Metadata
}
```

### New Layout (Row with Column + Button)
```
Row {
  Column(weight=1f) {    ← Takes available space
    Text(prefix + content)
    Metadata
  }
  IconButton {           ← Fixed size (28dp)
    Icon(ContentCopy)
  }
}
```

## Copy Format Examples

### Copy Single Message

**User Message**:
```
Input:  User types "Show me Docker containers"
Display: > Show me Docker containers                   [📋]
Click:  Copies "> Show me Docker containers"
```

**AI Response**:
```
Input:  AI responds "Here are your containers..."
Display: Here are your containers...                   [📋]
         2.1s · 234 tokens (↑56 ↓178)
Click:  Copies "Here are your containers..."
```

**Tool Call**:
```
Input:  AI calls docker_ps
Display: [tool] docker_ps                              [📋]
Click:  Copies "[tool] docker_ps"
```

**Tool Result**:
```
Input:  Result: "CONTAINER ID  NAMES..."
Display: [result] CONTAINER ID  NAMES...               [📋]
Click:  Copies "[result] CONTAINER ID  NAMES..."
```

### Copy All Messages

**Full Conversation**:
```
Messages:
  1. > Show me Docker containers
  2. I'll check your containers...
  3. [tool] docker_ps: {...}
  4. [result] CONTAINER ID  NAMES...
  5. You have 3 containers running...

Click Copy All [📋]:

Copied Text:
> Show me Docker containers

I'll check your containers...

[tool] docker_ps: {...}

[result] CONTAINER ID  NAMES...

You have 3 containers running...
```

## User Flow Diagrams

### Flow 1: Copy Single Message
```
User sees message
       ↓
Clicks copy icon [📋]
       ↓
ClipboardManager.copyToClipboard(text)
       ↓
Text copied to system clipboard
       ↓
User can paste (Ctrl+V / Cmd+V)
```

### Flow 2: Copy All Messages
```
User has conversation
       ↓
Clicks Copy All [📋] in toolbar
       ↓
Format all messages with prefixes
       ↓
Join with double newlines
       ↓
ClipboardManager.copyToClipboard(allText)
       ↓
Full conversation in clipboard
       ↓
User can paste anywhere
```

## Responsive Design

### Mobile (Android)
```
┌─────────────────────────────┐
│ AI Agent · 13 tools         │
│             [🔄][📋][🗑️][⚙️]│
├─────────────────────────────┤
│                             │
│ > Message            [📋]   │ ← Touch target
│                             │
│ Response             [📋]   │ ← Touch target
│                             │
└─────────────────────────────┘

Touch Targets:
  - 28dp minimum (follows Material Design)
  - Adequate spacing for fingers
  - No accidental clicks
```

### Desktop (Wide Screen)
```
┌──────────────────────────────────────────┐
│ AI Agent · 13 tools      [🔄][📋][🗑️][⚙️]│
├──────────────────────────────────────────┤
│                                          │
│ > Message                          [📋]  │ ← Hover effect
│                                          │
│ Response                           [📋]  │ ← Hover effect
│   metadata                               │
│                                          │
└──────────────────────────────────────────┘

Mouse Interactions:
  - Hover shows button highlight
  - Click copies instantly
  - Cursor changes to pointer
```

## Accessibility

### Icon Descriptions
```
Individual Copy:
  contentDescription = "Copy message"

Copy All:
  contentDescription = "Copy all"
```

### Keyboard Navigation
```
Current: Mouse/Touch only
Future: Tab navigation through buttons
        Enter/Space to activate
```

## Platform Differences

### Android
```
┌─────────────────────────┐
│ [📋] Click              │
│      ↓                  │
│ ClipboardManager        │
│      ↓                  │
│ Android System          │
│      ↓                  │
│ Toast: "Copied"         │ ← System may show
└─────────────────────────┘
```

### Desktop
```
┌─────────────────────────┐
│ [📋] Click              │
│      ↓                  │
│ ClipboardManager        │
│      ↓                  │
│ AWT Toolkit             │
│      ↓                  │
│ Silent copy             │ ← No toast
└─────────────────────────┘
```

## Size Reference

### Button Sizes
```
Individual Copy Button:
  Button: 28dp × 28dp
  Icon:   14dp × 14dp

Copy All Button (Toolbar):
  Button: 32dp × 32dp
  Icon:   18dp × 18dp

Other Toolbar Buttons:
  Button: 32dp × 32dp
  Icon:   18dp × 18dp
```

### Spacing
```
Toolbar:
  horizontalArrangement = spacedBy(4.dp)

Message Row:
  padding(start=12.dp, end=6.dp, top=2.dp, bottom=variable)
```

## Color Palette

```
Copy Button Colors:
  Individual:  #606060  (Gray)
  Copy All On: #A9DC76  (Green)
  Copy All Off:#404040  (Dark Gray)

Background:
  Screen:      #1E1E1E  (Dark)
  Toolbar:     #252525  (Lighter Dark)

Text:
  User:        #6CB6FF  (Blue)
  AI:          #E6E6E6  (Light Gray)
  Tool:        #FFD866  (Yellow)
  Result:      #A9DC76  (Green)
  Error:       #FF6188  (Red)
```

## Animation (Future)

### Potential Enhancements
```
Copy Button Click:
  1. Scale down (0.95x)
  2. Scale up (1.0x)
  3. Duration: 100ms

Copy Confirmation:
  1. Show "✓ Copied" toast
  2. Fade in: 200ms
  3. Show: 2000ms
  4. Fade out: 200ms
```

## Testing Checklist

### Visual Testing
```
□ Copy icon appears on all messages
□ Copy All button in toolbar
□ Correct colors (green/gray)
□ Proper alignment
□ No layout shifts
□ Icons sized correctly
□ Touch targets adequate
```

### Functional Testing
```
□ Click copies user message (with >)
□ Click copies AI message (no prefix)
□ Click copies tool call (with [tool])
□ Click copies result (with [result])
□ Copy All formats correctly
□ Copy All disabled when empty
□ Paste works in external apps
```

### Platform Testing
```
□ Works on Android
□ Works on Desktop
□ Clipboard manager functions
□ No crashes
□ No memory leaks
```

---

## Summary

**Visual Changes**:
- ✅ Copy icon on every message
- ✅ Copy All button in toolbar
- ✅ Color-coded states
- ✅ Proper spacing
- ✅ Touch-friendly sizes

**User Experience**:
- ✅ Intuitive placement
- ✅ Clear visual feedback
- ✅ Consistent with app style
- ✅ Accessible
- ✅ Cross-platform

**Quality**:
- ✅ Follows Material Design
- ✅ Matches existing UI
- ✅ Clean implementation
- ✅ Well documented
