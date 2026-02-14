# Исправление: Review больших Pull Request

## Проблема

При попытке выполнить `/review-pr 2` AI Agent получал ошибку:

```
GitHub API returned 406: {"message":"Sorry, the diff exceeded the maximum number of lines (20000)"...}
```

GitHub API отказывается отдавать diff для PR, если он содержит больше 20000 строк.

## Решение

Добавлена обработка ошибки "diff too large" с автоматическим переключением на альтернативный метод:

### Изменения в `CommandHandler.kt`

1. **Метод `prefetchReviewData`** (строки 251-285):
   - При ошибке `github_pr_diff` (406) использует `github_pr_files` для получения списка файлов
   - Строит минимальный diff-summary из списка файлов
   - Читает полное содержимое каждого файла для review

2. **Новый метод `parseChangedFilesFromPrFiles`** (строки 232-240):
   - Парсит вывод инструмента `github_pr_files`
   - Извлекает пути к файлам из формата: `[status] path/to/file.kt (+X -Y)`

3. **Новый метод `buildMinimalDiffFromFiles`** (строки 242-256):
   - Создает информативный summary для больших PR
   - Указывает, что полное содержимое файлов будет предоставлено для review

## Как использовать

### 1. Убедитесь, что GITHUB_TOKEN настроен

```bash
# Проверьте, установлен ли токен
echo $GITHUB_TOKEN

# Если нет, создайте GitHub Personal Access Token:
# https://github.com/settings/tokens
# Права: repo (или минимум repo:status, public_repo)

# Установите токен
export GITHUB_TOKEN="ghp_ваш_токен_здесь"

# Для сохранения между сессиями добавьте в ~/.zshrc или ~/.bashrc
echo 'export GITHUB_TOKEN="ghp_ваш_токен_здесь"' >> ~/.zshrc
source ~/.zshrc
```

### 2. Перезапустите Git MCP Server

```bash
cd mcp-servers
# Остановите текущий сервер (Ctrl+C)
./START.sh

# При запуске вы должны увидеть:
# ✓ GITHUB_TOKEN is set — git push/pull will authenticate to GitHub
```

### 3. Запустите AI Agent

```bash
./gradlew :ai-agent:run
```

### 4. Выполните review

Для PR (требует GitHub токен):
```
/review-pr 2
/review-pr 3
```

Для локальных изменений (токен не требуется):
```
/review-pr
```

## Как это работает

### Для небольших PR (< 20000 строк):

1. `github_pr_diff` → получает полный diff
2. Парсит список файлов из diff
3. Читает содержимое каждого файла
4. Отправляет все данные в LLM для review

### Для больших PR (> 20000 строк):

1. `github_pr_diff` → **ошибка 406**
2. **Fallback:** `github_pr_files` → получает список файлов
3. Создает минимальный diff-summary
4. Читает содержимое каждого файла
5. Отправляет все данные в LLM для review

## Ограничения

- GitHub API лимит: 20000 строк для diff
- AI Agent лимит файлов: `AgentConfig.MAX_REVIEW_FILES_WITH_CONTENT` (по умолчанию 20)
- AI Agent лимит размера: `AgentConfig.MAX_REVIEW_TOTAL_FILES_CHARS` (по умолчанию 150000 символов)

Если PR содержит больше файлов или символов, чем лимиты AI Agent, будут прочитаны только первые N файлов.

## Проверка

После применения изменений:

```bash
./gradlew :ai-agent:build
./gradlew :ai-agent:run

# В AI Agent:
/review-pr 3
```

Вы должны увидеть:
- Для PR #3 (маленький): нормальный diff + содержимое файлов
- Для PR #2 (большой): "Large PR - Diff Summary" + содержимое файлов

## Доступные PR в репозитории

- **PR #2**: Большой PR (>20000 строк) - будет использовать fallback
- **PR #3**: Небольшой PR - будет работать через обычный diff

## Дальнейшие улучшения

Возможные улучшения в будущем:

1. **Incremental review**: разбивать большие PR на части
2. **Smart file filtering**: анализировать только измененные строки, не весь файл
3. **Caching**: кешировать содержимое файлов между запросами
4. **Progress indicator**: показывать прогресс чтения файлов для больших PR
