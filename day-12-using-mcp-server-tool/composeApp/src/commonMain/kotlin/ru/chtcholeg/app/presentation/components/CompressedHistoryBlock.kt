package ru.chtcholeg.app.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import ru.chtcholeg.app.domain.model.ChatMessage
import ru.chtcholeg.app.presentation.theme.chatColors

@Composable
fun CompressedHistoryBlock(
    summary: ChatMessage,
    compressedMessages: List<ChatMessage>?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onUndo: () -> Unit,
    onLoadMessages: (() -> Unit)? = null,
    onCopyMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLoadingMessages: Boolean = false
) {
    val colors = chatColors()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(12.dp)
    ) {
        // Expandable Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!isExpanded && compressedMessages == null && onLoadMessages != null) {
                        onLoadMessages()
                    }
                    onToggleExpanded()
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Compressed History (${summary.compressedMessageCount ?: 0} messages)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onUndo,
                modifier = Modifier.width(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Undo compression",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Summary Message (always visible)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small
                )
                .padding(8.dp)
        ) {
            MessageItem(
                message = summary,
                onCopyMessage = onCopyMessage
            )
        }

        // Original Messages (collapsible)
        AnimatedVisibility(
            visible = isExpanded && compressedMessages != null && compressedMessages.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (compressedMessages != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Divider(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Previous Messages",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column {
                        compressedMessages.forEach { message ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(0.7f)
                                    .padding(vertical = 4.dp)
                            ) {
                                MessageItem(
                                    message = message,
                                    onCopyMessage = onCopyMessage
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // "New Messages" separator (shown when history is expanded)
    AnimatedVisibility(
        visible = isExpanded && compressedMessages != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Divider(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                text = "New Messages",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Divider(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
