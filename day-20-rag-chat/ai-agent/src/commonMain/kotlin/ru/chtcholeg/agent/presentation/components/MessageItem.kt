package ru.chtcholeg.agent.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.chtcholeg.agent.domain.model.AgentMessage
import ru.chtcholeg.agent.domain.model.MessageType
import ru.chtcholeg.agent.domain.model.SourceReference
import ru.chtcholeg.agent.util.ClipboardManager
import ru.chtcholeg.agent.util.ImageDecoder

/**
 * Console-style message display.
 * Shows messages as plain text like in a terminal.
 */
@Composable
fun MessageItem(
    message: AgentMessage,
    onSourceClick: (String) -> Unit = {},
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
        MessageType.RAG_CONTEXT -> "[rag] "
    }

    val prefixColor = when (message.type) {
        MessageType.USER -> Color(0xFF6CB6FF)  // Blue
        MessageType.AI -> Color(0xFFE6E6E6)     // Light gray
        MessageType.TOOL_CALL -> Color(0xFFFFD866)  // Yellow
        MessageType.TOOL_RESULT -> Color(0xFFA9DC76)  // Green
        MessageType.SCREENSHOT -> Color(0xFF78DCE8)  // Cyan
        MessageType.SYSTEM -> Color(0xFFAB9DF2)  // Purple
        MessageType.ERROR -> Color(0xFFFF6188)  // Red
        MessageType.RAG_CONTEXT -> Color(0xFFE8A840)  // Orange
    }

    val textColor = when (message.type) {
        MessageType.USER -> Color(0xFFE6E6E6)
        MessageType.AI -> Color(0xFFE6E6E6)
        MessageType.TOOL_CALL -> Color(0xFFB0B0B0)
        MessageType.TOOL_RESULT -> Color(0xFFB0B0B0)
        MessageType.SCREENSHOT -> Color(0xFFB0B0B0)
        MessageType.SYSTEM -> Color(0xFF909090)
        MessageType.ERROR -> Color(0xFFFF6188)
        MessageType.RAG_CONTEXT -> Color(0xFFB0B0B0)
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
            if (message.type == MessageType.AI && !message.sources.isNullOrEmpty()) {
                val annotated = buildSourceAnnotatedString(prefix, prefixColor, message.content, textColor, message.sources)
                @Suppress("DEPRECATION")
                ClickableText(
                    text = annotated,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    onClick = { charOffset ->
                        annotated.getStringAnnotations(tag = "source", start = charOffset, end = charOffset)
                            .firstOrNull()?.let { annotation ->
                                val sourceNum = annotation.item.toIntOrNull()
                                sourceNum?.let { message.sources?.get(it) }?.let { source ->
                                    onSourceClick(source.filePath)
                                }
                            }
                    }
                )
            } else {
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
            }

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

            // Display sources list for AI messages with RAG sources
            if (message.type == MessageType.AI && !message.sources.isNullOrEmpty()) {
                var sourcesExpanded by remember { mutableStateOf(false) }

                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252525), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    // Header with expand/collapse button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sourcesExpanded = !sourcesExpanded }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Источники (${message.sources.size}):",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF909090),
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (sourcesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (sourcesExpanded) "Hide sources" else "Show sources",
                            tint = Color(0xFF606060),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Sources list (only shown when expanded)
                    if (sourcesExpanded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        message.sources.entries.sortedBy { it.key }.forEach { (num, source) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSourceClick(source.filePath) }
                                ) {
                                    Text(
                                        text = "[$num] ",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFF6CB6FF),
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (source.isUrl) {
                                        Text(
                                            text = shortenUrl(source.filePath),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFF78DCE8),
                                            textDecoration = TextDecoration.Underline
                                        )
                                    } else {
                                        Text(
                                            text = source.filePath.substringAfterLast("/").substringAfterLast("\\"),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFFA9DC76)
                                        )
                                    }
                                }

                                // Display citation/quote text if available
                                if (source.text.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 20.dp, top = 4.dp)
                                            .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "\"${source.text.take(300)}${if (source.text.length > 300) "..." else ""}\"",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = Color(0xFFB0B0B0),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }

                                // Display chunk info and similarity
                                Text(
                                    text = "Фрагмент ${source.chunkIndex}/${source.totalChunks} · Релевантность: ${"%.0f".format(source.similarity * 100)}%",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFF707070),
                                    modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                                )
                            }
                        }
                    }
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

private val sourcePattern = Regex("""\[Источник\s+(\d+)]""")
private val sourceSectionPattern = Regex("""\n*📚\s*Источники:[\s\S]*$""")

/**
 * Strip the AI-generated "📚 Источники:" section from the end of the message,
 * since sources are now shown as inline clickable links.
 */
private fun stripSourcesSection(content: String): String {
    return sourceSectionPattern.replace(content, "").trimEnd()
}

private fun buildSourceAnnotatedString(
    prefix: String,
    prefixColor: Color,
    content: String,
    textColor: Color,
    sources: Map<Int, SourceReference>
): AnnotatedString = buildAnnotatedString {
    if (prefix.isNotEmpty()) {
        withStyle(SpanStyle(color = prefixColor, fontWeight = FontWeight.Bold)) {
            append(prefix)
        }
    }
    val cleanContent = stripSourcesSection(content)
    var lastIndex = 0
    sourcePattern.findAll(cleanContent).forEach { match ->
        val sourceNum = match.groupValues[1].toIntOrNull()
        // Append text before this match
        if (match.range.first > lastIndex) {
            withStyle(SpanStyle(color = textColor)) {
                append(cleanContent.substring(lastIndex, match.range.first))
            }
        }
        if (sourceNum != null && sources.containsKey(sourceNum)) {
            val source = sources[sourceNum]!!
            pushStringAnnotation(tag = "source", annotation = sourceNum.toString())
            withStyle(
                SpanStyle(
                    color = if (source.isUrl) Color(0xFF78DCE8) else Color(0xFFA9DC76),
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append("[${sourceNum}]")
            }
            pop()
        } else {
            withStyle(SpanStyle(color = textColor)) {
                append(match.value)
            }
        }
        lastIndex = match.range.last + 1
    }
    // Append remaining text
    if (lastIndex < cleanContent.length) {
        withStyle(SpanStyle(color = textColor)) {
            append(cleanContent.substring(lastIndex))
        }
    }
}

/**
 * Shorten a URL if it's longer than maxLength characters.
 * Shows the beginning (host + start of path) and end, with "..." in the middle.
 */
private fun shortenUrl(url: String, maxLength: Int = 100): String {
    if (url.length <= maxLength) return url

    // Try to extract host
    val hostEnd = url.indexOf('/', url.indexOf("://") + 3)
    if (hostEnd == -1) return url // No path, just host

    val host = url.substring(0, hostEnd)
    val path = url.substring(hostEnd)

    // If host itself is too long, just truncate
    if (host.length >= maxLength - 10) {
        return url.take(maxLength - 3) + "..."
    }

    // Calculate how much space we have for the path
    val availableSpace = maxLength - host.length - 3 // 3 for "..."
    if (availableSpace <= 10) {
        return host + "/..."
    }

    // Split available space: more for the end (filename is usually more important)
    val startChars = availableSpace / 3
    val endChars = availableSpace - startChars

    return host + path.take(startChars) + "..." + path.takeLast(endChars)
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
