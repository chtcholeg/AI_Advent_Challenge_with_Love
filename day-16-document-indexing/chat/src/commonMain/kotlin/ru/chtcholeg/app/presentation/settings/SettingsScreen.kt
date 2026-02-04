package ru.chtcholeg.app.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import ru.chtcholeg.app.data.repository.SettingsRepository
import ru.chtcholeg.app.domain.model.AiSettings
import ru.chtcholeg.shared.domain.model.Model
import ru.chtcholeg.app.domain.model.ReminderInterval
import ru.chtcholeg.app.domain.model.ResponseMode
import ru.chtcholeg.app.presentation.components.PlatformVerticalScrollbar
import ru.chtcholeg.app.presentation.reminder.ReminderIntent
import ru.chtcholeg.app.presentation.reminder.ReminderStore
import ru.chtcholeg.app.presentation.settings.mcp.McpSettingsCard
import ru.chtcholeg.app.presentation.theme.chatColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMcpManagement: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settingsRepository: SettingsRepository = koinInject()
    val reminderStore: ReminderStore = koinInject()
    val settings by settingsRepository.settings.collectAsState()
    val reminderState by reminderStore.state.collectAsState()
    val colors = chatColors()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
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
                    containerColor = colors.headerBackground,
                    titleContentColor = colors.headerText,
                    navigationIconContentColor = colors.headerText,
                    actionIconContentColor = colors.headerText
                )
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Configure AI model parameters",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.headerText.copy(alpha = 0.7f)
                )

                ExpandableModelCard(
                    currentModel = settings.model,
                    onModelChange = { model ->
                        settingsRepository.updateSettings(settings.copy(model = model))
                    }
                )

                SettingsCard {
                    ResponseModeSelector(
                        currentMode = settings.responseMode,
                        onModeChange = { mode ->
                            settingsRepository.updateSettings(settings.copy(responseMode = mode))
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = colors.divider)
                    Spacer(modifier = Modifier.height(16.dp))

                    PreserveHistorySetting(
                        isEnabled = settings.preserveHistoryOnSystemPromptChange,
                        onToggle = { enabled ->
                            settingsRepository.updateSettings(settings.copy(preserveHistoryOnSystemPromptChange = enabled))
                        }
                    )
                }

                SettingsCard {
                    SummarizationSettings(
                        isEnabled = settings.summarizationEnabled,
                        messageThreshold = settings.summarizationMessageThreshold,
                        onToggle = { enabled ->
                            settingsRepository.updateSettings(settings.copy(summarizationEnabled = enabled))
                        },
                        onThresholdChange = { threshold ->
                            settingsRepository.updateSettings(settings.copy(summarizationMessageThreshold = threshold))
                        }
                    )
                }

                // MCP Server Management
                McpSettingsCard(
                    onNavigateToManagement = onNavigateToMcpManagement
                )

                // Reminder (Telegram channel monitoring)
                ReminderSettingsCard(
                    activeConfig = reminderState.activeConfig,
                    onStart = { config -> reminderStore.dispatch(ReminderIntent.Start(config)) },
                    onStop = { reminderStore.dispatch(ReminderIntent.Stop) },
                    onUpdate = { field, value -> reminderStore.dispatch(ReminderIntent.UpdateConfig(field, value)) }
                )

                AdvancedParametersCard(
                    settings = settings,
                    onUpdateSettings = { newSettings ->
                        settingsRepository.updateSettings(newSettings)
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

@Composable
private fun SettingsCard(
    content: @Composable () -> Unit
) {
    val colors = chatColors()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = colors.divider,
                spotColor = colors.divider
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandableModelCard(
    currentModel: String,
    onModelChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val selectedModel = Model.fromId(currentModel) ?: Model.GigaChat
    val colors = chatColors()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = colors.divider,
                spotColor = colors.divider
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Clickable header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Model",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.headerText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${selectedModel.displayName} (${selectedModel.api.name})",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.headerText.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = colors.primaryAccent
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = colors.divider)
                    Spacer(modifier = Modifier.height(16.dp))

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedModel.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.headerText,
                                unfocusedTextColor = colors.headerText,
                                focusedLabelColor = colors.primaryAccent,
                                unfocusedLabelColor = colors.headerText.copy(alpha = 0.7f),
                                focusedBorderColor = colors.primaryAccent,
                                unfocusedBorderColor = colors.divider
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )

                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            containerColor = colors.dropdownBackground
                        ) {
                            Model.ALL_MODELS.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = model.displayName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = colors.dropdownText
                                            )
                                            Text(
                                                text = "Provider: ${model.api.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.dropdownTextSecondary
                                            )
                                        }
                                    },
                                    onClick = {
                                        onModelChange(model.id)
                                        dropdownExpanded = false
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
                        color = colors.headerText.copy(alpha = 0.6f)
                    )
                }
            }
        }
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
    val colors = chatColors()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.headerText
            )
            Text(
                text = formatFloat(value),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.primaryAccent
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = colors.primaryAccent,
                activeTrackColor = colors.primaryAccent,
                inactiveTrackColor = colors.divider
            )
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.headerText.copy(alpha = 0.6f)
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
    val colors = chatColors()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.headerText
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.primaryAccent
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = ((valueRange.last - valueRange.first) / 256).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = colors.primaryAccent,
                activeTrackColor = colors.primaryAccent,
                inactiveTrackColor = colors.divider
            )
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.headerText.copy(alpha = 0.6f)
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
    val colors = chatColors()

    Column(modifier = modifier) {
        Text(
            text = "Response Mode",
            style = MaterialTheme.typography.titleMedium,
            color = colors.headerText
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.headerText,
                    unfocusedTextColor = colors.headerText,
                    focusedLabelColor = colors.primaryAccent,
                    unfocusedLabelColor = colors.headerText.copy(alpha = 0.7f),
                    focusedBorderColor = colors.primaryAccent,
                    unfocusedBorderColor = colors.divider
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = colors.dropdownBackground
            ) {
                ResponseMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.dropdownText
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.dropdownTextSecondary
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
                ResponseMode.STRUCTURED_JSON -> "AI will respond in strict JSON format with question summary, detailed response, expert role, and unicode symbols."
                ResponseMode.DIALOG -> "AI will ask clarifying questions one at a time to gather all necessary information."
                ResponseMode.STEP_BY_STEP -> "AI will solve problems step-by-step, showing clear reasoning at each stage."
                ResponseMode.EXPERT_PANEL -> "AI simulates a panel of experts discussing the topic from different perspectives."
                ResponseMode.STRUCTURED_XML -> "AI will respond in strict XML format with structured data."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.headerText.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun PreserveHistorySetting(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = chatColors()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Preserve Chat History",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.headerText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEnabled) {
                        "Chat history will be preserved when changing response modes."
                    } else {
                        "Chat history will be cleared when changing response modes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.headerText.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.surface,
                    checkedTrackColor = colors.primaryAccent,
                    uncheckedThumbColor = colors.surface,
                    uncheckedTrackColor = colors.divider
                )
            )
        }
    }
}

