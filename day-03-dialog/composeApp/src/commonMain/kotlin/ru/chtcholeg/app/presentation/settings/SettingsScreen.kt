package ru.chtcholeg.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import ru.chtcholeg.app.data.repository.SettingsRepository
import ru.chtcholeg.app.domain.model.AiSettings
import ru.chtcholeg.app.domain.model.Model
import ru.chtcholeg.app.domain.model.ResponseMode
import ru.chtcholeg.app.presentation.components.PlatformVerticalScrollbar
import ru.chtcholeg.app.presentation.theme.ChatColors
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsRepository: SettingsRepository = koinInject()
    val settings by settingsRepository.settings.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("AI Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { settingsRepository.resetToDefaults() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset to defaults"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ChatColors.HeaderBackground,
                    titleContentColor = ChatColors.HeaderText,
                    navigationIconContentColor = ChatColors.HeaderText,
                    actionIconContentColor = ChatColors.HeaderText
                )
            )
        }
    ) { paddingValues ->
        val gradientBrush = Brush.verticalGradient(
            colors = listOf(
                ChatColors.BackgroundGradientTop,
                ChatColors.BackgroundGradientMiddle,
                ChatColors.BackgroundGradientBottom
            )
        )

        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(brush = gradientBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .padding(end = 12.dp), // Add padding for scrollbar
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
            Text(
                text = "Configure AI model parameters",
                style = MaterialTheme.typography.bodyLarge,
                color = ChatColors.HeaderBackground
            )

            // Model selection
            ModelSelector(
                currentModel = settings.model,
                onModelChange = { model ->
                    settingsRepository.updateSettings(settings.copy(model = model))
                }
            )

            HorizontalDivider()

            // Response Mode selector
            ResponseModeSelector(
                currentMode = settings.responseMode,
                onModeChange = { mode ->
                    settingsRepository.updateSettings(settings.copy(responseMode = mode))
                }
            )

            HorizontalDivider()

            // Temperature slider
            SliderSetting(
                label = "Temperature",
                value = settings.temperature ?: AiSettings.DEFAULT_TEMPERATURE,
                valueRange = AiSettings.MIN_TEMPERATURE..AiSettings.MAX_TEMPERATURE,
                steps = 19, // 20 steps for 0.1 increments
                description = "Controls randomness. Higher values make output more random.",
                onValueChange = { value ->
                    settingsRepository.updateSettings(settings.copy(temperature = value))
                }
            )

            // Top P slider
            SliderSetting(
                label = "Top P",
                value = settings.topP ?: AiSettings.DEFAULT_TOP_P,
                valueRange = AiSettings.MIN_TOP_P..AiSettings.MAX_TOP_P,
                steps = 9, // 10 steps for 0.1 increments
                description = "Nucleus sampling threshold. Controls diversity of responses.",
                onValueChange = { value ->
                    settingsRepository.updateSettings(settings.copy(topP = value))
                }
            )

            // Max Tokens slider
            IntSliderSetting(
                label = "Max Tokens",
                value = settings.maxTokens ?: AiSettings.DEFAULT_MAX_TOKENS,
                valueRange = AiSettings.MIN_MAX_TOKENS..AiSettings.MAX_MAX_TOKENS,
                description = "Maximum length of generated response.",
                onValueChange = { value ->
                    settingsRepository.updateSettings(settings.copy(maxTokens = value))
                }
            )

            // Repetition Penalty slider
            SliderSetting(
                label = "Repetition Penalty",
                value = settings.repetitionPenalty ?: AiSettings.DEFAULT_REPETITION_PENALTY,
                valueRange = AiSettings.MIN_REPETITION_PENALTY..AiSettings.MAX_REPETITION_PENALTY,
                steps = 19, // 20 steps for 0.1 increments
                description = "Penalizes repeating tokens. Higher values reduce repetition.",
                onValueChange = { value ->
                    settingsRepository.updateSettings(settings.copy(repetitionPenalty = value))
                }
            )
            }

            PlatformVerticalScrollbar(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(
    currentModel: String,
    onModelChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = Model.fromId(currentModel) ?: Model.GigaChat

    Column(modifier = modifier) {
        Text(
            text = "Model",
            style = MaterialTheme.typography.titleMedium,
            color = ChatColors.HeaderBackground
        )
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedModel.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    focusedTextColor = ChatColors.HeaderBackground,
                    unfocusedTextColor = ChatColors.HeaderBackground,
                    focusedLabelColor = ChatColors.HeaderBackground,
                    unfocusedLabelColor = ChatColors.HeaderBackground
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Model.ALL_MODELS.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ChatColors.UserBubbleText
                                )
                                Text(
                                    text = "Provider: ${model.api.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChatColors.UserBubbleText.copy(alpha = 0.7f)
                                )
                            }
                        },
                        onClick = {
                            onModelChange(model.id)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose the AI model to use for chat responses",
            style = MaterialTheme.typography.bodySmall,
            color = ChatColors.AiBubbleBackground
        )
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    description: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = ChatColors.HeaderBackground
            )
            Text(
                text = formatFloat(value, 2),
                style = MaterialTheme.typography.bodyLarge,
                color = ChatColors.UserBubbleBackground
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = ChatColors.UserBubbleBackground,
                activeTrackColor = ChatColors.UserBubbleBackground,
                inactiveTrackColor = ChatColors.AiBubbleBackground.copy(alpha = 0.3f)
            )
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = ChatColors.AiBubbleBackground
        )
    }
}

