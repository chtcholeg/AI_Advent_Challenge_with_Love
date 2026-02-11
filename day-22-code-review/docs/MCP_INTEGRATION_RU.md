# Руководство по интеграции: Git MCP Server с AI Agent

Данное руководство описывает процесс интеграции Git MCP Server с приложением AI Agent.

## Предварительные требования

- Приложение AI Agent собрано и запущено
- Git MCP Server установлен и запущен
- Базовое понимание протокола MCP

## Обзор архитектуры

```
┌─────────────────┐         HTTP/SSE         ┌──────────────────┐
│   AI Agent      │◄─────────────────────────┤  Git MCP Server  │
│   (Kotlin/KMP)  │                          │  (Python/FastAPI)│
└─────────────────┘                          └──────────────────┘
        │                                            │
        │ Сообщения протокола MCP                    │
        │ (JSON-RPC 2.0)                             │
        │                                            │
        ▼                                            ▼
┌─────────────────┐                          ┌──────────────────┐
│  McpRepository  │                          │   Git Commands   │
│  McpClient      │                          │   (subprocess)   │
└─────────────────┘                          └──────────────────┘
```

## Пошаговая интеграция

### 1. Запустите Git MCP Server

```bash
cd mcp-servers
source venv/bin/activate  # Если еще не активирован
python -m git.main --no-auth
```

По умолчанию сервер запускается на `http://localhost:8010`.

### 2. Проверьте работоспособность сервера

```bash
# Проверка состояния
curl http://localhost:8010/health

# Ожидаемый ответ:
{
  "status": "healthy",
  "repository": "/path/to/current/repo"
}
```

### 3. Добавьте сервер в интерфейсе AI Agent

#### Вариант A: Через интерфейс (рекомендуется)

1. Запустите приложение AI Agent:
   ```bash
   cd ai-agent
   ./gradlew :ai-agent:run
   ```

2. Перейдите в **Settings → MCP Servers**

3. Нажмите кнопку **"Add Server"**

4. Заполните данные сервера:
   - **Name:** `Git`
   - **Description:** `Git repository operations` (необязательно)
   - **URL:** `http://localhost:8010/sse`
   - **API Key:** (оставьте пустым для режима `--no-auth`)

5. Нажмите **"Save"**

6. Переключите сервер в состояние **Enabled**

#### Вариант B: Через базу данных (продвинутый)

Если необходимо добавить сервер программно:

```kotlin
// В вашем коде
val mcpRepository = get<McpRepository>()

val gitServer = McpServer(
    id = UUID.randomUUID().toString(),
    name = "Git",
    url = "http://localhost:8010/sse",
    apiKey = "", // Пустой для режима без аутентификации
    enabled = true,
    status = ConnectionStatus.DISCONNECTED
)

mcpRepository.addServer(gitServer)
```

### 4. Проверьте подключение

После добавления сервера:

1. Проверьте список MCP-серверов в настройках
2. Git-сервер должен отображать статус: **Connected** (зеленый)
3. Если статус **Error** (красный), проверьте логи сервера

### 5. Протестируйте выполнение инструментов

В чате AI Agent:

```
Пользователь: What's the git status?

Ожидается: ИИ вызывает инструмент git_status и отображает статус репозитория
```

## Доступные инструменты

Git MCP Server предоставляет следующие инструменты:

### Инструменты только для чтения (безопасные)

| Инструмент | Описание | Пример |
|------------|----------|--------|
| `git_status` | Показать статус репозитория | "What's the current status?" |
| `git_log` | Показать историю коммитов | "Show last 5 commits" |
| `git_diff` | Показать изменения | "What files changed?" |
| `git_branch_list` | Показать список веток | "What branches exist?" |
| `git_show_commit` | Показать детали коммита | "Show commit abc123" |
| `git_blame` | Показать авторов строк | "Who wrote config.py line 42?" |

### Инструменты записи (используйте с осторожностью)

| Инструмент | Описание | Пример |
|------------|----------|--------|
| `git_add` | Индексировать файлы | "Stage all changes" |
| `git_commit` | Создать коммит | "Commit with message 'Fix bug'" |
| `git_checkout` | Переключить/создать ветку | "Switch to main branch" |
| `git_pull` | Получить изменения с удаленного репозитория | "Pull latest changes" |
| `git_push` | Отправить на удаленный репозиторий | "Push to origin" |

## Параметры конфигурации

### Конфигурация сервера

Отредактируйте `mcp-servers/git/config.py` или используйте переменные окружения:

```bash
# Путь к репозиторию
export GIT_REPO_PATH="/path/to/repo"

# Хост/порт сервера
export HOST="0.0.0.0"
export PORT="8010"

# Аутентификация
export MCP_API_KEY="your-secret-key"
export NO_AUTH="false"

# Ограничения
export GIT_MAX_LOG_ENTRIES="100"
export GIT_MAX_DIFF_LINES="1000"
```

### Конфигурация AI Agent

MCP-клиент AI Agent настраивается в:
- `ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/data/repository/McpRepositoryImpl.kt`
- База данных: `~/.ai-agent/mcp.db` (SQLDelight)

## Устранение неполадок

### Сервер не подключается

**Симптом:** Сервер отображает статус "Error" в AI Agent

**Решения:**

1. Убедитесь, что сервер запущен:
   ```bash
   curl http://localhost:8010/health
   ```

2. Проверьте URL в настройках AI Agent (должен быть `http://localhost:8010/sse`)

3. Проверьте логи сервера на наличие ошибок:
   ```bash
   python -m git.main --no-auth
   # Следите за выводом в консоли
   ```