@Composable
private fun SummarizationSettings(
    isEnabled: Boolean,
    messageThreshold: Int,
    onToggle: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = chatColors()

    Column(modifier = modifier) {
        Text(
            text = "Auto-Summarization",
            style = MaterialTheme.typography.titleMedium,
            color = colors.headerText
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable Auto-Summarization",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.headerText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEnabled) {
                        "Conversation will be automatically compressed after $messageThreshold messages"
                    } else {
                        "Auto-compression is disabled. Use the compress button manually."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.headerText.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.surface,
                    checkedTrackColor = colors.primaryAccent,
                    uncheckedThumbColor = colors.surface,
                    uncheckedTrackColor = colors.divider
                )
            )
        }

        if (isEnabled) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Message Threshold",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.headerText
                )
                Text(
                    text = "$messageThreshold messages",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.primaryAccent
                )
            }

            Slider(
                value = messageThreshold.toFloat(),
                onValueChange = { onThresholdChange(it.toInt()) },
                valueRange = AiSettings.MIN_SUMMARIZATION_THRESHOLD.toFloat()..AiSettings.MAX_SUMMARIZATION_THRESHOLD.toFloat(),
                steps = (AiSettings.MAX_SUMMARIZATION_THRESHOLD - AiSettings.MIN_SUMMARIZATION_THRESHOLD) / 2 - 1,
                colors = SliderDefaults.colors(
                    thumbColor = colors.primaryAccent,
                    activeTrackColor = colors.primaryAccent,
                    inactiveTrackColor = colors.divider
                )
            )

            Text(
                text = "Number of messages after which conversation will be compressed (${AiSettings.MIN_SUMMARIZATION_THRESHOLD}-${AiSettings.MAX_SUMMARIZATION_THRESHOLD})",
                style = MaterialTheme.typography.bodySmall,
                color = colors.headerText.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Note: Compression mode is always enabled - history will be replaced with summary to reduce token usage",
            style = MaterialTheme.typography.bodySmall,
            color = colors.headerText.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun AdvancedParametersCard(
    settings: AiSettings,
    onUpdateSettings: (AiSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val colors = chatColors()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = colors.divider,
                spotColor = colors.divider
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Clickable header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Advanced Parameters",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.headerText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Temperature, Top P, Max Tokens, Repetition Penalty",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.headerText.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = colors.primaryAccent
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = colors.divider)
                    Spacer(modifier = Modifier.height(16.dp))

                    SliderSetting(
                        label = "Temperature",
                        value = settings.temperature ?: AiSettings.DEFAULT_TEMPERATURE,
                        valueRange = AiSettings.MIN_TEMPERATURE..AiSettings.MAX_TEMPERATURE,
                        steps = 19,
                        description = "Controls randomness. Higher values make output more random.",
                        onValueChange = { value ->
                            onUpdateSettings(settings.copy(temperature = value))
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    SliderSetting(
                        label = "Top P",
                        value = settings.topP ?: AiSettings.DEFAULT_TOP_P,
                        valueRange = AiSettings.MIN_TOP_P..AiSettings.MAX_TOP_P,
                        steps = 9,
                        description = "Nucleus sampling threshold. Controls diversity of responses.",
                        onValueChange = { value ->
                            onUpdateSettings(settings.copy(topP = value))
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    IntSliderSetting(
                        label = "Max Tokens",
                        value = settings.maxTokens ?: AiSettings.DEFAULT_MAX_TOKENS,
                        valueRange = AiSettings.MIN_MAX_TOKENS..AiSettings.MAX_MAX_TOKENS,
                        description = "Maximum length of generated response.",
                        onValueChange = { value ->
                            onUpdateSettings(settings.copy(maxTokens = value))
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    SliderSetting(
                        label = "Repetition Penalty",
                        value = settings.repetitionPenalty ?: AiSettings.DEFAULT_REPETITION_PENALTY,
                        valueRange = AiSettings.MIN_REPETITION_PENALTY..AiSettings.MAX_REPETITION_PENALTY,
                        steps = 19,
                        description = "Penalizes repeating tokens. Higher values reduce repetition.",
                        onValueChange = { value ->
                            onUpdateSettings(settings.copy(repetitionPenalty = value))
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSettingsCard(
    activeConfig: ru.chtcholeg.app.domain.model.ReminderConfig?,
    onStart: (ru.chtcholeg.app.domain.model.ReminderConfig) -> Unit,
    onStop: () -> Unit,
    onUpdate: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = chatColors()
    val isEnabled = activeConfig?.enabled == true
    var channel by remember(activeConfig) { mutableStateOf(activeConfig?.channel ?: "") }
    var selectedInterval by remember(activeConfig) { mutableStateOf(activeConfig?.interval ?: ReminderInterval.THIRTY_SECONDS) }
    var messageCount by remember(activeConfig) { mutableStateOf(activeConfig?.messageCount ?: 10) }
    var intervalExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = colors.divider,
                spotColor = colors.divider
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Мониторинг Telegram-канала",
                style = MaterialTheme.typography.titleMedium,
                color = colors.headerText
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Автоматический саммари",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.headerText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEnabled) {
                            "Саммари последних $messageCount сообщений из @$channel каждые ${selectedInterval.displayName}"
                        } else {
                            "Напишите в чате: «мониторь канал @username» или настройте здесь"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.headerText.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && channel.isNotBlank()) {
                            onStart(ru.chtcholeg.app.domain.model.ReminderConfig(
                                channel = channel,
                                interval = selectedInterval,
                                messageCount = messageCount,
                                enabled = true
                            ))
                        } else if (enabled) {
                            // Can't enable without channel
                        } else {
                            onStop()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.surface,
                        checkedTrackColor = colors.primaryAccent,
                        uncheckedThumbColor = colors.surface,
                        uncheckedTrackColor = colors.divider
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Channel input
            OutlinedTextField(
                value = channel,
                onValueChange = { newChannel ->
                    channel = newChannel.removePrefix("@")
                    if (isEnabled) onUpdate("channel", channel)
                },
                label = { Text("Канал (@username)") },
                placeholder = { Text("durov") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.headerText,
                    unfocusedTextColor = colors.headerText,
                    focusedLabelColor = colors.primaryAccent,
                    unfocusedLabelColor = colors.headerText.copy(alpha = 0.7f),
                    focusedBorderColor = colors.primaryAccent,
                    unfocusedBorderColor = colors.divider
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Interval dropdown
            ExposedDropdownMenuBox(
                expanded = intervalExpanded,
                onExpandedChange = { intervalExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedInterval.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Интервал проверки") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.headerText,
                        unfocusedTextColor = colors.headerText,
                        focusedLabelColor = colors.primaryAccent,
                        unfocusedLabelColor = colors.headerText.copy(alpha = 0.7f),
                        focusedBorderColor = colors.primaryAccent,
                        unfocusedBorderColor = colors.divider
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                )

                ExposedDropdownMenu(
                    expanded = intervalExpanded,
                    onDismissRequest = { intervalExpanded = false },
                    containerColor = colors.dropdownBackground
                ) {
                    ReminderInterval.entries.forEach { interval ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = interval.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.dropdownText
                                )
                            },
                            onClick = {
                                selectedInterval = interval
                                intervalExpanded = false
                                if (isEnabled) onUpdate("interval_seconds", interval.seconds.toString())
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Message count slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Сообщений для саммари",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.headerText
                )
                Text(
                    text = "$messageCount",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.primaryAccent
                )
            }

            Slider(
                value = messageCount.toFloat(),
                onValueChange = { newValue ->
                    messageCount = newValue.toInt().coerceIn(1, 30)
                    if (isEnabled) onUpdate("message_count", messageCount.toString())
                },
                valueRange = 1f..30f,
                steps = 28,
                colors = SliderDefaults.colors(
                    thumbColor = colors.primaryAccent,
                    activeTrackColor = colors.primaryAccent,
                    inactiveTrackColor = colors.divider
                )
            )

            Text(
                text = "Количество последних сообщений канала, включённых в саммари (1-30)",
                style = MaterialTheme.typography.bodySmall,
                color = colors.headerText.copy(alpha = 0.6f)
            )
        }
    }
}

private fun formatFloat(value: Float, decimalPlaces: Int = 2): String {
    val multiplier = when (decimalPlaces) {
        1 -> 10
        2 -> 100
        3 -> 1000
        else -> 100
    }
    val rounded = (value * multiplier).toInt().toFloat() / multiplier
    return rounded.toString()
}
