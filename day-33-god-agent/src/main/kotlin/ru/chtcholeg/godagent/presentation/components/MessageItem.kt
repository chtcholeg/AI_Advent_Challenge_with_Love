package ru.chtcholeg.godagent.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.chtcholeg.godagent.domain.model.ChatMessage
import ru.chtcholeg.godagent.domain.model.MessageRole
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageItem(message: ChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.isFromUser
    val isToolCall = message.role == MessageRole.TOOL_CALL
    val isToolResult = message.role == MessageRole.TOOL_RESULT
    val isSummary = message.role == MessageRole.SUMMARY
    val colors = MaterialTheme.colorScheme

    when {
        isToolCall || isToolResult -> {
            // Tool messages shown in a monospace gray block centered
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                BoxWithConstraints {
                    Column(
                        modifier = Modifier
                            .widthIn(max = maxWidth * 0.85f)
                            .background(
                                color = colors.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        val label = if (isToolCall) "[Tool: ${message.toolName ?: "?"}]"
                        else "[Result: ${message.toolName ?: "?"}]"
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace
                        )
                        SelectionContainer {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
        isSummary -> {
            // Summary shown as a divider-like element
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "[${message.content}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurface.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        else -> {
            // Normal user/assistant message
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                BoxWithConstraints {
                    Column(
                        modifier = Modifier
                            .widthIn(max = maxWidth * 0.75f)
                            .background(
                                color = if (isUser) colors.primary else colors.secondary,
                                shape = if (isUser) {
                                    RoundedCornerShape(
                                        topStart = 16.dp, topEnd = 16.dp,
                                        bottomStart = 16.dp, bottomEnd = 4.dp
                                    )
                                } else {
                                    RoundedCornerShape(
                                        topStart = 16.dp, topEnd = 16.dp,
                                        bottomStart = 4.dp, bottomEnd = 16.dp
                                    )
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser) colors.onPrimary else colors.onSecondary
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val metaColor = (if (isUser) colors.onPrimary else colors.onSecondary)
                                .copy(alpha = 0.6f)

                            message.executionTimeMs?.let {
                                Text(
                                    text = formatTime(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = metaColor
                                )
                            }

                            Text(
                                text = formatTimestamp(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = metaColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> "${ms / 1000}.${(ms % 1000) / 100}s"
    else -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
}

private fun formatTimestamp(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
