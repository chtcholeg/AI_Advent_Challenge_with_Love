package ru.chtcholeg.agent.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.chtcholeg.agent.domain.model.AgentMessage
import ru.chtcholeg.agent.domain.model.MessageType
import ru.chtcholeg.agent.util.ClipboardManager
import ru.chtcholeg.agent.util.ImageDecoder

/**
 * Console-style message display.
 * Shows messages as plain text like in a terminal.
 */
@Composable
fun MessageItem(
    message: AgentMessage,
    modifier: Modifier = Modifier
) {
    val prefix = when (message.type) {
        MessageType.USER -> "> "
        MessageType.AI -> ""
        MessageType.TOOL_CALL -> "[tool] "
        MessageType.TOOL_RESULT -> "[result] "
        MessageType.SCREENSHOT -> "[screenshot] "
        MessageType.SYSTEM -> "[system] "
        MessageType.ERROR -> "[error] "
    }

    val prefixColor = when (message.type) {
        MessageType.USER -> Color(0xFF6CB6FF)  // Blue
        MessageType.AI -> Color(0xFFE6E6E6)     // Light gray
        MessageType.TOOL_CALL -> Color(0xFFFFD866)  // Yellow
        MessageType.TOOL_RESULT -> Color(0xFFA9DC76)  // Green
        MessageType.SCREENSHOT -> Color(0xFF78DCE8)  // Cyan
        MessageType.SYSTEM -> Color(0xFFAB9DF2)  // Purple
        MessageType.ERROR -> Color(0xFFFF6188)  // Red
    }

    val textColor = when (message.type) {
        MessageType.USER -> Color(0xFFE6E6E6)
        MessageType.AI -> Color(0xFFE6E6E6)
        MessageType.TOOL_CALL -> Color(0xFFB0B0B0)
        MessageType.TOOL_RESULT -> Color(0xFFB0B0B0)
        MessageType.SCREENSHOT -> Color(0xFFB0B0B0)
        MessageType.SYSTEM -> Color(0xFF909090)
        MessageType.ERROR -> Color(0xFFFF6188)
    }

    // Add extra bottom padding after AI responses to separate Q&A pairs
    val bottomPadding = when (message.type) {
        MessageType.AI -> 14.dp
        MessageType.TOOL_RESULT -> 10.dp
        MessageType.SCREENSHOT -> 14.dp
        else -> 2.dp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = bottomPadding),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = buildAnnotatedString {
                    if (prefix.isNotEmpty()) {
                        withStyle(SpanStyle(color = prefixColor, fontWeight = FontWeight.Bold)) {
                            append(prefix)
                        }
                    }
                    withStyle(SpanStyle(color = textColor)) {
                        append(message.content)
                    }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            // Display screenshot image if present
            if (message.imageBase64 != null) {
                var expanded by remember { mutableStateOf(false) }
                val imageBitmap = remember(message.imageBase64) {
                    ImageDecoder.decodeBase64ToImageBitmap(message.imageBase64)
                }

                if (imageBitmap != null) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A2A2A))
                            .padding(4.dp)
                    ) {
                        Column {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Screenshot",
                                modifier = Modifier
                                    .then(
                                        if (expanded) {
                                            Modifier.fillMaxWidth()
                                        } else {
                                            Modifier.widthIn(max = 300.dp)
                                        }
                                    )
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { expanded = !expanded },
                                contentScale = ContentScale.FillWidth
                            )

                            // Zoom hint
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                                    contentDescription = if (expanded) "Shrink" else "Expand",
                                    tint = Color(0xFF606060),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { expanded = !expanded }
                                )
                            }
                        }
                    }
                } else {
                    // Failed to decode image
                    Text(
                        text = "[Failed to decode image]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFFF6188),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Metadata for AI messages (tokens, time) - subtle gray
            if (message.type == MessageType.AI && (message.executionTimeMs != null || message.totalTokens != null)) {
                val metaText = buildString {
                    message.executionTimeMs?.let { ms ->
                        append(formatExecutionTime(ms))
                    }
                    if (message.totalTokens != null && message.promptTokens != null && message.completionTokens != null) {
                        if (isNotEmpty()) append(" · ")
                        append("${message.totalTokens} tokens (↑${message.promptTokens} ↓${message.completionTokens})")
                    }
                }
                if (metaText.isNotEmpty()) {
                    Text(
                        text = metaText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF606060),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Copy button
        IconButton(
            onClick = { ClipboardManager.copyToClipboard(message.content) },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy message",
                tint = Color(0xFF606060),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun formatExecutionTime(ms: Long): String {
    return when {
        ms < 1000 -> "${ms}ms"
        ms < 60000 -> "%.1fs".format(ms / 1000.0)
        else -> {
            val minutes = ms / 60000
            val seconds = (ms % 60000) / 1000
            "${minutes}m ${seconds}s"
        }
    }
}