@Composable
private fun IntSliderSetting(
    label: String,
    value: Int,
    valueRange: IntRange,
    description: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = ChatColors.HeaderBackground
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = ChatColors.UserBubbleBackground
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = ((valueRange.last - valueRange.first) / 256).coerceAtLeast(0), // Reasonable number of steps
            colors = SliderDefaults.colors(
                thumbColor = ChatColors.UserBubbleBackground,
                activeTrackColor = ChatColors.UserBubbleBackground,
                inactiveTrackColor = ChatColors.AiBubbleBackground.copy(alpha = 0.3f)
            )
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = ChatColors.AiBubbleBackground
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResponseModeSelector(
    currentMode: ResponseMode,
    onModeChange: (ResponseMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "Response Mode",
            style = MaterialTheme.typography.titleMedium,
            color = ChatColors.HeaderBackground
        )
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentMode.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Response Mode") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    focusedTextColor = ChatColors.HeaderBackground,
                    unfocusedTextColor = ChatColors.HeaderBackground,
                    focusedLabelColor = ChatColors.HeaderBackground,
                    unfocusedLabelColor = ChatColors.HeaderBackground
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ResponseMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ChatColors.UserBubbleText
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChatColors.UserBubbleText.copy(alpha = 0.7f)
                                )
                            }
                        },
                        onClick = {
                            onModeChange(mode)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (currentMode) {
                ResponseMode.NORMAL -> "AI will respond directly to your questions in a conversational manner."
                ResponseMode.STRUCTURED_JSON -> "AI will respond in strict JSON format with question summary, detailed response, expert role, and unicode symbols. Use the Format button to view structured data."
                ResponseMode.DIALOG -> "AI will ask clarifying questions one at a time to gather all necessary information before providing a comprehensive final result."
            },
            style = MaterialTheme.typography.bodySmall,
            color = ChatColors.AiBubbleBackground
        )
    }
}

/**
 * Format float to string with specified decimal places
 * Cross-platform alternative to String.format()
 */
private fun formatFloat(value: Float, decimalPlaces: Int): String {
    val multiplier = when (decimalPlaces) {
        1 -> 10
        2 -> 100
        3 -> 1000
        else -> 100
    }
    val rounded = (value * multiplier).toInt().toFloat() / multiplier
    return rounded.toString()
}
