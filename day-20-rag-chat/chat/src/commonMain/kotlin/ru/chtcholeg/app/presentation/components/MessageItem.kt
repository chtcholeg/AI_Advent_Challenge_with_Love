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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.chtcholeg.app.domain.model.ChatMessage
import ru.chtcholeg.app.domain.model.MessageType
import ru.chtcholeg.app.domain.model.StructuredResponse
import ru.chtcholeg.app.domain.model.StructuredXmlResponse
import ru.chtcholeg.app.presentation.theme.chatColors

@Composable
fun MessageItem(
    message: ChatMessage,
    onCopyMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = chatColors()

    val arrangement = when (message.messageType) {
        MessageType.SYSTEM, MessageType.REMINDER -> Arrangement.Center
        MessageType.USER -> Arrangement.End
        MessageType.AI -> Arrangement.Start
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = arrangement
    ) {
        BoxWithConstraints {
            val bubbleShape = when (message.messageType) {
                MessageType.SYSTEM, MessageType.REMINDER -> RoundedCornerShape(24.dp)
                MessageType.USER -> RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 6.dp
                )
                MessageType.AI -> RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = 6.dp,
                    bottomEnd = 24.dp
                )
            }

            val bubbleColor = when (message.messageType) {
                MessageType.USER -> colors.userBubbleBackground
                MessageType.AI -> colors.aiBubbleBackground
                MessageType.SYSTEM -> colors.systemBubbleBackground
                MessageType.REMINDER -> colors.systemBubbleBackground
            }

            val textColor = when (message.messageType) {
                MessageType.USER -> colors.userBubbleText
                MessageType.AI -> colors.aiBubbleText
                MessageType.SYSTEM -> colors.systemBubbleText
                MessageType.REMINDER -> colors.headerText
            }

            val timestampColor = when (message.messageType) {
                MessageType.USER -> colors.userBubbleTimestamp
                MessageType.AI -> colors.aiBubbleTimestamp
                MessageType.SYSTEM -> colors.systemBubbleTimestamp
                MessageType.REMINDER -> colors.headerText.copy(alpha = 0.5f)
            }

            Box(
                modifier = Modifier
                    .widthIn(max = if (message.messageType == MessageType.SYSTEM || message.messageType == MessageType.REMINDER) maxWidth * 0.9f else maxWidth * 0.8f)
                    .shadow(
                        elevation = 2.dp,
                        shape = bubbleShape,
                        ambientColor = colors.divider,
                        spotColor = colors.divider
                    )
                    .background(
                        color = bubbleColor,
                        shape = bubbleShape
                    )
                    .padding(16.dp)
            ) {
                val alignment = when (message.messageType) {
                    MessageType.USER -> Alignment.CenterEnd
                    MessageType.AI -> Alignment.CenterStart
                    MessageType.SYSTEM, MessageType.REMINDER -> Alignment.Center
                }

                Column(modifier = Modifier.align(alignment)) {
                    // Reminder header with channel label
                    if (message.messageType == MessageType.REMINDER) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "\uD83D\uDD14 Автоматический саммари",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryAccent
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    val isJsonResponse = message.messageType == MessageType.AI &&
                        StructuredResponse.looksLikeStructuredResponse(message.content)
                    val isXmlResponse = message.messageType == MessageType.AI &&
                        StructuredXmlResponse.looksLikeStructuredXmlResponse(message.content)

                    when {
                        isJsonResponse -> StructuredJsonMessageContent(
                            message = message,
                            textColor = textColor,
                            accentColor = colors.primaryAccent
                        )
                        isXmlResponse -> StructuredXmlMessageContent(
                            message = message,
                            textColor = textColor,
                            accentColor = colors.primaryAccent
                        )
                        else -> {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (message.messageType == MessageType.AI && (message.executionTimeMs != null || message.totalTokens != null)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            message.executionTimeMs?.let { timeMs ->
                                Text(
                                    text = formatExecutionTime(timeMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = timestampColor
                                )
                            }

                            if (message.executionTimeMs != null && message.totalTokens != null) {
                                Text(
                                    text = " | ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = timestampColor
                                )
                            }

                            message.totalTokens?.let { total ->
                                val tokensText = buildString {
                                    append("$total tokens")
                                    if (message.promptTokens != null && message.completionTokens != null) {
                                        append(" (↑${message.promptTokens} + ↓${message.completionTokens})")
                                    }
                                }
                                Text(
                                    text = tokensText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = timestampColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimestamp(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = timestampColor
                        )

                        IconButton(
                            onClick = { onCopyMessage(message.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Copy message",
                                modifier = Modifier.size(16.dp),
                                tint = timestampColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StructuredJsonMessageContent(
    message: ChatMessage,
    textColor: androidx.compose.ui.graphics.Color,
    accentColor: androidx.compose.ui.graphics.Color
) {
    var showFormatted by remember { mutableStateOf(false) }
    val structuredResponse = remember(message.content, showFormatted) {
        if (showFormatted) StructuredResponse.tryParse(message.content) else null
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showFormatted) "Formatted View" else "JSON View",
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.7f)
            )
            TextButton(
                onClick = { showFormatted = !showFormatted }
            ) {
                Text(
                    text = if (showFormatted) "Show JSON" else "Format",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showFormatted && structuredResponse != null) {
            FormattedStructuredContent(
                unicodeSymbols = structuredResponse.unicodeSymbols,
                questionShort = structuredResponse.questionShort,
                answer = structuredResponse.response,
                responderRole = structuredResponse.responderRole,
                textColor = textColor
            )
        } else {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )

            if (showFormatted && structuredResponse == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Failed to parse JSON",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun StructuredXmlMessageContent(
    message: ChatMessage,
    textColor: androidx.compose.ui.graphics.Color,
    accentColor: androidx.compose.ui.graphics.Color
) {
    var showFormatted by remember { mutableStateOf(false) }
    val structuredResponse = remember(message.content, showFormatted) {
        if (showFormatted) StructuredXmlResponse.tryParse(message.content) else null
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showFormatted) "Formatted View" else "XML View",
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.7f)
            )
            TextButton(
                onClick = { showFormatted = !showFormatted }
            ) {
                Text(
                    text = if (showFormatted) "Show XML" else "Format",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showFormatted && structuredResponse != null) {
            FormattedStructuredContent(
                unicodeSymbols = structuredResponse.unicodeSymbols,
                questionShort = structuredResponse.questionShort,
                answer = structuredResponse.answer,
                responderRole = structuredResponse.responderRole,
                textColor = textColor
            )
        } else {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )

            if (showFormatted && structuredResponse == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Failed to parse XML",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun FormattedStructuredContent(
    unicodeSymbols: String,
    questionShort: String,
    answer: String,
    responderRole: String,
    textColor: androidx.compose.ui.graphics.Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = unicodeSymbols,
            style = MaterialTheme.typography.headlineMedium,
            color = textColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Вопрос коротко:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = questionShort,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Ответ:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = answer,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Ответил на него:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = responderRole,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = textColor
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    val now = Clock.System.now()
    val nowLocalDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())

    val time = "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"

    // Check if the message is from a different day
    return if (localDateTime.date != nowLocalDateTime.date) {
        val day = localDateTime.dayOfMonth.toString().padStart(2, '0')
        val month = localDateTime.monthNumber.toString().padStart(2, '0')
        "$day.$month $time"
    } else {
        time
    }
}

private fun formatExecutionTime(milliseconds: Long): String {
    return when {
        milliseconds < 1000 -> "${milliseconds}ms"
        milliseconds < 60000 -> {
            val seconds = milliseconds / 1000
            val decimal = (milliseconds % 1000) / 100
            "${seconds}.${decimal}s"
        }
        else -> {
            val minutes = milliseconds / 60000
            val seconds = (milliseconds % 60000) / 1000
            "${minutes}m ${seconds}s"
        }
    }
}
