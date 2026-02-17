# Git MCP Server - Краткое руководство по запуску

Запустите Git MCP сервер за 5 минут!

## Предварительные требования

- Python 3.10 или выше
- Git установлен и доступен в PATH
- Git-репозиторий для работы

## Шаг 1: Установка (2 минуты)

```bash
# Перейдите в директорию mcp-servers
cd mcp-servers

# Создайте и активируйте виртуальное окружение
python3 -m venv venv
source venv/bin/activate  # На Windows: venv\Scripts\activate

# Установите зависимости
pip install -e .
```

Ожидаемый вывод:
```
Successfully installed fastapi-0.104.0 uvicorn-0.24.0 ...
```

## Шаг 2: Запуск сервера (1 минута)

```bash
# Запустите Git MCP сервер без аутентификации
python -m git.main --no-auth
```

Ожидаемый вывод:
```
============================================================
Git MCP Server Starting
============================================================
Repository path: /current/directory
Host: 0.0.0.0
Port: 8010
Authentication: DISABLED
============================================================
Registered 11 Git MCP tools
  - git_status: Show the working tree status...
  - git_log: Show commit logs...
  - git_diff: Show changes between commits...
  - git_branch_list: List all branches...
  - git_show_commit: Show information about a specific commit...
  - git_blame: Show what revision and author last modified...
  - git_add: Add file contents to the staging area...
  - git_commit: Record changes to the repository...
  - git_checkout: Switch branches or restore working tree...
  - git_pull: Fetch from and integrate with another repository...
  - git_push: Update remote refs along with associated objects...
INFO:     Started server process
INFO:     Uvicorn running on http://0.0.0.0:8010
```

## Шаг 3: Проверка сервера (30 секунд)

Откройте новый терминал и выполните проверку:

```bash
# Проверка состояния
curl http://localhost:8010/health

# Ожидаемый ответ:
{"status":"healthy","repository":"/your/repo/path"}

# Список доступных инструментов
curl http://localhost:8010/tools

# Ожидаемый результат: JSON с 11 git-инструментами
```

## Шаг 4: Добавление в AI Agent (1 минута)

1. Откройте приложение **AI Agent**
2. Перейдите в **Settings** -> **MCP Servers**
3. Нажмите **"Add Server"**
4. Заполните форму:
   - **Name:** `Git`
   - **URL:** `http://localhost:8010/sse`
   - **API Key:** *(оставьте пустым)*
5. Нажмите **"Save"**
6. **Включите** переключатель сервера

## Шаг 5: Проверьте работу! (30 секунд)

В AI Agent попробуйте следующие команды:

**Пример 1: Проверка статуса**
```
Вы: Какой текущий статус git?

AI: [Вызывает git_status]

On branch main
Your branch is up to date with 'origin/main'.

nothing to commit, working tree clean
```

**Пример 2: Просмотр истории**
```
Вы: Покажи последние 3 коммита

AI: [Вызывает git_log с max_count=3]

commit abc123...
Author: John Doe
Date: Thu Feb 9 15:30:00 2024

    Add Git MCP server

commit def456...
Author: Jane Smith
Date: Thu Feb 9 14:15:00 2024

    Update documentation
```

**Пример 3: Просмотр веток**
```
Вы: Какие у нас есть ветки?

AI: [Вызывает git_branch_list]

Current branch: main

Branches:
* main
  feature/new-tool
  dev
```

## Типичные сценарии использования

### Рабочий процесс разработки

```
Вы: Добавь все изменения в индекс и сделай коммит с сообщением "Fix bug in parser"

AI: [Вызывает git_add с paths=["."]
    [Вызывает git_commit с message="Fix bug in parser"]

Files added to staging area
Staged paths: .

Commit created successfully
Message: Fix bug in parser
[main abc123d] Fix bug in parser
 2 files changed, 15 insertions(+), 3 deletions(-)
```

### Управление ветками

```
Вы: Создай новую ветку feature/mcp-integration и переключись на неё

AI: [Вызывает git_checkout с branch="feature/mcp-integration", create=true]

Successfully created and checked out branch: feature/mcp-integration
Switched to a new branch 'feature/mcp-integration'
```

### Ревью кода

```
Вы: Покажи diff файла src/main.py

AI: [Вызывает git_diff с file_path="src/main.py"]

diff --git a/src/main.py b/src/main.py
index abc123..def456 100644
--- a/src/main.py
+++ b/src/main.py
@@ -10,6 +10,8 @@
 def main():
+    # Инициализация MCP сервера
+    init_mcp()
```

```
Вы: Кто написал строку 42 в config.py?

AI: [Вызывает git_blame с file_path="config.py"]

abc123 (John Doe 2024-02-09 14:30:00) PORT = 8010
```

## Параметры конфигурации

### Указание другого репозитория

Работа с другим репозиторием:

```bash
python -m git.main --repo-path /path/to/other/repo --no-auth
```

### Указание другого порта

Использование другого порта:

```bash
python -m git.main --port 8011 --no-auth
```

Затем обновите URL в AI Agent на: `http://localhost:8011/sse`

### С аутентификацией

Для использования в продакшене:

```bash
# Установите API-ключ
export MCP_API_KEY="your-secret-key"

# Запустите с включённой аутентификацией
python -m git.main

# В AI Agent введите API-ключ при добавлении сервера
```

## Устранение неполадок

### "Not a git repository"

```bash
# Убедитесь, что вы находитесь в git-репозитории
cd /path/to/your/git/repo

# Или укажите путь к репозиторию
python -m git.main --repo-path /path/to/repo --no-auth
```

### Порт уже занят

```bash
# Используйте другой порт
python -m git.main --port 8011 --no-auth

# Не забудьте обновить URL в AI Agent
```

### Не удаётся подключиться из AI Agent

1. Проверьте, что сервер запущен:
   ```bash
   curl http://localhost:8010/health
   ```

2. Проверьте URL в AI Agent: `http://localhost:8010/sse`

3. Проверьте логи сервера на наличие ошибок

4. Попробуйте перезапустить и сервер, и AI Agent

### Команды не работают

1. Убедитесь, что Git установлен:
   ```bash
   git --version
   ```

2. Проверьте доступность репозитория:
   ```bash
   cd <repo-path>
   git status
   ```

3. Проверьте логи сервера на наличие подробных сообщений об ошибках

## Дальнейшие шаги

- Прочитайте [git/README.md](git/README.md) для подробной документации
- Изучите все 11 доступных Git-инструментов
- Настройте аутентификацию для продакшена
- Добавьте другие MCP серверы (погода, время и т.д.)

## Советы

1. **Начните просто:** Начните с команд только для чтения (status, log, diff)
2. **Будьте конкретны:** Точно указывайте AI, что именно вы хотите увидеть
3. **Объединяйте команды:** Попросите AI выполнить несколько git-операций за раз
4. **Сначала проверьте:** Всегда проверяйте статус перед коммитом
5. **Используйте ветки:** Создавайте feature-ветки для экспериментов

## Поддержка

- Смотрите [README.md](README.md) для полной документации
- Смотрите [git/README.md](git/README.md) для подробностей о Git-сервере
- Проверяйте логи сервера для отладки
- Используйте команды `curl` для изоляции проблем

Приятной работы с Git MCP сервером!