4. Перезапустите сервер и AI Agent

### Инструменты не отображаются

**Симптом:** Git-сервер подключен, но инструменты недоступны

**Решения:**

1. Убедитесь, что инструменты зарегистрированы:
   ```bash
   curl http://localhost:8010/tools | python3 -m json.tool
   ```

2. Проверьте логи AI Agent на наличие ошибок получения списка инструментов

3. Переподключите сервер:
   - Отключите сервер в настройках
   - Подождите 2 секунды
   - Включите сервер снова

### Ошибка выполнения инструмента

**Симптом:** Вызовы инструментов возвращают ошибки

**Решения:**

1. Убедитесь, что Git установлен:
   ```bash
   git --version
   ```

2. Проверьте доступность репозитория:
   ```bash
   cd <repo-path>
   git status
   ```

3. Проверьте права доступа (особенно для операций записи)

4. Просмотрите логи сервера для получения подробных сообщений об ошибках

### Проблемы с аутентификацией

**Симптом:** Ошибки 401 Unauthorized

**Решения:**

1. При использовании `--no-auth` убедитесь, что поле API Key в AI Agent пустое

2. При использовании аутентификации:
   - Убедитесь, что переменная `MCP_API_KEY` установлена на сервере
   - Убедитесь, что тот же ключ введен в настройках AI Agent
   - Формат ключа: только значение ключа (без префикса "Bearer ")

## Продвинутое использование: другой репозиторий

Для работы с другим репозиторием:

### Временно (только для текущей сессии)

```bash
# Запуск сервера с указанием другого репозитория
python -m git.main --repo-path /path/to/other/repo --no-auth
```

### Постоянно (переменная окружения)

```bash
# Добавьте в ~/.bashrc или ~/.zshrc
export GIT_REPO_PATH="/path/to/repo"

# Перезапустите сервер
python -m git.main --no-auth
```

### Отдельный экземпляр для каждого репозитория

Запустите несколько Git-серверов для разных репозиториев:

```bash
# Сервер 1: Проект A (порт 8010)
GIT_REPO_PATH="/path/to/project-a" python -m git.main --no-auth --port 8010

# Сервер 2: Проект B (порт 8011)
GIT_REPO_PATH="/path/to/project-b" python -m git.main --no-auth --port 8011
```

Добавьте оба в AI Agent с разными именами:
- Git Project A: `http://localhost:8010/sse`
- Git Project B: `http://localhost:8011/sse`

## Лучшие практики безопасности

### Разработка

Для локальной разработки режим `--no-auth` допустим:
```bash
python -m git.main --no-auth
```

### Продакшен

Для продакшена или общих сред:

1. **Включите аутентификацию:**
   ```bash
   export MCP_API_KEY="$(openssl rand -base64 32)"
   python -m git.main
   ```

2. **Используйте HTTPS (для удаленного доступа):**
   - Разместите сервер за обратным прокси (nginx, caddy)
   - Используйте SSL-сертификаты
   - Обновите URL в AI Agent: `https://your-domain.com/sse`

3. **Ограничьте операции:**
   - Рассмотрите режим только для чтения (удалите инструменты записи)
   - Используйте отдельный репозиторий для тестирования
   - Запускайте с ограниченными правами пользователя

4. **Сетевая безопасность:**
   - Привяжите только к localhost: `--host 127.0.0.1`
   - Используйте правила файрвола
   - VPN для удаленного доступа

## Пример: полный рабочий процесс

### Настройка

```bash
# Терминал 1: Запуск Git MCP Server
cd mcp-servers
source venv/bin/activate
python -m git.main --no-auth

# Терминал 2: Запуск AI Agent
cd ai-agent
./gradlew :ai-agent:run
```

### В интерфейсе AI Agent

1. Добавьте Git-сервер (см. шаг 3 выше)
2. Включите сервер
3. Дождитесь статуса "Connected"

### Использование

```
Пользователь: Check git status

ИИ: [Вызывает git_status]
On branch main
Your branch is up to date with 'origin/main'.
nothing to commit, working tree clean

Пользователь: Show last 3 commits

ИИ: [Вызывает git_log с max_count=3]
commit abc123...
Author: John Doe
...

Пользователь: Create branch feature/new-tool

ИИ: [Вызывает git_checkout с branch="feature/new-tool", create=true]
Switched to a new branch 'feature/new-tool'

Пользователь: Stage all changes and commit

ИИ: [Вызывает git_add с paths=["."]]
    [Вызывает git_commit с message="..."]
Файлы проиндексированы и коммит создан успешно
```

## Мониторинг

### Логи сервера

Просмотр логов сервера в реальном времени:
```bash
python -m git.main --no-auth | tee git-mcp.log
```

### Логи AI Agent

Проверьте консольный вывод AI Agent на наличие:
- Событий подключения MCP
- Запросов на выполнение инструментов
- Сообщений об ошибках

### Проверка состояния

Автоматический мониторинг состояния:
```bash
# Простой скрипт
while true; do
  curl -s http://localhost:8010/health || echo "Сервер недоступен!"
  sleep 30
done
```

## Дальнейшие шаги

- Изучите другие MCP-серверы (FileOps, Docker и т.д.)
- Реализуйте собственные рабочие процессы Git
- Добавьте аутентификацию для продакшена
- Создайте MCP-сервер для ваших собственных сервисов

## Поддержка

При возникновении проблем или вопросов:
- Проверьте логи сервера
- Изучите код MCP-клиента AI Agent
- Протестируйте с помощью `curl` для локализации проблемы
- Смотрите [README.md](README.md) и [git/README.md](git/README.md)
