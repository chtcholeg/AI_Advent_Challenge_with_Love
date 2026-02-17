# Git MCP Server - Инструкция по настройке

Краткое руководство по подключению Git MCP Server к AI Agent.

## 1. Запуск Git MCP Server

```bash
cd mcp-servers
./START.sh
```

Сервер запустится по адресу `http://localhost:8010`

## 2. Подключение к AI Agent

1. Запустите AI Agent:
   ```bash
   ./gradlew :ai-agent:run
   ```

2. В интерфейсе AI Agent:
   - Перейдите в **Settings -> MCP Servers**
   - Нажмите **"Add Server"**
   - Введите:
     - **Name:** Git
     - **URL:** `http://localhost:8010/sse`
     - **API Key:** (оставьте пустым)
   - Нажмите **"Save"**
   - Переключите сервер в состояние **Enabled**

3. Проверьте подключение:
   - Сервер должен показывать статус **Connected** (зеленый)

## 3. Начинайте работу!

Попробуйте следующие запросы в AI Agent:

```
"What's the git status?"
"Show me the last 5 commits"
"What branches do we have?"
"Show diff of src/main.kt"
```

## Доступные инструменты

### Операции чтения (безопасные)
- `git_status` - Статус репозитория
- `git_log` - История коммитов
- `git_diff` - Изменения в файлах
- `git_branch_list` - Список веток
- `git_show_commit` - Детали коммита
- `git_blame` - Авторство строк

### Операции записи (используйте с осторожностью)
- `git_add` - Добавление файлов в индекс
- `git_commit` - Создание коммита
- `git_checkout` - Переключение/создание ветки
- `git_pull` - Получение изменений
- `git_push` - Отправка изменений

## Устранение неполадок

### Сервер не запускается
```bash
# Убедитесь, что Python и Git установлены
python3 --version
git --version

# Переустановите зависимости
cd mcp-servers
rm -rf venv
./START.sh
```

### Не удается подключиться из AI Agent
```bash
# Проверьте, что сервер запущен
curl http://localhost:8010/health

# Проверьте URL в AI Agent: http://localhost:8010/sse
# Перезапустите и сервер, и AI Agent
```

### Инструменты не работают
```bash
# Убедитесь, что вы находитесь в git-репозитории
cd /path/to/your/repo
git status

# Или укажите репозиторий при запуске сервера
cd mcp-servers
python -m git.main --repo-path /path/to/repo --no-auth
```

## Документация

- [Быстрый старт (5 мин)](mcp-servers/QUICKSTART.md)
- [Полная документация](mcp-servers/README.md)
- [Руководство по интеграции](mcp-servers/INTEGRATION.md)
- [Подробности Git Server](mcp-servers/git/README.md)

## Примеры

### Проверка статуса и создание коммита
```
Пользователь: Проверь статус git, и если есть изменения, добавь их в индекс и закоммить

AI: [Вызывает git_status]
    [Обнаруживает изменения]
    [Вызывает git_add с paths=["."]]
    [Вызывает git_commit с message="..."]

    Изменения обнаружены и успешно закоммичены
```

### Создание ветки для новой функциональности
```
Пользователь: Создай новую ветку feature/mcp-git и переключись на нее

AI: [Вызывает git_checkout с branch="feature/mcp-git", create=true]

    Ветка feature/mcp-git успешно создана и выбрана как текущая
```

### Просмотр изменений
```
Пользователь: Покажи, что изменилось в последнем коммите

AI: [Вызывает git_log с max_count=1]
    [Вызывает git_show_commit с commit="HEAD"]

    Последний коммит: abc123
    Автор: John Doe
    Сообщение: Add Git MCP server

    [Показывает полный diff]
```

Готово! Теперь вы можете использовать Git MCP Server в качестве AI-ассистента разработчика.
