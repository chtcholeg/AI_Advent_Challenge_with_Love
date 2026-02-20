# Day 23 - User Support Assistant

Мини-сервис технической поддержки с использованием RAG и MCP для AI-курса.

## 🎯 Задача

Создать ассистента технической поддержки, который:
- 📚 Использует **RAG** для ответов на основе документации и FAQ
- 🔧 Интегрируется с **CRM** через MCP для доступа к пользователям и тикетам
- 🤖 Предоставляет **персонализированные** ответы с учетом контекста

**Пример использования:**
```
Пользователь: "Почему не работает авторизация?"
Ассистент:
  1. Проверяет тикет пользователя через CRM MCP
  2. Находит решение в FAQ через RAG
  3. Отвечает с учетом тарифного плана и истории тикетов
```

## 🏗️ Архитектура

```
User Question
     │
     ▼
┌─────────────────────────────────┐
│      AI Agent                    │
│  (Support Assistant Mode)        │
├─────────────────────────────────┤
│  • System Prompt для поддержки   │
│  • RAG для FAQ                   │
│  • MCP для CRM                   │
└────────┬───────────────┬─────────┘
         │               │
         ▼               ▼
┌─────────────────┐  ┌──────────────────┐
│   RAG System    │  │  CRM MCP Server  │
├─────────────────┤  ├──────────────────┤
│ FAQ Docs:       │  │ Tools:           │
│ • auth          │  │ • get_user       │
│ • install       │  │ • get_tickets    │
│ • mcp           │  │ • search         │
│ • features      │  │ • update         │
│ • errors        │  │                  │
│                 │  │ Data:            │
│ Embeddings:     │  │ • users.json     │
│ • GigaChat      │  │ • tickets.json   │
│ • Ollama        │  │                  │
└─────────────────┘  └──────────────────┘
```

## 📦 Компоненты

### 1. FAQ Документация (`support-docs/faq/`)

Проиндексированная база знаний:
- **authentication.md** - проблемы авторизации и креденшелов
- **installation.md** - установка и запуск приложения
- **mcp-servers.md** - настройка и использование MCP серверов
- **features.md** - использование функций (RAG, сессии, напоминания)
- **errors.md** - распространенные ошибки и их решения

### 2. CRM MCP Server (`mcp-servers/crm/`)

Python MCP сервер с инструментами:
- **get_user** - информация о пользователе
- **list_users** - список пользователей с фильтрацией
- **get_ticket** - детали тикета
- **list_tickets** - список тикетов с фильтрацией
- **update_ticket_status** - обновление статуса и заметок
- **search_tickets** - поиск по ключевым словам
- **get_user_tickets** - все тикеты пользователя с контекстом

**Данные:**
- `data/users.json` - 5 тестовых пользователей
- `data/tickets.json` - 7 тестовых тикетов

### 3. Конфигурация (`support-docs/config/`)

- **support-assistant-prompt.md** - System Prompt для режима поддержки
- **mcp-config.json** - конфигурация MCP серверов

### 4. Документация

- **SETUP_GUIDE.md** - полное руководство по настройке
- **USAGE_GUIDE.md** - примеры использования
- **TEST_SCENARIOS.md** - тестовые сценарии

## 🚀 Quick Start

### 1. Индексирование FAQ

```bash
# С GigaChat embeddings
export GIGACHAT_CLIENT_ID="your_client_id"
export GIGACHAT_CLIENT_SECRET="your_client_secret"
./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-index.json md"

# Или с Ollama (локально)
ollama serve
./gradlew :indexer:run
# В GUI выберите support-docs/faq → index
```

### 2. Запуск CRM MCP Server

```bash
cd mcp-servers/crm
./START.sh
```

### 3. Настройка AI Agent

```bash
# Скопировать конфигурацию MCP
mkdir -p ~/.ai-agent
cp support-docs/config/mcp-config.json ~/.ai-agent/

# Запустить AI Agent
./gradlew :ai-agent:run
```

### 4. Настройка в UI

1. **System Prompt:**
   - Settings → System Prompt
   - Скопировать из `support-docs/config/support-assistant-prompt.md`

2. **RAG:**
   - Settings → RAG Mode: Enable
   - Index Path: `./support-index.json`

3. **MCP:**
   - Settings → MCP Servers → Enable "crm"

### 5. Тестирование

```
Вопрос: "Почему не работает авторизация?"
Вопрос: "Расскажи про пользователя user_001"
Вопрос: "У user_001 проблема с авторизацией. Помоги решить."
```

## 📖 Примеры использования

### Пример 1: Простой вопрос из FAQ

**Вопрос:**
```
Почему не работает авторизация?
```

**Ответ:**
```
Проблема с авторизацией обычно связана с креденшелами GigaChat API.

Решение:
1. Проверьте креденшелы в local.properties:
   gigachat.clientId=...
   gigachat.clientSecret=...

2. Пересоберите проект:
   ./gradlew clean build

3. Убедитесь, что токен не истек

Подробнее: support-docs/faq/authentication.md
```

### Пример 2: Вопрос с контекстом пользователя

**Вопрос:**
```
У user_001 проблема с авторизацией. Что делать?
```

