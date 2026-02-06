# Clickable Sources - URL Opening Feature

## Описание

Теперь при клике на источник в AI Agent, если источник ссылается на web-страницу (URL), эта страница автоматически откроется в браузере.

## Как это работает

### 1. Индексирование web-страниц

Для начала нужно проиндексировать web-страницы. Это можно сделать несколькими способами:

#### CLI (Command Line)

```bash
# Индексировать одну URL
./gradlew :shared:runIndexing --args="indexUrl 'https://example.com' ./index.db"

# Индексировать несколько URL
./gradlew :shared:runIndexing --args="indexUrls 'https://example.com' 'https://another.com' ./index.db"
```

#### Indexer GUI

```bash
# Запустить GUI индексатор
./gradlew :indexer:run

# В интерфейсе:
# 1. Выберите режим "URL"
# 2. Введите URL веб-страницы
# 3. Нажмите "Index"
```

### 2. Использование в AI Agent

```bash
# Запустить AI Agent
./gradlew :ai-agent:run
```

**Настройка RAG:**
1. Перейдите в Settings (⚙️)
2. Включите "RAG Mode" → ON
3. Укажите путь к индексу (например, `./index.db`)
4. (Опционально) Настройте Reranker для лучшей фильтрации результатов

**Использование:**
1. Задайте вопрос, связанный с проиндексированным контентом
2. Агент найдёт релевантные фрагменты из документов
3. В ответе появятся ссылки вида `[Источник 1]`, `[Источник 2]` и т.д.
4. **Кликните по ссылке `[Источник N]`**
5. Если источник - web-страница (URL), она откроется в браузере
6. Если источник - локальный файл, он откроется в редакторе по умолчанию

## Технические детали

### Поддерживаемые платформы

| Платформа | Поведение |
|-----------|-----------|
| **Desktop (JVM)** | URL открывается в браузере по умолчанию через `Desktop.browse()` |
| **Android** | URL открывается через Intent `ACTION_VIEW` |

### Определение типа источника

`FileOpener` автоматически определяет тип источника:
- Если `path` начинается с `http://` или `https://` → открывается в браузере
- Иначе → открывается как локальный файл

### Метаданные источников

В индексе каждый документ имеет метаданные:
```kotlin
data class DocumentMetadata(
    val source: String,        // URL или file path
    val sourceType: SourceType, // FILE или URL
    val chunkIndex: Int,
    val totalChunks: Int,
    val timestamp: Long
)
```

При индексировании через:
- `indexDocument()` → `sourceType = SourceType.FILE`
- `indexUrl()` → `sourceType = SourceType.URL`

## Примеры

### Индексирование и использование

```bash
# 1. Индексируем документацию Kotlin
./gradlew :shared:runIndexing --args="indexUrl 'https://kotlinlang.org/docs/home.html' ./kotlin-docs.db"

# 2. Запускаем AI Agent
./gradlew :ai-agent:run

# 3. В Settings:
#    - RAG Mode: ON
#    - Index Path: ./kotlin-docs.db

# 4. Задаём вопрос:
#    "Что такое coroutines в Kotlin?"

# 5. В ответе появятся ссылки [Источник 1], [Источник 2]
# 6. Клик по источнику откроет страницу kotlinlang.org в браузере
```

### Смешанные источники

Можно использовать индекс с файлами и URL одновременно:

```bash
# Создать индекс
./gradlew :shared:runIndexing --args="index ./docs ./mixed-index.db md txt"

# Добавить URL в тот же индекс
./gradlew :shared:runIndexing --args="indexUrl 'https://example.com/api-docs' ./mixed-index.db"
```

При клике на источник:
- Локальные файлы откроются в редакторе
- URL откроются в браузере

## Код

Реализация находится в:
- `ai-agent/src/desktopMain/kotlin/ru/chtcholeg/agent/util/FileOpener.desktop.kt`
- `ai-agent/src/androidMain/kotlin/ru/chtcholeg/agent/util/FileOpener.android.kt`
- `ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/util/FileOpener.kt` (expect declaration)

## FAQ

**Q: Можно ли открыть источник по средней кнопке мыши?**
A: Пока поддерживается только левый клик.

**Q: Работает ли это в Chat модуле?**
A: Пока нет, эта функция реализована только в AI Agent модуле, который поддерживает RAG.

**Q: Можно ли добавить поддержку других протоколов (ftp://, file://)?**
A: Да, можно расширить проверку в `FileOpener`:
```kotlin
when {
    path.startsWith("http://") || path.startsWith("https://") -> // browser
    path.startsWith("file://") -> // file handler
    // ...
}
```

**Q: Что если URL больше не доступен?**
A: Браузер откроется и покажет стандартную ошибку 404 или "Page Not Found".
