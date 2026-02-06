package ru.chtcholeg.agent.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Console-style input field with `>` prompt.
 */
@Composable
fun MessageInput(
    onSendMessage: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var message by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Prompt symbol
        Text(
            text = "> ",
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = Color(0xFF6CB6FF),
            modifier = Modifier.align(Alignment.Top)
        )

        // Input field (multiline, max 3 lines)
        BasicTextField(
            value = message,
            onValueChange = { message = it },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    // Enter sends message, Shift+Enter adds new line
                    if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                        if (event.isShiftPressed) {
                            // Allow default behavior (new line)
                            false
                        } else {
                            if (message.isNotBlank()) {
                                onSendMessage(message)
                                message = ""
                            }
                            true
                        }
                    } else {
                        false
                    }
                },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Color(0xFFE6E6E6)
            ),
            cursorBrush = SolidColor(Color(0xFF6CB6FF)),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (message.isNotBlank()) {
                        onSendMessage(message)
                        message = ""
                    }
                }
            ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.TopStart) {
                    if (message.isEmpty()) {
                        Text(
                            text = "Type your message... (Shift+Enter for new line)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color(0xFF606060),
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
