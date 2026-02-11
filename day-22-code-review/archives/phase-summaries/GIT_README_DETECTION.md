# Git-based README Detection Strategy

## Обзор

Новая стратегия (Strategy 1) для автоматического определения рабочей директории разработчика с использованием Git MCP Server. Позволяет AI Agent находить README.md в контексте текущей работы.

## Архитектура

### Компоненты

```
ProjectRootProvider (expect/actual)
    ↓
DesktopProjectRootProvider(mcpRepository: McpRepository?)
    ↓
McpRepository.executeTool("git_status"|"git_log")
    ↓
Git MCP Server
```

### Файловая структура

- **Interface**: `commonMain/.../ProjectRootProvider.kt`
- **Desktop Implementation**: `desktopMain/.../ProjectRootProvider.desktop.kt`
- **Android Implementation**: `androidMain/.../ProjectRootProvider.android.kt`
- **DI Configuration**: `commonMain/di/Koin.kt`

## Алгоритм работы

### Strategy 1: Git MCP Detection

```kotlin
suspend fun detectGitWorkingDirectory(): File? {
    // Step 1: Try git_status (current changes)
    val statusResult = mcpRepository.executeTool("git_status", {})
    if (hasModifiedFiles(statusResult)) {
        val files = parseGitStatusFiles(statusResult)
        return findCommonDirectory(files)
    }

    // Step 2: Try git_log (last commit)
    val logResult = mcpRepository.executeTool("git_log", { max_count = 1 })
    if (hasCommitFiles(logResult)) {
        val files = parseGitLogFiles(logResult)
        return findCommonDirectory(files)
    }

    return null
}
```

### Парсинг Git Status

Поддерживаемые форматы:
```
modified:   path/to/file.kt
new file:   path/to/file.kt
deleted:    path/to/file.kt
M  path/to/file.kt
 M path/to/file.kt
?? path/to/file.kt
```

Регулярные выражения:
```kotlin
Regex("""^(?:modified|new file|deleted|renamed):\s+(.+)$""")
Regex("""^[MADRCU?!]{1,2}\s+(.+)$""")
```

### Парсинг Git Log

Ищет файлы в выводе `git log`:
```
diff --git a/path/to/file.kt b/path/to/file.kt
```

Регулярное выражение:
```kotlin
Regex("""^diff --git a/(.+?) b/""")
```

### Поиск общей директории

```kotlin
fun findCommonDirectory(filePaths: List<String>): File? {
    // 1. Один файл → parent directory
    if (filePaths.size == 1) {
        return File(gitRoot, filePaths[0]).parentFile
    }

    // 2. Несколько файлов → общий префикс
    val pathParts = filePaths.map { it.split("/") }
    var commonDepth = 0
    for (i in 0 until minLength) {
        if (pathParts.all { it[i] == pathParts[0][i] }) {
            commonDepth = i + 1
        } else break
    }

    // 3. Построить путь
    val commonPath = pathParts[0].take(commonDepth).joinToString("/")
    return File(gitRoot, commonPath)
}
```

## Приоритеты стратегий

1. **Git MCP Detection** (новая) - наивысший приоритет
   - Требует подключённый Git MCP Server
   - Анализирует текущие изменения или последний коммит
   - Возвращает контекстную директорию

2. **Git Root Search** - средний приоритет
   - Ищет `.git` папку
   - Возвращает корень репозитория

3. **Hierarchy Search** - фоллбэк
   - Поиск вверх по файловой системе (до 5 уровней)
   - Работает без Git

## Логирование

Все операции логируются с префиксом `[ProjectRootProvider]`:

```
[ProjectRootProvider] Working directory: /Users/.../day-21-developer-assistant
[ProjectRootProvider] Trying git_status via MCP...
[ProjectRootProvider] git_status result: On branch main...
[ProjectRootProvider] Found 3 modified files from git_status
[ProjectRootProvider] Git MCP detected working directory: /Users/.../ai-agent
[ProjectRootProvider] Found README.md in Git working directory
```

## Обработка ошибок

```kotlin
try {
    val gitWorkingDir = detectGitWorkingDirectory()
    if (gitWorkingDir != null) {
        // Use directory
    }
} catch (e: Exception) {
    println("[ProjectRootProvider] Git MCP strategy failed: ${e.message}, trying other strategies...")
    // Fall through to Strategy 2
}
```

