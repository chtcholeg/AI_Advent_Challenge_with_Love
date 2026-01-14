package ru.chtcholeg.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.chtcholeg.app.domain.model.ChatMessage
import ru.chtcholeg.app.domain.model.MessageType
import ru.chtcholeg.app.domain.model.StructuredResponse
import ru.chtcholeg.app.presentation.theme.ChatColors

@Composable
fun MessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    // System messages are displayed centered
    val arrangement = when (message.messageType) {
        MessageType.SYSTEM -> Arrangement.Center
        MessageType.USER -> Arrangement.End
        MessageType.AI -> Arrangement.Start
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = arrangement
    ) {
        BoxWithConstraints {
            Box(
                modifier = Modifier
                    .widthIn(max = if (message.messageType == MessageType.SYSTEM) maxWidth * 0.9f else maxWidth * 0.75f)
                    .background(
                        color = when (message.messageType) {
                            MessageType.USER -> ChatColors.UserBubbleBackground
                            MessageType.AI -> ChatColors.AiBubbleBackground
                            MessageType.SYSTEM -> ChatColors.SystemBubbleBackground
                        },
                        shape = when (message.messageType) {
                            MessageType.SYSTEM -> RoundedCornerShape(16.dp)
                            MessageType.USER -> RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 4.dp
                            )
                            MessageType.AI -> RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 4.dp,
                                bottomEnd = 16.dp
                            )
                        }
                    )
                    .padding(12.dp)
            ) {
                val alignment = when (message.messageType) {
                    MessageType.USER -> Alignment.CenterEnd
                    MessageType.AI -> Alignment.CenterStart
                    MessageType.SYSTEM -> Alignment.Center
                }

                Column(modifier = Modifier.align(alignment)) {
                    // Check if this is a structured JSON response
                    if (message.messageType == MessageType.AI && StructuredResponse.looksLikeStructuredResponse(message.content)) {
                        StructuredMessageContent(message = message)
                    } else {
                        // Regular message display
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (message.messageType) {
                                MessageType.USER -> ChatColors.UserBubbleText
                                MessageType.AI -> ChatColors.AiBubbleText
                                MessageType.SYSTEM -> ChatColors.SystemBubbleText
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (message.messageType) {
                            MessageType.USER -> ChatColors.UserBubbleTimestamp
                            MessageType.AI -> ChatColors.AiBubbleTimestamp
                            MessageType.SYSTEM -> ChatColors.SystemBubbleTimestamp
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StructuredMessageContent(message: ChatMessage) {
    var showFormatted by remember { mutableStateOf(false) }
    val structuredResponse = remember(message.content, showFormatted) {
        if (showFormatted) StructuredResponse.tryParse(message.content) else null
    }

    Column {
        // Toggle button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showFormatted) "Formatted View" else "JSON View",
                style = MaterialTheme.typography.labelMedium,
                color = ChatColors.AiBubbleText.copy(alpha = 0.7f)
            )
            TextButton(
                onClick = { showFormatted = !showFormatted }
            ) {
                Text(
                    text = if (showFormatted) "Show JSON" else "Format",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChatColors.AiBubbleText
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content display
        if (showFormatted && structuredResponse != null) {
            // Formatted view
            Column(modifier = Modifier.fillMaxWidth()) {
                // Unicode symbols
                Text(
                    text = structuredResponse.unicodeSymbols,
                    style = MaterialTheme.typography.headlineMedium,
                    color = ChatColors.AiBubbleText
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Question short
                Row {
                    Text(
                        text = "Вопрос коротко: ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChatColors.AiBubbleText
                    )
                    Text(
                        text = structuredResponse.questionShort,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = ChatColors.AiBubbleText
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Response
                Column {
                    Text(
                        text = "Ответ:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChatColors.AiBubbleText
                    )
                    Text(
                        text = structuredResponse.response,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = ChatColors.AiBubbleText
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Responder role
                Row {
                    Text(
                        text = "Ответил на него: ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChatColors.AiBubbleText
                    )
                    Text(
                        text = structuredResponse.responderRole,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = ChatColors.AiBubbleText
                    )
                }
            }
        } else {
            // JSON view (or failed to parse)
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = ChatColors.AiBubbleText
            )

            if (showFormatted && structuredResponse == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Failed to parse JSON",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChatColors.AiBubbleText.copy(alpha = 0.5f),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
}
