# Тестирование Strategy 3 (Git MCP Detection)

## Быстрый старт

### 1. Запуск Git MCP Server

```bash
cd mcp-servers
./START.sh
```

Вы должны увидеть:
```
Git MCP Server starting...
Server running on http://localhost:8010
```

### 2. Запуск AI Agent

AI Agent уже запущен (окно должно быть открыто). Если нет:
```bash
./gradlew :ai-agent:run
```

### 3. Настройка Git MCP Server в AI Agent

В открытом окне AI Agent:

1. Нажмите на **⚙️ Settings** (иконка в правом верхнем углу)
2. Прокрутите вниз до секции **MCP Servers**
3. Нажмите **➕ Add Server**
4. Заполните форму:
   - **Name**: `Git`
   - **URL**: `http://localhost:8010/sse`
   - **API Key**: оставьте пустым
   - **Enabled**: ✓ (отметьте галочку)
5. Нажмите **Save**

Git MCP Server должен подключиться (статус: **🟢 CONNECTED**)

### 4. Тестирование Strategy 3

#### Тест 1: С текущими изменениями (git_status)

1. Измените любой файл в ai-agent:
   ```bash
   echo "// test" >> ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/App.kt
   ```

2. В AI Agent введите команду: `/help`

3. Проверьте логи в терминале:
   ```
   [ProjectRootProvider] Working directory: .../day-21-developer-assistant
   [ProjectRootProvider] Trying git_status via MCP...
   [ProjectRootProvider] git_status result: On branch master...
   [ProjectRootProvider] Found 1 modified files from git_status
   [ProjectRootProvider] Git MCP detected working directory: .../ai-agent
   [ProjectRootProvider] Found README.md in Git working directory
   ```

4. README должен быть из `ai-agent/README.md` ✅

#### Тест 2: Без изменений (git_log)

1. Откатите изменения:
   ```bash
   git restore ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/App.kt
   ```

2. В AI Agent введите: `/help`

3. Проверьте логи:
   ```
   [ProjectRootProvider] No modified files in git_status, trying git_log...
   [ProjectRootProvider] Trying git_log via MCP...
   [ProjectRootProvider] Found X files from last commit
   [ProjectRootProvider] Git MCP detected working directory: ...
   ```

4. README будет из директории последнего коммита ✅

#### Тест 3: MCP недоступен (фоллбэк)

1. Остановите Git MCP Server (Ctrl+C в терминале)

2. В AI Agent отключите Git MCP Server:
   - Settings → MCP Servers → Git → снимите галочку Enabled

3. В AI Agent введите: `/help`

4. Проверьте логи:
   ```
   [ProjectRootProvider] Git MCP strategy failed: ..., trying other strategies...
   [ProjectRootProvider] Found Git root: .../day-21-developer-assistant
   [ProjectRootProvider] Found README.md in Git root
   ```

5. README будет из корня репозитория (Strategy 2) ✅

## Ожидаемое поведение

### Сценарий A: Работа в ai-agent/
- Изменённые файлы: `ai-agent/**/*.kt`
- **Результат**: README из `ai-agent/README.md`

### Сценарий B: Работа в корне
- Изменённые файлы: `README.md`, `build.gradle.kts`
- **Результат**: README из корня проекта

### Сценарий C: Работа в chat/
- Изменённые файлы: `chat/**/*.kt`
- **Результат**: README из `chat/` (если есть) или корня

### Сценарий D: Последний коммит в shared/
- Нет изменений, последний коммит изменил `shared/**/*.kt`
- **Результат**: README из `shared/` или корня

## Проверка логов

Все логи имеют префикс `[ProjectRootProvider]`. Ищите их в терминале, где запущен AI Agent:

```bash
# Отфильтровать только логи ProjectRootProvider
./gradlew :ai-agent:run 2>&1 | grep ProjectRootProvider
```

## Проблемы и решения

### Git MCP Server не подключается

**Проблема**: Status показывает 🔴 ERROR или 🔵 DISCONNECTED

**Решение**:
1. Проверьте, что сервер запущен: `curl http://localhost:8010/tools`
2. Проверьте URL в настройках (должен быть `/sse` в конце)
3. Перезапустите сервер и переподключите в Settings

### Strategy 3 не срабатывает

**Проблема**: В логах нет `"Git MCP detected working directory"`

**Решение**:
1. Убедитесь, что Git MCP Server подключён (статус CONNECTED)
2. Проверьте, что есть изменённые файлы или коммиты
3. Посмотрите логи на предмет ошибок парсинга

### README не тот, который ожидался

**Проблема**: Открывается README из другой директории

**Решение**:
1. Проверьте логи - какая стратегия сработала
2. Если Strategy 1, проверьте `git status` вручную: `git status --short`
3. Убедитесь, что изменённые файлы находятся в нужной директории

## Расширенное тестирование

### Тест парсинга различных форматов

```bash
# Создать разные типы изменений
echo "test" >> new_file.txt           # Untracked
git add some_file.kt                  # Staged
echo "change" >> another_file.kt      # Modified

# Проверить, что все распознаны
./gradlew :ai-agent:run
# Введите /help и проверьте логи
```

### Тест с множественными директориями

```bash
# Изменить файлы в разных модулях
echo "test" >> ai-agent/App.kt
echo "test" >> shared/Api.kt

# Ожидаемый результат: общая директория = корень
```

## Метрики производительности

Засеките время выполнения:

```bash
time git status --short  # Baseline
# Обычно: ~50-100ms

time git log -1  # Baseline
# Обычно: ~100-200ms
```

Strategy 3 должна быть сопоставима с этими временами (150-300ms total).

## Отладка

Для детального логирования добавьте в код (временно):

```kotlin
// ProjectRootProvider.desktop.kt
println("[DEBUG] MCP Repository available: ${mcpRepository != null}")
println("[DEBUG] Status result: $statusResult")
println("[DEBUG] Modified files: $modifiedFiles")
```

Перекомпилируйте и запустите:
```bash
./gradlew :ai-agent:compileKotlinDesktop
./gradlew :ai-agent:run
```