Стратегия gracefully деградирует к следующей при:
- MCP Server недоступен
- Git инструменты не отвечают
- Нет изменённых файлов/коммитов
- Ошибки парсинга

## Интеграция с Koin DI

```kotlin
// ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/di/Koin.kt

single<McpRepository> {
    McpRepositoryImpl(
        mcpClientManager = get(),
        mcpLocalRepository = get(),
        localToolsProvider = get()
    )
}

// McpRepository передаётся в ProjectRootProvider
single<ProjectRootProvider> { createProjectRootProvider(get()) }
```

## Платформенные различия

### Desktop (macOS/Linux/Windows)
- Полная поддержка всех стратегий
- McpRepository передаётся и используется
- Реальный файловый доступ

### Android
- McpRepository игнорируется (`@Suppress("UNUSED_PARAMETER")`)
- Возвращает статический README
- Нет Git интеграции

## Примеры использования

### Сценарий 1: Работа в поддиректории

```
Репозиторий: /Users/dev/project/
Рабочая директория: /Users/dev/project/ai-agent/
Изменённые файлы:
  - ai-agent/src/main/App.kt
  - ai-agent/src/main/Store.kt

Результат:
  Общая директория: /Users/dev/project/ai-agent/
  README: /Users/dev/project/ai-agent/README.md ✅
```

### Сценарий 2: Работа в корне

```
Репозиторий: /Users/dev/project/
Рабочая директория: /Users/dev/project/
Изменённые файлы:
  - README.md
  - build.gradle.kts

Результат:
  Общая директория: /Users/dev/project/
  README: /Users/dev/project/README.md ✅
```

### Сценарий 3: Нет изменений → git_log

```
git_status: пусто
git_log (последний коммит):
  - ai-agent/src/main/Store.kt
  - shared/src/api/Api.kt

Результат:
  Общая директория: /Users/dev/project/ (корень)
  README: /Users/dev/project/README.md ✅
```

### Сценарий 4: MCP недоступен → фоллбэк

```
Git MCP Server: не подключён

Strategy 1: пропущена (MCP недоступен)
Strategy 2: ищет .git → /Users/dev/project/
  README: /Users/dev/project/README.md ✅
```

## Git MCP Server конфигурация

Для работы Strategy 1 требуется:

1. Запущенный Git MCP Server:
```bash
cd mcp-servers
python -m git.main --no-auth --repo-path /path/to/repo
```

2. Сервер добавлен в AI Agent:
   - Settings → MCP Servers
   - Name: Git
   - URL: `http://localhost:8010/sse`
   - Enabled: ✓

3. Доступные инструменты:
   - `git_status` - показать изменённые файлы
   - `git_log` - показать историю коммитов

## Производительность

- **git_status**: ~50-100ms (локальный MCP)
- **git_log**: ~100-200ms (локальный MCP)
- **Парсинг**: <10ms
- **findCommonDirectory**: <5ms

**Общее время Strategy 1**: ~150-300ms (при наличии изменений)

## Тестирование

### Ручное тестирование

1. Запустить Git MCP Server:
```bash
cd mcp-servers
./START.sh
```

2. Запустить AI Agent:
```bash
./gradlew :ai-agent:run
```

3. Добавить Git MCP Server через Settings

4. Проверить логи в консоли при запуске команды `/help`

### Проверка стратегий

```kotlin
// Изменить файлы в git
// Запустить /help
// Проверить лог:
[ProjectRootProvider] Git MCP detected working directory: ...

// Откатить изменения (git restore .)
// Запустить /help снова
// Проверить лог:
[ProjectRootProvider] No modified files in git_status, trying git_log...
```

## Известные ограничения

1. **Требует Git MCP Server**: Без него Strategy 1 не работает
2. **Локальный репозиторий**: Работает только с локальными Git репозиториями
3. **Парсинг зависит от формата**: Может не распознать нестандартные форматы git status/log
4. **Только .md файлы**: Ищет только README.md, не README.txt и т.д.

## Будущие улучшения

1. Поддержка `git_diff` для более точного определения
2. Кэширование результата на N секунд
3. Поддержка других файлов (CONTRIBUTING.md, CHANGELOG.md)
4. Веса для разных типов изменений (M > A > D)
5. Интеграция с git worktrees
