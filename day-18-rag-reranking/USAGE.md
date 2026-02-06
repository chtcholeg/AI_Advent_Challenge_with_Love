# Re-ranking в RAG: примеры использования

## Что такое Re-ranking

Re-ranking (повторное ранжирование) -- это вторая стадия поиска в RAG-пайплайне, которая фильтрует и переупорядочивает результаты, полученные из векторного хранилища. Цель -- оставить только по-настоящему релевантные фрагменты документов, прежде чем подставлять их в промпт LLM.

В этом проекте реализован **двухстадийный поиск**:

1. **Стадия 1 (широкий поиск)** -- извлекаем большой набор кандидатов из векторного хранилища с мягким порогом.
2. **Стадия 2 (строгая фильтрация)** -- применяем три последовательных фильтра:
   - Порог схожести (similarity threshold)
   - Обнаружение разрыва в оценках (score-gap detection)
   - Ограничение количества результатов (final top-K)

---

## Архитектура пайплайна

```
Запрос пользователя
       │
       ▼
┌──────────────────────────────┐
│ Генерация эмбеддинга запроса │
│ EmbeddingService (GigaChat)  │
│ → вектор из 1024 измерений   │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│ Стадия 1: Широкий векторный поиск    │
│ vectorStore.search(                  │
│   embedding,                         │
│   topK = initialTopK,   // default 10│
│   threshold = 0.3       // мягкий    │
│ )                                    │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│ Стадия 2a: Порог схожести            │
│ filter { similarity >= 0.5 }         │
│ Убираем слабо релевантные результаты │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│ Стадия 2b: Обнаружение разрыва       │
│ Если sim[i] - sim[i+1] > 0.15,      │
│ отсекаем всё начиная с i+1           │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│ Стадия 2c: Финальный Top-K           │
│ take(finalTopK)  // default 3        │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│ Форматирование контекста             │
│ Подстановка в системный промпт LLM   │
│ <context>...чанки...</context>       │
└──────────────────────────────────────┘
```

---

## Пример 1: Базовый RAG без re-ranking

Когда re-ranker выключен, выполняется **одностадийный поиск**: просто берём top-5 результатов с порогом 0.3.

### Настройки

| Параметр | Значение |
|----------|----------|
| RAG Mode | ON |
| Reranker | OFF |
| Index Path | `~/.indexer/index.db` |

### Код (AgentStore)

```kotlin
// Одностадийный поиск
val chunks = ragRepository.getRelevantChunks(content)
summary = "Found ${chunks.size} relevant chunk(s):\n" +
    ragRepository.formatChunksSummary(chunks)
```

### Результат в UI

```
Found 5 relevant chunk(s):
  · architecture.md [chunk 2] sim=0.89
  · architecture.md [chunk 5] sim=0.84
  · setup-guide.md [chunk 1] sim=0.71
  · faq.md [chunk 3] sim=0.58
  · changelog.md [chunk 12] sim=0.35
```

Проблема: последние результаты (sim=0.58, sim=0.35) могут быть нерелевантны и **засорять контекст** LLM.

---

## Пример 2: RAG с re-ranking

При включённом re-ranker результаты проходят строгую фильтрацию.

### Настройки

| Параметр | Значение | Описание |
|----------|----------|----------|
| RAG Mode | ON | Включить RAG |
| Reranker | ON | Включить двухстадийный поиск |
| Stage 1 Top-K | 10 | Кандидатов на первой стадии |
| Similarity Threshold | 0.50 | Минимальная схожесть (стадия 2) |
| Score Gap Threshold | 0.15 | Порог разрыва между соседними оценками |
| Final Top-K | 3 | Максимум результатов в финале |

### Код (AgentStore)

```kotlin
if (settings.rerankerEnabled) {
    val rerankerResult = ragRepository.getRelevantChunksWithReranking(
        query = content,
        initialTopK = settings.ragInitialTopK,      // 10
        finalTopK = settings.ragFinalTopK,           // 3
        rerankerThreshold = settings.rerankerThreshold, // 0.5
        scoreGapThreshold = settings.scoreGapThreshold  // 0.15
    )
    chunksForContext = rerankerResult.rerankedResults
    summary = ragRepository.formatRerankerReport(rerankerResult)
}
```

