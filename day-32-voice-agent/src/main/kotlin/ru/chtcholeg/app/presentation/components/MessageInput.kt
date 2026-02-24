package ru.chtcholeg.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp

@Composable
fun MessageInput(
    onSendMessage: (String) -> Unit,
    isLoading: Boolean,
    isRecording: Boolean,
    isPassiveListening: Boolean,
    isInitializingSTT: Boolean,
    keywordsEnabled: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onStartPassiveListening: () -> Unit,
    onStopPassiveListening: () -> Unit,
    recognizedText: String?,
    partialText: String,
    clearInputTrigger: Int,
    onRecognizedTextConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    // Text that was in the field before recording started
    var baseText by remember { mutableStateOf("") }

    // Save base text when recording starts
    LaunchedEffect(isRecording) {
        if (isRecording) baseText = text
    }

    // Show partial result in real-time while recording
    LaunchedEffect(partialText) {
        if (isRecording) {
            text = if (baseText.isBlank()) partialText else "$baseText $partialText"
        }
    }

    // Clear input on SEND / CANCEL stop word
    LaunchedEffect(clearInputTrigger) {
        if (clearInputTrigger > 0) {
            text = ""
            baseText = ""
        }
    }

    // Replace partial with final result when recording stops
    LaunchedEffect(recognizedText) {
        if (recognizedText != null) {
            text = if (baseText.isBlank()) recognizedText else "$baseText $recognizedText"
            baseText = text
            onRecognizedTextConsumed()
        }
    }

    fun sendIfNotEmpty() {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty() && !isLoading) {
            onSendMessage(trimmed)
            text = ""
            baseText = ""
        }
    }

    // Pulsing animation shared by recording and passive listening buttons
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            if (isRecording) {
                Text(
                    text = "Запись... Нажмите стоп, чтобы завершить",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp)
                )
            } else if (isPassiveListening) {
                Text(
                    text = "Голосовое управление активно — жду кодовое слово...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp)
                )
            } else if (keywordsEnabled) {
                Text(
                    text = "Голосовое управление остановлено",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key == Key.Enter &&
                                event.isCtrlPressed
                            ) {
                                sendIfNotEmpty()
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("Введите сообщение... (Enter — новая строка, Ctrl+Enter — отправить)") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 5,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Passive listening button (only when keywords enabled)
                if (keywordsEnabled) {
                    FilledIconButton(
                        onClick = {
                            if (isPassiveListening) onStopPassiveListening()
                            else onStartPassiveListening()
                        },
                        enabled = !isLoading && !isRecording,
                        modifier = Modifier
                            .size(48.dp)
                            .then(if (isPassiveListening) Modifier.scale(scale) else Modifier),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isPassiveListening)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        if (isInitializingSTT && !isRecording) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (isPassiveListening) {
                            Icon(
                                imageVector = Icons.Filled.HearingDisabled,
                                contentDescription = "Остановить прослушивание",
                                tint = MaterialTheme.colorScheme.onTertiary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Hearing,
                                contentDescription = "Слушать кодовое слово",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Microphone button
                FilledIconButton(
                    onClick = { if (isRecording) onStopRecording() else onStartRecording() },
                    enabled = !isLoading && !isPassiveListening,
                    modifier = Modifier
                        .size(48.dp)
                        .then(if (isRecording) Modifier.scale(scale) else Modifier),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isRecording)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    if (isInitializingSTT && !isPassiveListening) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (isRecording) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Стоп",
                            tint = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Голосовой ввод",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Send button
                FilledIconButton(
                    onClick = { sendIfNotEmpty() },
                    enabled = text.isNotBlank() && !isLoading,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }
    }
}
