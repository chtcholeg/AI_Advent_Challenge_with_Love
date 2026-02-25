package ru.chtcholeg.godagent.presentation.settings

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
import ru.chtcholeg.godagent.data.repository.SettingsRepository
import ru.chtcholeg.godagent.data.tools.ToolExecutor
import ru.chtcholeg.godagent.data.tools.ToolStatus
import ru.chtcholeg.godagent.domain.model.*
import ru.chtcholeg.godagent.presentation.chat.ChatIntent
import ru.chtcholeg.godagent.presentation.chat.ChatState
import ru.chtcholeg.godagent.presentation.chat.ChatStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: ChatStore,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsRepository: SettingsRepository = koinInject()
    val toolExecutor: ToolExecutor = koinInject()
    val settings by settingsRepository.settings.collectAsState()
    val chatState by store.state.collectAsState()
    val toolStatuses by toolExecutor.toolStatuses.collectAsState()

    var ragIndexProgress by remember { mutableStateOf("") }
    var ragIndexResult by remember { mutableStateOf("") }

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

            // Agent settings
            ExpandableSection(title = "Агент", initiallyExpanded = true) {
                AgentSection(
                    settings = settings,
                    onSettingsChange = { settingsRepository.updateSettings(it) }
                )
            }

            // Git Repository
            ExpandableSection(title = "Git репозиторий", initiallyExpanded = false) {
                GitSection(
                    settings = settings,
                    onSettingsChange = { settingsRepository.updateSettings(it) }
                )
            }

            // GitHub
            ExpandableSection(title = "GitHub", initiallyExpanded = false) {
                GitHubSection(
                    settings = settings,
                    onSettingsChange = { settingsRepository.updateSettings(it) }
                )
            }

            // Telegram
            ExpandableSection(title = "Telegram", initiallyExpanded = false) {
                TelegramSection(
                    settings = settings,
                    onSettingsChange = { settingsRepository.updateSettings(it) }
                )
            }

            // RAG
            ExpandableSection(title = "RAG База знаний", initiallyExpanded = false) {
                RagSection(
                    settings = settings,
                    onSettingsChange = { settingsRepository.updateSettings(it) },
                    indexProgress = ragIndexProgress,
                    indexResult = ragIndexResult,
                    onIndex = { path ->
                        ragIndexProgress = "Начинаю индексацию..."
                        ragIndexResult = ""
                        store.indexRagFolder(
                            folderPath = path,
                            onProgress = { ragIndexProgress = it },
                            onDone = { count ->
                                ragIndexProgress = ""
                                ragIndexResult = if (count >= 0) "Проиндексировано: $count чанков" else "Ошибка индексации"
                            }
                        )
                    }
                )
            }

            // Tool Statuses
            ExpandableSection(title = "Статус инструментов", initiallyExpanded = false) {
                ToolStatusSection(
                    toolStatuses = toolStatuses,
                    onRefresh = { toolExecutor.checkStatuses() }
                )
            }

            // Voice Keywords (expandable)
            ExpandableSection(title = "Кодовые слова", initiallyExpanded = false) {
                VoiceKeywordsSection(
                    keywords = settings.voiceKeywords,
                    onKeywordsChange = { settingsRepository.updateSettings(settings.copy(voiceKeywords = it)) }
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

@Composable
private fun AgentSection(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IntSliderParam(
            label = "Макс. итераций ReAct",
            description = "Максимальное число шагов агента (вызовов инструментов) за один запрос.",
            value = settings.maxReactIterations,
            range = 1..20,
            onValueChange = { onSettingsChange(settings.copy(maxReactIterations = it)) }
        )
    }
}

@Composable
private fun GitSection(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = settings.gitRepoPath,
            onValueChange = { onSettingsChange(settings.copy(gitRepoPath = it)) },
            label = { Text("Путь к репозиторию") },
            placeholder = { Text("/Users/me/projects/myrepo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Абсолютный путь к git-репозиторию для инструментов git_log, git_diff и др.") }
        )
    }
}

@Composable
private fun GitHubSection(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = settings.githubToken,
            onValueChange = { onSettingsChange(settings.copy(githubToken = it)) },
            label = { Text("GitHub Token (опционально)") },
            placeholder = { Text("ghp_xxxxxxxxxxxx") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Personal access token для доступа к приватным репозиториям и повышения лимита запросов.") }
        )
    }
}

@Composable
private fun TelegramSection(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = settings.telegramBotToken,
            onValueChange = { onSettingsChange(settings.copy(telegramBotToken = it)) },
            label = { Text("Telegram Bot Token") },
            placeholder = { Text("123456789:AAF...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Получите токен у @BotFather в Telegram.") }
        )
        OutlinedTextField(
            value = settings.telegramChatId,
            onValueChange = { onSettingsChange(settings.copy(telegramChatId = it)) },
            label = { Text("Telegram Chat ID") },
            placeholder = { Text("-100123456789") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("ID чата или канала для отправки сообщений.") }
        )
    }
}