### Результат в UI (отчёт re-ranker)

```
── Stage 1: Vector Search ──
Retrieved 10 candidate(s):
  · architecture.md [chunk 2] sim=0.89
  · architecture.md [chunk 5] sim=0.84
  · setup-guide.md [chunk 1] sim=0.71
  · faq.md [chunk 3] sim=0.58
  · api-docs.md [chunk 7] sim=0.52
  · changelog.md [chunk 12] sim=0.35
  · readme.md [chunk 1] sim=0.33
  · notes.md [chunk 4] sim=0.32
  · todo.md [chunk 2] sim=0.31
  · misc.md [chunk 1] sim=0.30

── Stage 2: Reranking ──
Removed by threshold: 5
Removed by score gap: 0
Kept 3 result(s):
  · architecture.md [chunk 2] sim=0.89
  · architecture.md [chunk 5] sim=0.84
  · setup-guide.md [chunk 1] sim=0.71
```

В итоге LLM получает контекст только из 3 самых релевантных чанков вместо 10.

---

## Пример 3: Score-gap отсечение в действии

Score-gap детектор находит "обрыв" в последовательности оценок -- резкое падение схожести между соседними результатами.

### Сценарий

Запрос: *"Как настроить MCP-сервер?"*

Стадия 1 вернула 8 кандидатов:

| # | Документ | Similarity |
|---|----------|-----------|
| 1 | mcp-setup.md [chunk 1] | 0.92 |
| 2 | mcp-setup.md [chunk 3] | 0.88 |
| 3 | mcp-config.md [chunk 2] | 0.85 |
| 4 | server-api.md [chunk 5] | 0.67 |
| 5 | faq.md [chunk 8] | 0.55 |
| 6 | readme.md [chunk 1] | 0.52 |
| 7 | changelog.md [chunk 3] | 0.40 |
| 8 | notes.md [chunk 1] | 0.35 |

### Как работает фильтрация

**Стадия 2a (порог 0.5):**
Убираем #7 (0.40) и #8 (0.35). Осталось 6 результатов.

**Стадия 2b (score-gap 0.15):**
Проверяем разницу между соседями:
- 0.92 → 0.88: разница 0.04 (ОК)
- 0.88 → 0.85: разница 0.03 (ОК)
- 0.85 → 0.67: разница **0.18** > 0.15 -- **ОТСЕЧЕНИЕ!**

Всё после #3 отбрасывается. Осталось 3 результата.

**Стадия 2c (final top-K = 3):**
Уже 3 результата -- ничего не отсекается.

### Отчёт

```
── Stage 1: Vector Search ──
Retrieved 8 candidate(s):
  · mcp-setup.md [chunk 1] sim=0.92
  · mcp-setup.md [chunk 3] sim=0.88
  · mcp-config.md [chunk 2] sim=0.85
  · server-api.md [chunk 5] sim=0.67
  · faq.md [chunk 8] sim=0.55
  · readme.md [chunk 1] sim=0.52
  · changelog.md [chunk 3] sim=0.40
  · notes.md [chunk 1] sim=0.35

── Stage 2: Reranking ──
Removed by threshold: 2
Removed by score gap: 3
Kept 3 result(s):
  · mcp-setup.md [chunk 1] sim=0.92
  · mcp-setup.md [chunk 3] sim=0.88
  · mcp-config.md [chunk 2] sim=0.85
```

Score-gap детектор обнаружил естественную границу между "ядром релевантности" и "шумовыми" результатами.

---

## Пример 4: Подстановка контекста в промпт LLM

После фильтрации оставшиеся чанки форматируются и вставляются в системный промпт.

### Форматирование контекста (RagRepository)

