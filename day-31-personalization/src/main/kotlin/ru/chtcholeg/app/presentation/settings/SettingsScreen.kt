package ru.chtcholeg.app.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import ru.chtcholeg.app.data.repository.SettingsRepository
import ru.chtcholeg.app.domain.model.*
import ru.chtcholeg.app.presentation.chat.ChatState
import ru.chtcholeg.app.presentation.chat.ChatStore
import ru.chtcholeg.app.presentation.chat.ChatIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: ChatStore,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsRepository: SettingsRepository = koinInject()
    val settings by settingsRepository.settings.collectAsState()
    val chatState by store.state.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Model Selection
            ModelSelectionSection(
                settings = settings,
                chatState = chatState,
                onSettingsChange = { settingsRepository.updateSettings(it) },
                onRefreshModels = { store.dispatch(ChatIntent.RefreshOllamaModels) }
            )

            // Model Parameters (expandable)
            ExpandableSection(title = "Параметры модели", initiallyExpanded = false) {
                ModelParametersSection(
                    params = settings.modelParameters,
                    onParamsChange = { settingsRepository.updateSettings(settings.copy(modelParameters = it)) }
                )
            }

            // User Profile (expandable)
            ExpandableSection(title = "Профиль пользователя") {
                UserProfileSection(
                    profile = settings.userProfile,
                    onProfileChange = { settingsRepository.updateSettings(settings.copy(userProfile = it)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSection(
    settings: AppSettings,
    chatState: ChatState,
    onSettingsChange: (AppSettings) -> Unit,
    onRefreshModels: () -> Unit
) {
    SettingsCard(title = "Выбор модели") {
        var expanded by remember { mutableStateOf(false) }

        val allModels: List<ModelInfo> = buildList {
            addAll(ModelInfo.GIGACHAT_MODELS)
            addAll(chatState.ollamaModels)
        }
        val selectedModel = allModels.firstOrNull { it.id == settings.selectedModelId }
            ?: allModels.firstOrNull()

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedModel?.let { "${it.displayName} (${it.provider})" } ?: settings.selectedModelId,
                onValueChange = {},
                readOnly = true,
                label = { Text("Модель") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // GigaChat models
                Text(
                    "GigaChat",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                ModelInfo.GIGACHAT_MODELS.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName) },
                        onClick = {
                            onSettingsChange(settings.copy(selectedModelId = model.id))
                            expanded = false
                        },
                        leadingIcon = {
                            if (model.id == settings.selectedModelId) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }

                // Ollama models
                if (chatState.ollamaModels.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        "Ollama (локальные)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    chatState.ollamaModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.displayName) },
                            onClick = {
                                onSettingsChange(settings.copy(selectedModelId = model.id))
                                expanded = false
                            },
                            leadingIcon = {
                                if (model.id == settings.selectedModelId) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val ollamaStatus = if (chatState.isOllamaAvailable)
                "Ollama: ${chatState.ollamaModels.size} моделей доступно"
            else
                "Ollama: недоступна"

            val statusColor = if (chatState.isOllamaAvailable)
                MaterialTheme.colorScheme.tertiary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)

            Text(
                ollamaStatus,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier.weight(1f)
            )

            OutlinedButton(
                onClick = onRefreshModels,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Обновить", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}


@Composable
private fun ModelParametersSection(
    params: ModelParameters,
    onParamsChange: (ModelParameters) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FloatSliderParam(
            label = "Температура",
            description = "Случайность ответов. Выше — больше вариативности.",
            value = params.temperature,
            range = ModelParameters.MIN_TEMPERATURE..ModelParameters.MAX_TEMPERATURE,
            steps = 19,
            onValueChange = { onParamsChange(params.copy(temperature = it)) }
        )

        FloatSliderParam(
            label = "Top P",
            description = "Nucleus sampling. Контролирует разнообразие ответов.",
            value = params.topP,
            range = ModelParameters.MIN_TOP_P..ModelParameters.MAX_TOP_P,
            steps = 9,
            onValueChange = { onParamsChange(params.copy(topP = it)) }
        )

        IntSliderParam(
            label = "Top K",
            description = "Количество токенов-кандидатов при генерации.",
            value = params.topK,
            range = ModelParameters.MIN_TOP_K..ModelParameters.MAX_TOP_K,
            onValueChange = { onParamsChange(params.copy(topK = it)) }
        )

        IntSliderParam(
            label = "Max Tokens",
            description = "Максимальная длина ответа в токенах.",
            value = params.maxTokens,
            range = ModelParameters.MIN_MAX_TOKENS..ModelParameters.MAX_MAX_TOKENS,
            onValueChange = { onParamsChange(params.copy(maxTokens = it)) }
        )

        FloatSliderParam(
            label = "Repetition Penalty",
            description = "Штраф за повторение токенов. Выше — меньше повторений.",
            value = params.repetitionPenalty,
            range = ModelParameters.MIN_REPETITION_PENALTY..ModelParameters.MAX_REPETITION_PENALTY,
            steps = 19,
            onValueChange = { onParamsChange(params.copy(repetitionPenalty = it)) }
        )

        OutlinedButton(
            onClick = { onParamsChange(ModelParameters()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Сбросить по умолчанию")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserProfileSection(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Enable toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Персонализация",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (profile.isEnabled)
                        "AI учитывает данные профиля в ответах"
                    else
                        "Обычный режим без учёта профиля",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = profile.isEnabled,
                onCheckedChange = { onProfileChange(profile.copy(isEnabled = it)) }
            )
        }

        AnimatedVisibility(visible = profile.isEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = profile.name,
                    onValueChange = { onProfileChange(profile.copy(name = it)) },
                    label = { Text("Имя") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ProfileTextField(
                    value = profile.preferredLanguage,
                    onValueChange = { onProfileChange(profile.copy(preferredLanguage = it)) },
                    label = "Предпочитаемый язык",
                    placeholder = "Русский",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ProfileTextField(
                    value = profile.areasOfInterest,
                    onValueChange = { onProfileChange(profile.copy(areasOfInterest = it)) },
                    label = "Области интересов",
                    placeholder = "Программирование, AI, наука...",
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Detail level
                Column {
                    Text(
                        "Уровень детализации",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailLevel.entries.forEach { level ->
                            FilterChip(
                                selected = profile.detailLevel == level,
                                onClick = { onProfileChange(profile.copy(detailLevel = level)) },
                                label = { Text(level.displayName) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Use examples toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Использовать примеры",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = profile.useExamples,
                        onCheckedChange = { onProfileChange(profile.copy(useExamples = it)) }
                    )
                }

                ProfileTextField(
                    value = profile.additionalDescription,
                    onValueChange = { onProfileChange(profile.copy(additionalDescription = it)) },
                    label = "Дополнительное описание",
                    placeholder = "Опыт, предпочтения, особые пожелания...",
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )

                // Preview system prompt
                if (profile.isEnabled) {
                    val preview = profile.buildSystemPrompt()
                    if (preview.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Системный промпт (предпросмотр):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Text field for user profile: pressing → when empty fills the field with placeholder text.
@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = modifier.onPreviewKeyEvent { event ->
            if (value.isEmpty() &&
                event.type == KeyEventType.KeyDown &&
                event.key == Key.DirectionRight
            ) {
                onValueChange(placeholder)
                true
            } else {
                false
            }
        },
        singleLine = singleLine,
        maxLines = maxLines
    )
}

// ── Reusable UI components ─────────────────────────────────────────────────────

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun FloatSliderParam(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                formatFloat(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
        Text(
            description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun IntSliderParam(
    label: String,
    description: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / 64).coerceAtLeast(0)
        )
        Text(
            description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

private fun formatFloat(value: Float): String {
    val rounded = (value * 100).toInt().toFloat() / 100
    return rounded.toString()
}
