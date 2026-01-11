package ru.chtcholeg.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.chtcholeg.app.data.repository.SettingsRepository
import ru.chtcholeg.app.domain.model.AiSettings
import ru.chtcholeg.app.domain.model.Model
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Configure AI model parameters",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Model selection
            ModelSelector(
                currentModel = settings.model,
                onModelChange = { model ->
                    settingsRepository.updateSettings(settings.copy(model = model))
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
            color = MaterialTheme.colorScheme.onSurface
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
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
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
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Provider: ${model.api.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatFloat(value, 2),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = ((valueRange.last - valueRange.first) / 256).coerceAtLeast(0) // Reasonable number of steps
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
