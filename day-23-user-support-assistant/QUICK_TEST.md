# Support Assistant - Быстрый тест

## Подготовка (опционально)

### 1. Создать RAG индекс для FAQ
```bash
export GIGACHAT_CLIENT_ID="your_client_id"
export GIGACHAT_CLIENT_SECRET="your_client_secret"

./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-knowledge.json md txt"
```

### 2. Настроить RAG в AI Agent
- Запустите AI Agent: `./gradlew :ai-agent:run`
- Settings → RAG Mode: Enable
- Settings → Index Path: `./support-knowledge.json`

### 3. (Опционально) Запустить CRM MCP Server
```bash
cd mcp-servers
python -m crm.main
```

Настроить в AI Agent:
- Settings → MCP Servers → Add Server
- Name: CRM
- Command: `python`
- Args: `-m crm.main`
- Type: stdio

## Тестовые сценарии

### Тест 1: Базовая активация ✅
**Команда:**
```
/support
```

**Ожидаемый результат:**
```
Привет! Я готов помочь с вопросами по GigaChat Multiplatform Chat App.
Опиши, пожалуйста, свою проблему или задай вопрос.
```

**Проверка:**
- ✅ Промпт загружен
- ✅ Support Assistant активирован
- ✅ Приветствие выведено

---

### Тест 2: Простой вопрос (без RAG) ✅
**Команда:**
```
/support Как запустить AI Agent?
```

**Ожидаемый результат:**
- Ответ с командой `./gradlew :ai-agent:run`
- Структурированный формат
- Дружелюбный тон

**Проверка:**
- ✅ Использует промпт Support Assistant
- ✅ Даёт конкретные инструкции

---

### Тест 3: Вопрос с RAG (если индекс создан) ✅
**Команда:**
```
/support Проблема с авторизацией GigaChat
```

**Ожидаемый результат:**
- RAG поиск в FAQ: `support-docs/faq/authentication.md`
- Ответ включает:
  - Проверку креденшелов в `local.properties`
  - Команду `./gradlew clean build`
  - Проверку токена на сайте GigaChat

**Проверка:**
- ✅ Использует RAG для поиска решений
- ✅ Ссылается на документацию
- ✅ Даёт пошаговое решение

---

### Тест 4: Вопрос с MCP/CRM (если сервер запущен) ✅
**Команда:**
```
/support userId=user_001 У меня проблема с MCP сервером
```

**Ожидаемый результат:**
- Вызов `get_user userId=user_001`
- Вызов `get_user_tickets userId=user_001`
- Персонализированный ответ:
  - Упоминание имени пользователя
  - Ссылка на историю тикетов
  - Решение из базы знаний

**Проверка:**
- ✅ Использует CRM инструменты
- ✅ Персонализирует ответ
- ✅ Обновляет статус тикета

---

### Тест 5: Поиск похожих тикетов (если CRM запущен) ✅
**Команда:**
```
/support Есть ли у других проблемы с индексацией документов?
```

**Ожидаемый результат:**
- Вызов `search_tickets query="индексация"`
- Анализ похожих тикетов
- Обобщение решений

**Проверка:**
- ✅ Использует search_tickets
- ✅ Анализирует паттерны
- ✅ Предлагает проверенные решения

---

### Тест 6: Через Task Tool (advanced) ✅
**Действие в AI Agent:**
Попросите AI Agent запустить subagent:
```
Запусти support-assistant агента для обработки тикета: "Как настроить Ollama?"
```

**Ожидаемый результат:**
- Task tool вызывает support-assistant
- Агент работает изолированно
- Возвращает структурированный ответ

**Проверка:**
- ✅ Агент зарегистрирован в AgentRegistry
- ✅ Запускается через Task tool
- ✅ Имеет доступ ко всем инструментам

## Проверка интеграции

### Проверка 1: Промпт загружается
**Проверка:**
```bash
cat support-docs/config/support-assistant-prompt.md
```

**Ожидается:** Файл существует и содержит промпт между ``` маркерами.

### Проверка 2: Команда зарегистрирована
**Код:** `CommandHandler.kt`
```kotlin
"support" -> handleSupportCommand(args)
```

**Ожидается:** ✅ Обработчик добавлен

### Проверка 3: Агент в регистре
**Код:** `AgentRegistry.kt`
```kotlin
supportAssistantAgent
```

**Ожидается:** ✅ Агент зарегистрирован

## Debug

### Если команда не работает

1. **Проверьте логи:**
   ```
   [CommandHandler] Processing command: /support
   ```

2. **Проверьте файл промпта:**
   ```bash
   ls -la support-docs/config/support-assistant-prompt.md
   ```

3. **Проверьте компиляцию:**
   ```bash
   ./gradlew :ai-agent:build
   ```

### Если RAG не работает

1. **Проверьте индекс:**
   ```bash
   ls -la ./support-knowledge.json
   ```

2. **Проверьте настройки RAG в AI Agent**
   - Settings → RAG Mode: ✅ Enabled
   - Settings → Index Path: `./support-knowledge.json`

3. **Переиндексируйте:**
   ```bash
   ./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-knowledge.json md"
   ```

### Если MCP не работает

1. **Проверьте CRM сервер:**
   ```bash
   cd mcp-servers
   python -m crm.main
   # Должен запуститься без ошибок
   ```

2. **Проверьте Settings → MCP Servers**
   - CRM server должен быть в списке
   - Status: Connected

3. **Проверьте логи MCP**

## Успешное выполнение

Все тесты пройдены, если:
- ✅ `/support` активирует Support Assistant
- ✅ Промпт загружается корректно
- ✅ RAG находит информацию в FAQ (если индекс создан)
- ✅ MCP инструменты работают (если сервер запущен)
- ✅ Ответы структурированные и дружелюбные
- ✅ Task tool может запустить support-assistant

## Базовый тест (минимальные требования)

Даже без RAG и MCP Support Assistant должен работать:

```
./gradlew :ai-agent:run
```

В AI Agent:
```
/support Как установить проект?
```

**Ожидается:**
- Загрузка промпта Support Assistant
- Дружелюбный ответ с инструкциями
- Использование существующих знаний AI

## Результат

Support Assistant готов к работе! 🎉

**Следующие шаги:**
1. Создайте RAG индекс для полной функциональности
2. Настройте CRM MCP server для персонализации
3. Тестируйте различные сценарии поддержки
4. Обновляйте FAQ по мере необходимости
