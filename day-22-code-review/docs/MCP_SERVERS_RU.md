# MCP-серверы для AI Agent

Коллекция MCP (Model Context Protocol) серверов для проекта AI Agent.

## Доступные серверы

### Git MCP Server (порт 8010)

Операции с Git-репозиториями через протокол MCP.

**Возможности:**
- Статус репозитория и история изменений
- Управление ветками
- Операции с коммитами
- Индексация файлов и просмотр изменений
- Операции pull/push

**Документация:** [git/README.md](git/README.md)

## Быстрый старт

### Установка

```bash
cd mcp-servers

# Создание виртуального окружения
python -m venv venv
source venv/bin/activate  # На Windows: venv\Scripts\activate

# Установка Git MCP сервера
pip install -e .
```

### Запуск Git-сервера

```bash
# Без аутентификации (рекомендуется для локальной разработки)
python -m git.main --no-auth

# Или используйте установленную команду
mcp-git --no-auth

# С указанием пути к репозиторию
mcp-git --repo-path /path/to/repo --no-auth
```

### Добавление в AI Agent

1. Запустите приложение AI Agent
2. Перейдите в **Settings → MCP Servers**
3. Нажмите **"Add Server"**
4. Введите данные сервера:
   - Name: `Git`
   - URL: `http://localhost:8010/sse`
   - API Key: (оставьте пустым при использовании `--no-auth`)
5. Нажмите **"Save"** и включите сервер

## Конфигурация

### Переменные окружения

```bash
# Настройки Git-сервера
export GIT_REPO_PATH="/path/to/repository"  # По умолчанию: текущая директория
export HOST="0.0.0.0"                        # По умолчанию: 0.0.0.0
export PORT="8010"                           # По умолчанию: 8010

# Аутентификация (опционально)
export MCP_API_KEY="your-secret-key"
export NO_AUTH="true"                        # Отключить аутентификацию
```

### Параметры командной строки

```bash
python -m git.main --help

Options:
  --host HOST          Хост для привязки (по умолчанию: 0.0.0.0)
  --port PORT          Порт для привязки (по умолчанию: 8010)
  --no-auth            Отключить аутентификацию
  --repo-path PATH     Путь к Git-репозиторию (по умолчанию: текущая директория)
```

## Примеры использования

### С AI Agent

После добавления и включения Git MCP сервера в AI Agent:

**Проверка статуса репозитория:**
```
Пользователь: Какой текущий статус git?
ИИ: [Использует инструмент git_status]
    Показывает измененные файлы, проиндексированные изменения и информацию о ветке
```

**Просмотр истории коммитов:**
```
Пользователь: Покажи последние 10 коммитов
ИИ: [Использует инструмент git_log с max_count=10]
    Отображает историю коммитов с авторами и сообщениями
```

**Индексация и коммит изменений:**
```
Пользователь: Проиндексируй все изменения и сделай коммит с сообщением "Add Git MCP server"
ИИ: [Использует git_add с paths=["."]]
    [Использует git_commit с message="Add Git MCP server"]
    Файлы проиндексированы и закоммичены успешно
```

**Создание ветки для новой функциональности:**
```
Пользователь: Создай новую ветку с именем feature/new-tool
ИИ: [Использует git_checkout с branch="feature/new-tool", create=true]
    Ветка создана и выполнен переход на неё
```

## Архитектура

### Структура проекта

```
mcp-servers/
├── shared/                 # Общие компоненты MCP
│   ├── __init__.py
│   └── models.py          # ToolResult, BaseTool
│
├── git/                   # Git MCP Server
│   ├── __init__.py
│   ├── config.py          # Конфигурация
│   ├── git_client.py      # Исполнитель Git-команд
│   ├── tools.py           # Определения MCP-инструментов
│   ├── main.py            # Сервер FastAPI
│   └── README.md          # Документация Git-сервера
│
├── requirements.txt       # Зависимости Python
├── pyproject.toml         # Конфигурация пакета
└── README.md              # Этот файл
```

### Схема работы протокола MCP

```
AI Agent                Git MCP Server              Git-репозиторий
   |                           |                           |
   |--[HTTP/SSE Connect]------>|                           |
   |<------[Endpoint URL]------|                           |
   |                           |                           |
   |--[tools/list]------------>|                           |
   |<------[Tool List]---------|                           |
   |                           |                           |
   |--[tools/call: git_status]>|--[git status]------------>|
   |                           |<------[Status Output]-----|
   |<------[Tool Result]-------|                           |
```

