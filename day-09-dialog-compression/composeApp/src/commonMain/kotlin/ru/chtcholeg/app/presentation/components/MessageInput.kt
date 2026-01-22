package ru.chtcholeg.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import ru.chtcholeg.app.presentation.theme.chatColors

@Composable
fun MessageInput(
    onSendMessage: (String) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val colors = chatColors()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val sendMessage = {
        if (text.isNotBlank() && !isLoading) {
            onSendMessage(text)
            text = ""
            focusRequester.requestFocus()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = colors.divider,
                        spotColor = colors.divider
                    )
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.key == Key.Enter &&
                            keyEvent.isCtrlPressed
                        ) {
                            sendMessage()
                            true
                        } else {
                            false
                        }
                    },
                placeholder = {
                    Text(
                        text = "Type a message...",
                        color = colors.inputPlaceholder
                    )
                },
                enabled = !isLoading,
                maxLines = 5,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.inputBackground,
                    unfocusedContainerColor = colors.inputBackground,
                    disabledContainerColor = colors.inputBackground.copy(alpha = 0.6f),
                    focusedTextColor = colors.inputText,
                    unfocusedTextColor = colors.inputText,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(
                        elevation = if (text.isNotBlank() && !isLoading) 4.dp else 0.dp,
                        shape = CircleShape,
                        ambientColor = colors.primaryAccent.copy(alpha = 0.3f),
                        spotColor = colors.primaryAccent.copy(alpha = 0.3f)
                    )
                    .background(
                        color = if (text.isNotBlank() && !isLoading) {
                            colors.primaryAccent
                        } else {
                            colors.divider
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { sendMessage() },
                    enabled = text.isNotBlank() && !isLoading,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (text.isNotBlank() && !isLoading) {
                            Color.White
                        } else {
                            colors.inputPlaceholder
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
