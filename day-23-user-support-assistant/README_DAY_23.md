# Day 23: User Support Assistant

**AI-powered technical support assistant with RAG and CRM integration**

## 📖 Overview

Мини-сервис технической поддержки, который использует:
- **RAG** - для ответов на основе документации и FAQ
- **MCP** - для интеграции с CRM (пользователи и тикеты)
- **AI Agent** - для персонализированных ответов с контекстом

## 🎯 Пример работы

```
Вопрос: "Почему не работает авторизация?"

Ассистент:
  1. 🔍 Ищет решение в FAQ через RAG
  2. 📊 Проверяет историю тикетов через CRM MCP
  3. 💬 Отвечает с учетом тарифного плана пользователя

Ответ: "Привет, Иван! Вижу ваш тикет #ticket_001 про авторизацию.
        Вы на тарифе Pro. Вот решение: 1) Проверьте local.properties..."
```

## 🚀 Quick Start (5 минут)

```bash
# 1. Quick setup
./QUICK_START.sh

# 2. Start AI Agent
./gradlew :ai-agent:run

# 3. Configure in UI:
#    - System Prompt (from support-docs/config/)
#    - Enable RAG (index: ./support-index.json)
#    - Enable MCP CRM

# 4. Ask questions!
"Почему не работает авторизация?"
"У user_001 проблема с авторизацией"
```

## 📚 Components

### 1. FAQ Documentation (RAG)
5 документов в `support-docs/faq/`:
- `authentication.md` - проблемы авторизации
- `installation.md` - установка и запуск
- `mcp-servers.md` - MCP серверы
- `features.md` - функции приложения
- `errors.md` - распространенные ошибки

### 2. CRM MCP Server
`mcp-servers/crm/` - Python MCP сервер с 7 инструментами:
- get_user, list_users
- get_ticket, list_tickets
- update_ticket_status
- search_tickets
- get_user_tickets

**Тестовые данные:**
- 5 пользователей (users.json)
- 7 тикетов (tickets.json)

### 3. Configuration
- System Prompt для support mode
- MCP конфигурация
- RAG settings

## 📖 Documentation

| File | Description |
|------|-------------|
| **[DAY_23_README.md](support-docs/DAY_23_README.md)** | Полный обзор проекта |
| **[SETUP_GUIDE.md](support-docs/SETUP_GUIDE.md)** | Пошаговая настройка |
| **[USAGE_GUIDE.md](support-docs/USAGE_GUIDE.md)** | Примеры использования |
| **[TEST_SCENARIOS.md](support-docs/TEST_SCENARIOS.md)** | 10 тестовых сценариев |
| **[DAY_23_SUMMARY.md](DAY_23_SUMMARY.md)** | Итоговая сводка |

## 🎯 Key Features

### RAG (Retrieval-Augmented Generation)
✅ 5 FAQ документов (~200+ решений)
✅ GigaChat/Ollama embeddings
✅ Semantic search
✅ Source citations

### MCP (Model Context Protocol)
✅ 7 CRM инструментов
✅ Фильтрация и поиск
✅ CRUD операции с тикетами
✅ Расширяемая архитектура

### Personalization
✅ Контекст пользователя (план, история)
✅ Ссылки на предыдущие тикеты
✅ Дружелюбный тон
✅ Structured responses

## 🧪 Testing

10 тестовых сценариев:
1. Простой вопрос из FAQ (RAG only)
2. Информация о пользователе (MCP only)
3. История тикетов (MCP)
4. Комбинированный запрос (RAG + MCP)
5. Поиск тикетов
6. Фильтрация тикетов
7. Обновление статуса
8. Добавление заметок
9. Контекст между вопросами
10. Обработка ошибок

См. подробнее: [TEST_SCENARIOS.md](support-docs/TEST_SCENARIOS.md)

## 🏗️ Architecture

```
User Question
     │
     ▼
┌────────────────────┐
│   AI Agent         │
│ (Support Mode)     │
└────┬───────────┬───┘
     │           │
     ▼           ▼
┌─────────┐  ┌──────────┐
│   RAG   │  │   MCP    │
│  (FAQ)  │  │  (CRM)   │
└─────────┘  └──────────┘
     │           │
     └─────┬─────┘
           ▼
    Персонализированный
         ответ
```

## 📊 Statistics

- **FAQ Documents:** 5 files, ~1000 lines
- **CRM MCP Server:** ~400 lines Python
- **Documentation:** ~2000 lines
- **Test Users:** 5
- **Test Tickets:** 7
- **MCP Tools:** 7
- **Test Scenarios:** 10

## 💡 What it demonstrates

1. **RAG in practice** - semantic search over documentation
2. **MCP integration** - extending AI with external tools
3. **RAG + MCP combo** - knowledge + data = powerful assistant
4. **Full-stack AI** - from indexing to UI

## 🔧 Commands

```bash
# Index FAQ
./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-index.json md"

# Start CRM MCP
cd mcp-servers/crm && ./START.sh

# Run AI Agent
./gradlew :ai-agent:run

# Check index stats
./gradlew :shared:runIndexing --args="stats ./support-index.json"
```

## 📈 Metrics

- Time to First Response
- Resolution Rate (1st try)
- RAG Hit Rate
- Tool Usage Statistics
- User Satisfaction

## 🚧 Limitations

Current version (demo):
- JSON-based CRM (not production-ready)
- No authentication
- No audit log
- Synchronous processing

For production: see [SETUP_GUIDE.md](support-docs/SETUP_GUIDE.md) → "Production Deployment"

## 🎓 Learning Outcomes

This project teaches:
- RAG implementation (indexing → search → context)
- MCP protocol (tools → schema → integration)
- AI agent configuration (prompts → workflow)
- Combining RAG + MCP for real-world use case

## 🔗 Links

- **Main README:** [support-docs/DAY_23_README.md](support-docs/DAY_23_README.md)
- **Setup:** [support-docs/SETUP_GUIDE.md](support-docs/SETUP_GUIDE.md)
- **Usage:** [support-docs/USAGE_GUIDE.md](support-docs/USAGE_GUIDE.md)
- **Tests:** [support-docs/TEST_SCENARIOS.md](support-docs/TEST_SCENARIOS.md)
- **CRM MCP:** [mcp-servers/crm/README.md](mcp-servers/crm/README.md)

## 🎉 Status

✅ Day 23 completed!

All components implemented:
- ✅ RAG system with FAQ
- ✅ CRM MCP server
- ✅ Support assistant configuration
- ✅ Full documentation
- ✅ Test scenarios
- ✅ Quick start script

**Ready to use!**

---

**Start here:** Run `./QUICK_START.sh` or read [SETUP_GUIDE.md](support-docs/SETUP_GUIDE.md)