@Composable
private fun RagSection(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    indexProgress: String,
    indexResult: String,
    onIndex: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = settings.ragFolderPath,
            onValueChange = { onSettingsChange(settings.copy(ragFolderPath = it)) },
            label = { Text("Папка для индексации") },
            placeholder = { Text("/Users/me/documents/knowledge") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Папка с файлами для создания базы знаний (txt, md, kt, py, java, json и др.)") }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (settings.ragFolderPath.isNotBlank()) {
                        onIndex(settings.ragFolderPath)
                    }
                },
                enabled = settings.ragFolderPath.isNotBlank() && indexProgress.isBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Индексировать")
            }
        }

        if (indexProgress.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(indexProgress, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (indexResult.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    indexResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolStatusSection(
    toolStatuses: List<ToolStatus>,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Доступность инструментов",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Обновить", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (toolStatuses.isEmpty()) {
            Text(
                "Нажмите Обновить для проверки",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        } else {
            toolStatuses.forEach { status ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (status.isAvailable) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (status.isAvailable) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            status.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        if (!status.isAvailable && status.errorMessage != null) {
                            Text(
                                status.errorMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
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
                value = selectedModel?.let { "${it.displayName} (${it.provider})" }
                    ?: settings.selectedModelId,
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

        // Ollama base URL
        OutlinedTextField(
            value = settings.ollamaBaseUrl,
            onValueChange = { onSettingsChange(settings.copy(ollamaBaseUrl = it)) },
            label = { Text("Ollama Base URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        // GigaChat credentials (env vars only)
        GigaChatEnvVarStatus()

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

@Composable
private fun UserProfileSection(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    if (profile.isEnabled) "AI учитывает данные профиля в ответах"
                    else "Обычный режим без учёта профиля",
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Использовать примеры", style = MaterialTheme.typography.bodyMedium)
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

                if (profile.isEnabled) {
                    val preview = profile.buildPersonalizationPrompt()
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

@Composable
private fun VoiceKeywordsSection(
    keywords: VoiceKeywords,
    onKeywordsChange: (VoiceKeywords) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Управление голосом",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (keywords.enabled) "Запись запускается и останавливается по кодовым словам"
                    else "Кнопки управляют записью вручную",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = keywords.enabled,
                onCheckedChange = { onKeywordsChange(keywords.copy(enabled = it)) }
            )
        }

        AnimatedVisibility(visible = keywords.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = keywords.wakeWord,
                    onValueChange = { onKeywordsChange(keywords.copy(wakeWord = it)) },
                    label = { Text("Слово запуска") },
                    placeholder = { Text("компьютер") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Скажите это слово, чтобы начать запись") }
                )
                OutlinedTextField(
                    value = keywords.stopSendWord,
                    onValueChange = { onKeywordsChange(keywords.copy(stopSendWord = it)) },
                    label = { Text("Стоп + отправить") },
                    placeholder = { Text("отправить") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Остановить запись и сразу отправить сообщение") }
                )
                OutlinedTextField(
                    value = keywords.stopCancelWord,
                    onValueChange = { onKeywordsChange(keywords.copy(stopCancelWord = it)) },
                    label = { Text("Стоп + отмена") },
                    placeholder = { Text("отмена") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Остановить запись и сбросить набранный текст") }
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Пример: скажите «${keywords.wakeWord}» -> говорите сообщение -> " +
                            "«${keywords.stopSendWord}» для отправки или «${keywords.stopCancelWord}» для отмены",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

// Text field for user profile: pressing -> when empty fills the field with placeholder text.
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

@Composable
private fun GigaChatEnvVarStatus() {
    val clientIdSet = !System.getenv("GIGACHAT_CLIENT_ID").isNullOrBlank()
    val clientSecretSet = !System.getenv("GIGACHAT_CLIENT_SECRET").isNullOrBlank()
    val allSet = clientIdSet && clientSecretSet

    Surface(
        color = if (allSet) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "GigaChat credentials (из переменных окружения)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (allSet) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
            )
            listOf(
                "GIGACHAT_CLIENT_ID" to clientIdSet,
                "GIGACHAT_CLIENT_SECRET" to clientSecretSet
            ).forEach { (name, isSet) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isSet) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isSet) MaterialTheme.colorScheme.tertiary
                               else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        if (isSet) "$name: установлен" else "$name: не установлен",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (allSet) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            if (!allSet) {
                Text(
                    "Установите переменные окружения перед запуском приложения",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// -- Reusable UI components ---------------------------------------------------

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