**Ответ:**
```
Привет, Иван Петров!

Вижу ваш тикет #ticket_001 про проблему авторизации.
Вы на тарифе Pro, так что все функции доступны.

Вы получаете ошибку "Invalid credentials". Вот решение:

1. Проверьте креденшелы в local.properties:
   gigachat.clientId=...
   gigachat.clientSecret=...

2. После изменения выполните:
   ./gradlew clean build

3. Убедитесь, что токен не истек

Это должно решить проблему. Дай знать, если что-то не получится!
```

### Пример 3: Поиск и анализ тикетов

**Вопрос:**
```
Найди все тикеты про MCP серверы
```

**Ответ:**
```
Найдено тикетов: 1

- ticket_003: MCP сервер не отвечает [resolved]
  Пользователь: Алексей Козлов
  Описание: Запустил Git MCP сервер, но AI Agent не может подключиться
  Решение: Проблема была в firewall. Пользователь изменил порт на 8081.
```

## 🧪 Тестирование

См. полные тестовые сценарии в `support-docs/TEST_SCENARIOS.md`

**Быстрый тест:**
1. Simple FAQ: "Почему не работает авторизация?"
2. User info: "Расскажи про user_001"
3. Combined: "У user_001 проблема с авторизацией"
4. Search: "Найди тикеты про MCP"
5. Update: "Отметь ticket_004 как решенный"

## 📊 Данные

### Тестовые пользователи

| ID | Имя | План | Статус |
|----|-----|------|--------|
| user_001 | Иван Петров | Pro | active |
| user_002 | Мария Сидорова | Basic | active |
| user_003 | Алексей Козлов | Enterprise | active |
| user_004 | Елена Новикова | Pro | active |
| user_005 | Дмитрий Волков | Basic | inactive |

### Тестовые тикеты

| ID | User | Тема | Статус | Приоритет |
|----|------|------|--------|-----------|
| ticket_001 | user_001 | Не работает авторизация | open | high |
| ticket_002 | user_002 | Ошибка индексирования PDF | in_progress | medium |
| ticket_003 | user_003 | MCP сервер не отвечает | resolved | high |
| ticket_004 | user_004 | Настройка RAG | open | low |
| ticket_005 | user_001 | Медленная работа | open | medium |
| ticket_006 | user_005 | Ошибка сборки | resolved | medium |
| ticket_007 | user_002 | Telegram напоминания | in_progress | medium |

## 🔧 Технологии

- **AI Agent** - Kotlin Compose Multiplatform
- **RAG** - GigaChat/Ollama embeddings, semantic search
- **MCP** - Python MCP SDK
- **Data** - JSON (для demo, в prod использовать PostgreSQL)
- **Indexing** - SQLDelight для метаданных

## 📚 Документация

| Файл | Описание |
|------|----------|
| **SETUP_GUIDE.md** | Полное руководство по настройке |
| **USAGE_GUIDE.md** | Примеры использования |
| **TEST_SCENARIOS.md** | Тестовые сценарии |
| **support-assistant-prompt.md** | System Prompt |
| **mcp-servers/crm/README.md** | CRM MCP Server документация |

## 🎓 Что демонстрирует проект

1. **RAG (Retrieval-Augmented Generation)**
   - Индексирование документации
   - Semantic search с embeddings
   - Контекстные ответы на основе знаний

2. **MCP (Model Context Protocol)**
   - Интеграция с внешними данными (CRM)
   - Набор инструментов для AI
   - Расширяемая архитектура

3. **Комбинирование RAG + MCP**
   - Персонализированные ответы
   - Контекст из CRM + знания из FAQ
   - Workflow техподдержки

4. **System Design**
   - Модульная архитектура
   - Разделение ответственности
   - Тестируемость

## 🚧 Ограничения текущей версии

1. **JSON-based CRM** - не подходит для production
2. **Нет аутентификации** - открытый доступ к данным
3. **Нет истории изменений** - audit log отсутствует
4. **Синхронная обработка** - нет очередей

## 🔮 Улучшения для production

1. **База данных**
   ```
   JSON → PostgreSQL
   + Транзакции
   + Concurrent updates
   + Индексы для быстрого поиска
   ```

2. **Аутентификация**
   ```
   + OAuth 2.0 для MCP
   + Role-based access control
   + API tokens
   ```

3. **Мониторинг**
   ```
   + Логирование всех запросов
   + Метрики (время ответа, hit rate)
   + Алерты на ошибки
   ```

4. **Масштабирование**
   ```
   + Load balancer для MCP
   + Кеширование частых запросов
   + Message queue для тикетов
   ```

## 📈 Метрики успеха

- **Time to First Response** - как быстро агент отвечает
- **Resolution Rate** - % тикетов, решенных с первого раза
- **RAG Hit Rate** - как часто RAG находит релевантную информацию
- **Tool Usage** - статистика использования MCP инструментов
- **User Satisfaction** - feedback от пользователей

## 🤝 Вклад в AI-курс

Проект демонстрирует:
- Практическое применение RAG в реальном сценарии
- Интеграцию AI с бизнес-системами через MCP
- Персонализацию ответов на основе контекста
- Full-stack AI application (от индексирования до UI)

## 📝 Лицензия

Часть проекта GigaChat Multiplatform Chat App для AI-курса.

## 👤 Автор

Создано для Day 23 AI Advent Challenge

---

**Начните с:** `support-docs/SETUP_GUIDE.md`

**Вопросы?** Создайте тикет в системе 😉