```kotlin
fun formatContext(chunks: List<SearchResult>): String {
    return chunks.joinToString("\n---\n") { result ->
        "[${result.chunk.metadata.source}, " +
        "chunk ${result.chunk.metadata.chunkIndex}/${result.chunk.metadata.totalChunks}, " +
        "sim=${"%.2f".format(result.similarity)}]\n" +
        result.chunk.text
    }
}
```

### Пример сформированного контекста

```
[mcp-setup.md, chunk 1/5, sim=0.92]
Для настройки MCP-сервера создайте файл конфигурации с параметрами подключения.
Укажите имя сервера, команду запуска и аргументы...
---
[mcp-setup.md, chunk 3/5, sim=0.88]
После запуска сервера убедитесь, что инструменты загрузились.
Список доступных инструментов можно увидеть в разделе Tools...
---
[mcp-config.md, chunk 2/4, sim=0.85]
Пример конфигурации: command = "python", args = ["server.py", "--port", "8080"]...
```

### Системный промпт с RAG (AgentRepository)

```
Для ответа на вопрос пользователя используй следующий контекст из документов.
Если контекст содержит нужную информацию -- опирайся на него.
Если нет -- ответь на основе своих знаний.

<context>
[mcp-setup.md, chunk 1/5, sim=0.92]
Для настройки MCP-сервера создайте файл конфигурации...
---
[mcp-setup.md, chunk 3/5, sim=0.88]
После запуска сервера убедитесь...
---
[mcp-config.md, chunk 2/4, sim=0.85]
Пример конфигурации: command = "python"...
</context>
```

---

## Пример 5: Случай без релевантных результатов

Если ни один чанк не прошёл фильтрацию, LLM отвечает на основе собственных знаний.

### Сценарий

Запрос: *"Какая погода завтра?"* (документы содержат только техническую документацию)

### Результат

```
No relevant chunks found. Answering without document context.
```

RAG-контекст не подставляется в промпт, и LLM работает как обычно.

---

## Настройка параметров

### Через UI (SettingsScreen)

При включённом RAG в настройках появляется секция **Reranker / Relevance Filter**:

| Параметр | Ползунок | Значение по умолчанию | Диапазон |
|----------|----------|-----------------------|----------|
| Enable Reranker | Переключатель | OFF | ON/OFF |
| Stage 1 -- Candidates (Top-K) | Ползунок | 10 | 3--30 |
| Stage 2 -- Similarity Threshold | Ползунок | 0.50 | 0.10--0.90 |
| Score Gap Threshold | Ползунок | 0.15 | 0.02--0.50 |
| Final Results (Top-K) | Ползунок | 3 | 1--10 |

### Рекомендации по настройке

| Сценарий | Initial Top-K | Threshold | Score Gap | Final Top-K |
|----------|:---:|:---:|:---:|:---:|
| Точные ответы (узкая тема) | 10 | 0.60 | 0.15 | 2 |
| Сбалансированный режим | 10 | 0.50 | 0.15 | 3 |
| Широкий контекст (обзорные вопросы) | 20 | 0.35 | 0.10 | 5 |
| Малый индекс (< 50 чанков) | 5 | 0.40 | 0.20 | 3 |

---

## Ключевые файлы

| Файл | Назначение |
|------|-----------|
| `ai-agent/.../data/repository/RagRepository.kt` | Логика re-ranking и форматирование |
| `ai-agent/.../presentation/agent/AgentStore.kt` | Оркестрация RAG-пайплайна |
| `ai-agent/.../data/repository/AgentRepository.kt` | Подстановка контекста в промпт |
| `ai-agent/.../domain/model/AiSettings.kt` | Параметры re-ranking |
| `ai-agent/.../presentation/settings/SettingsScreen.kt` | UI-настройки re-ranking |
| `ai-agent/.../data/repository/SettingsRepository.kt` | Сохранение настроек |
| `shared/.../domain/service/VectorStore.kt` | Интерфейс векторного поиска |
| `shared/.../domain/service/EmbeddingServiceImpl.kt` | Генерация эмбеддингов (GigaChat) |