### Интерфейс инструментов

Каждый MCP-инструмент реализует:

```python
class GitTool(BaseTool):
    name: str              # Идентификатор инструмента
    description: str       # Описание для ИИ
    input_schema: dict     # JSON Schema для параметров

    async def execute(self, arguments: dict) -> ToolResult:
        # Выполнение git-команды
        # Возврат результата с содержимым и флагом ошибки
```

## API-эндпоинты

Все серверы предоставляют следующие эндпоинты:

| Эндпоинт | Метод | Описание |
|----------|--------|----------|
| `/` | GET | Информация о сервере и его возможностях |
| `/health` | GET | Проверка работоспособности |
| `/tools` | GET | Список доступных MCP-инструментов |
| `/sse` | GET | SSE-соединение для протокола MCP |
| `/message?sessionId=<id>` | POST | Отправка сообщений по протоколу MCP |

## Безопасность

### Аутентификация

**Разработка (рекомендуется):**
```bash
# Отключение аутентификации для локальной разработки
python -m git.main --no-auth
```

**Продакшен:**
```bash
# Включение аутентификации по API-ключу
export MCP_API_KEY="your-secret-key-here"
python -m git.main

# AI Agent должен будет предоставить ключ
```

### Доступ к репозиторию

- Сервер имеет доступ только к указанному пути репозитория
- Невозможен доступ к файлам за пределами репозитория
- Работает с правами серверного процесса
- Рекомендуется использовать операции только для чтения в недоверенных контекстах

## Устранение неполадок

### Сервер не запускается

```bash
# Проверьте, не занят ли порт
lsof -i :8010

# Используйте другой порт
python -m git.main --port 8011 --no-auth
```

### Ошибка "Not a git repository"

```bash
# Убедитесь, что вы находитесь в Git-репозитории
cd /path/to/your/git/repo
python -m git.main --no-auth

# Или укажите путь к репозиторию
python -m git.main --repo-path /path/to/repo --no-auth
```

### Проблемы с подключением в AI Agent

```bash
# Проверьте, что сервер запущен
curl http://localhost:8010/health

# Просмотрите логи сервера
python -m git.main --no-auth  # Следите за выводом в консоли

# Проверьте URL в настройках AI Agent
# URL должен быть: http://localhost:8010/sse
```

### Ошибка выполнения инструмента

```bash
# Проверьте, что git установлен
git --version

# Проверьте доступность репозитория
cd <repo-path> && git status

# Просмотрите логи сервера для получения подробной информации об ошибках
```

## Разработка

### Добавление новых серверов

Чтобы добавить новый MCP-сервер по аналогичному шаблону:

1. Создайте новую директорию: `mcp-servers/myserver/`
2. Скопируйте структуру из `git/`:
   ```
   myserver/
   ├── __init__.py
   ├── config.py
   ├── my_client.py      # Клиент для внешнего сервиса
   ├── tools.py          # Определения MCP-инструментов
   ├── main.py           # Сервер FastAPI
   └── README.md
   ```

3. Импортируйте общие компоненты:
   ```python
   from shared import ToolResult, BaseTool
   ```

4. Обновите `pyproject.toml`:
   ```toml
   [project.scripts]
   mcp-myserver = "myserver.main:main"
   ```

### Тестирование

```bash
# Проверка работоспособности Git-сервера
curl http://localhost:8010/health

# Список доступных инструментов
curl http://localhost:8010/tools

# Используйте AI Agent для тестирования выполнения инструментов
# Включите сервер и попросите ИИ выполнить git-операции
```

## Участие в разработке

При добавлении новых Git-инструментов:

1. Добавьте метод команды в `git_client.py`
2. Создайте класс инструмента в `tools.py`
3. Зарегистрируйте инструмент в `get_all_tools()`
4. Обновите документацию
5. Протестируйте с AI Agent

## Ссылки

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Документация FastAPI](https://fastapi.tiangolo.com/)
- [Документация Git](https://git-scm.com/doc)
- [AI Advent Challenge with Love](../../README.md)

## Лицензия

Часть проекта GigaChat Multiplatform Chat App.
