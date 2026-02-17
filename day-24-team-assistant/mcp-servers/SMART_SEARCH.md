# Smart Search для CRM MCP Server

## 🧠 Что это?

Умный поиск по тикетам с использованием LLM (GigaChat) для расширения запроса и улучшения релевантности.

## ✨ Возможности

### 1. **LLM Query Expansion**
Автоматическое расширение поискового запроса с помощью GigaChat:

**Пример:**
```
Запрос: "не работает авторизация"

LLM расширяет до:
- авторизация
- authentication
- логин
- вход
- login
- auth
- креденшелы
- credentials
- токен
- token
- Invalid credentials
```

### 2. **Relevance Scoring**
Результаты сортируются по релевантности:
- Совпадения в теме тикета: **+2.0 балла**
- Совпадения в описании: **+1.0 балл**

### 3. **Match Highlighting**
Показывает, какие термины были найдены:
```
📊 Релевантность: 3.0 (Matched terms: авторизация, credentials, token)
```

## 🚀 Как использовать

### Вариант 1: С LLM (Рекомендуется)

**Требования:** GigaChat API credentials

**Настройка:**
```bash
# В .env или export в терминале
export GIGACHAT_CLIENT_ID="ваш_client_id"
export GIGACHAT_CLIENT_SECRET="ваш_client_secret"
export CRM_USE_LLM_SEARCH=true  # По умолчанию true

# Запуск
cd mcp-servers
./START.sh
```

**Что вы увидите при старте:**
```
============================================================
CRM MCP Server Starting
============================================================
Data directory: /path/to/mcp-servers/crm/data
Host: 0.0.0.0
Port: 8011
Authentication: DISABLED
Smart Search: ENABLED (LLM query expansion via GigaChat)
============================================================
```

### Вариант 2: Без LLM (Fallback)

Если GigaChat credentials не предоставлены, используется обычный поиск по словам.

**Настройка:**
```bash
# Не устанавливайте GIGACHAT_* переменные
# ИЛИ явно отключите
export CRM_USE_LLM_SEARCH=false

cd mcp-servers
./START.sh
```

**Что вы увидите:**
```
Smart Search: DISABLED (no GigaChat credentials)
```

## 📊 Сравнение подходов

### Обычный поиск (без LLM)
```
Запрос: "авторизация не работает"
Ищет: ['авторизация', 'работает']
Найдено: 1 тикет
```

### Умный поиск (с LLM)
```
Запрос: "авторизация не работает"
LLM расширяет: ['авторизация', 'authentication', 'логин', 'вход', 'login',
                'auth', 'креденшелы', 'credentials', 'токен', 'token',
                'Invalid credentials', 'работает']
Найдено: 3 тикета (с релевантностью 4.0, 2.5, 1.0)
```

### Результат:
```
Найдено тикетов: 3 по запросу 'авторизация не работает'
(Отсортировано по релевантности)

1. ticket_001: Не работает авторизация [open]
   Приоритет: high | Категория: authentication
   Описание: При попытке войти в приложение получаю ошибку 'Invalid credentials'. Проверил креденшелы - они правильные. Пробовал очистить кеш - не помогло.
   📊 Релевантность: 4.0 (Matched terms: авторизация, credentials, работает)

2. ticket_006: Ошибка сборки после обновления креденшелов [resolved]
   Приоритет: medium | Категория: build
   Описание: Обновил креденшелы в local.properties, но при сборке получаю ошибку 'BuildKonfig secrets not found'.
   📊 Релевантность: 1.0 (Matched terms: креденшелы)
```

## 🔧 Настройки

### Переменные окружения

| Переменная | Описание | Значение по умолчанию |
|------------|----------|----------------------|
| `GIGACHAT_CLIENT_ID` | GigaChat Client ID | - (обязательно для LLM) |
| `GIGACHAT_CLIENT_SECRET` | GigaChat Client Secret | - (обязательно для LLM) |
| `CRM_USE_LLM_SEARCH` | Включить LLM поиск | `true` |

### Отключение LLM поиска

Если хотите использовать только простой поиск по словам:

```bash
export CRM_USE_LLM_SEARCH=false
./START.sh
```

## 🧪 Тестирование

### 1. Запустить CRM MCP Server
```bash
cd mcp-servers
export GIGACHAT_CLIENT_ID="..."
export GIGACHAT_CLIENT_SECRET="..."
./START.sh
```

### 2. Запустить AI Agent
```bash
./gradlew :ai-agent:run
```

### 3. Протестировать поиск

**В AI Agent:**
```
/support
```

**Тестовые запросы:**
```
1. "авторизация не работает"
   → Должен найти: ticket_001 (Не работает авторизация)

2. "ошибка индексирования pdf"
   → Должен найти: ticket_002 (Ошибка при индексировании документов)

3. "mcp сервер не отвечает"
   → Должен найти: ticket_003 (MCP сервер не отвечает)

4. "приложение тормозит"
   → Должен найти: ticket_005 (Приложение работает медленно)
   LLM расширит: ['приложение', 'тормозит', 'медленно', 'slow', 'performance', 'lag']
```

