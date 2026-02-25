package ru.chtcholeg.godagent.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.chtcholeg.godagent.domain.model.AgentStep

@Composable
fun ThinkingBlock(
    steps: List<AgentStep>,
    isRunning: Boolean,
    statusMessage: String,
    modifier: Modifier = Modifier
) {
    if (steps.isEmpty() && !isRunning) return

    var expanded by remember { mutableStateOf(true) }
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                } else {
                    Icon(
                        Icons.Default.Psychology,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (isRunning) statusMessage.ifBlank { "Думаю..." } else "Thinking",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Steps
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    steps.forEach { step ->
                        StepRow(step)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: AgentStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        when (step) {
            is AgentStep.ToolCall -> {
                Text("[tool]", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        step.toolName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (step.args.length > 2) {
                        Text(
                            step.args.take(80),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            is AgentStep.ToolResult -> {
                Text(
                    if (step.isError) "[err]" else "[ok]",
                    fontSize = 11.sp,
                    color = if (step.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    step.result.lines().firstOrNull()?.take(100) ?: step.result.take(100),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (step.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            is AgentStep.StatusUpdate -> {
                Text("[i]", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    step.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {}
        }
    }
}