## 📝 Логи

### При успешном запросе с LLM:
```
2026-02-15 12:00:00,000 - __main__ - INFO - [search_tickets] Searching with query: 'авторизация не работает'
2026-02-15 12:00:00,100 - crm.search_service - INFO - [SearchService] GigaChat token obtained successfully
2026-02-15 12:00:00,500 - crm.search_service - INFO - [SearchService] Query expanded: 'авторизация не работает' → 12 terms: ['авторизация', 'authentication', 'логин', 'вход', 'login']...
2026-02-15 12:00:00,510 - crm.search_service - INFO - [SearchService] Searching with 12 terms: ['авторизация', 'authentication', ...]
2026-02-15 12:00:00,520 - crm.search_service - INFO - [SearchService] Found 3 tickets with scores
2026-02-15 12:00:00,521 - __main__ - INFO - [search_tickets] Found 3 tickets
```

### При запросе без LLM (fallback):
```
2026-02-15 12:00:00,000 - __main__ - INFO - [search_tickets] Searching with query: 'авторизация не работает'
2026-02-15 12:00:00,010 - crm.search_service - INFO - [SearchService] Searching with 2 terms: ['авторизация', 'работает']
2026-02-15 12:00:00,020 - crm.search_service - INFO - [SearchService] Found 1 tickets with scores
```

## 🎯 Преимущества LLM поиска

1. **Находит больше релевантных тикетов**
   - Понимает синонимы (auth = авторизация = login)
   - Понимает технические термины (credentials, token, API)
   - Учитывает контекст (авторизация → Invalid credentials, Token expired)

2. **Работает с естественным языком**
   - "не могу войти" → найдёт тикеты про авторизацию
   - "падает приложение" → найдёт тикеты про crashes и errors
   - "медленно работает" → найдёт тикеты про performance

3. **Сортировка по релевантности**
   - Лучшие совпадения в начале списка
   - Показывает, почему тикет релевантен

4. **Graceful fallback**
   - Если GigaChat недоступен, работает обычный поиск
   - Если API ключи не заданы, работает обычный поиск

## 🔮 Будущие улучшения

### 1. Semantic Search (Embeddings)
Использовать векторные представления для семантического поиска:
```python
# Индексировать тикеты через embeddings
# Искать по косинусному сходству
```

### 2. Hybrid Search
Комбинация BM25 + Embeddings + LLM:
```python
score = 0.4 * bm25_score + 0.4 * embedding_score + 0.2 * llm_score
```

### 3. Query Understanding
Извлечение сущностей из запроса:
```
"ticket_001 не работает" → {"ticket_id": "ticket_001", "query": "не работает"}
```

### 4. Кэширование расширенных запросов
```
"авторизация" → кэшировать расширение на 1 час
```

## 🐛 Troubleshooting

### Проблема: LLM поиск не работает

**Проверьте:**
1. GigaChat credentials заданы:
   ```bash
   echo $GIGACHAT_CLIENT_ID
   echo $GIGACHAT_CLIENT_SECRET
   ```

2. CRM_USE_LLM_SEARCH=true:
   ```bash
   echo $CRM_USE_LLM_SEARCH
   ```

3. Логи показывают "Smart Search: ENABLED":
   ```bash
   tail -f /tmp/crm-mcp.log | grep "Smart Search"
   ```

### Проблема: Слишком медленный поиск

LLM запрос добавляет ~0.5-1 секунду на поиск.

**Решение 1 - Отключить LLM:**
```bash
export CRM_USE_LLM_SEARCH=false
```

**Решение 2 - Использовать кэширование (будущая фича)**

### Проблема: GigaChat token expires

Токен кэшируется, но может истечь. Сервис автоматически получит новый токен при следующем запросе.

### Проблема: Находит слишком много нерелевантных тикетов

LLM может расширить запрос слишком агрессивно. Попробуйте:
1. Более специфичный запрос
2. Использовать прямой ticket_id или user_id
3. Использовать list_tickets с фильтрами

## 📚 Примеры использования

### Пример 1: Общий вопрос
```
User: проблема с авторизацией
AI: [Вызывает search_tickets с query="проблема с авторизацией"]
LLM: Расширяет до: авторизация, authentication, логин, credentials, token, проблема, error, issue
Result: Находит 2 тикета про авторизацию с релевантностью 4.0 и 2.0
```

### Пример 2: Технический термин
```
User: Invalid credentials error
AI: [Вызывает search_tickets с query="Invalid credentials error"]
LLM: Расширяет до: invalid, credentials, error, авторизация, логин, креденшелы, ошибка
Result: Находит ticket_001 с релевантностью 5.0
```

### Пример 3: Неясный запрос
```
User: не могу войти
AI: [Вызывает search_tickets с query="не могу войти"]
LLM: Расширяет до: войти, вход, логин, login, авторизация, authentication
Result: Находит все тикеты связанные с авторизацией
```
