# Тест исправлений /support команды

## Что тестируем

1. ✅ Поиск находит тикеты по разным формам слов (морфология)
2. ✅ AI не галлюцинирует несуществующие ticket ID
3. ✅ Fallback работает без GigaChat credentials

---

## Тест 1: CRM Server (напрямую через API)

### Команда:
```bash
cd mcp-servers
curl -s -X POST "http://localhost:8011/message?sessionId=test" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search_tickets","arguments":{"query":"напоминания Telegram"}}}' \
  | python3 -c "import sys, json; data=json.load(sys.stdin); print(data['result']['content'][0]['text'])"
```

### ✅ Ожидаемый результат:
```
Найдено тикетов: 1 по запросу 'напоминания Telegram'
(Отсортировано по релевантности)

1. ticket_007: Не приходят напоминания в Telegram [in_progress]
   Приоритет: medium | Категория: features
   Описание: Настроил Telegram Bot Token и Chat ID, но напоминания не приходят...
   📊 Релевантность: 4.0 (Matched terms: напоминания, telegram)
```

### ❌ Старое поведение (до исправления):
```
Тикеты по запросу 'напоминания Telegram' не найдены.
```

---

## Тест 2: AI Agent с /support командой

### Запуск AI Agent:
```bash
cd /Users/shchepilov/AndroidStudioProjects/AI_Advent_Challenge_with_Love/day-23-user-support-assistant
./gradlew :ai-agent:run
```

### Тестовый запрос в UI:
```
/support Нет напоминаний в Telegram
```

### ✅ Ожидаемый результат:

```
Согласно документации [источник X]:

**Решение:**
1. Убедитесь, что локальный инструмент "send_telegram_reminder" зарегистрирован
2. Проверьте, что бот имеет доступ к Telegram API
3. Используйте команду для создания напоминания через AI Agent

**Похожие проблемы:**
- ticket_007: "Не приходят напоминания в Telegram" [in_progress] - проверяется конфигурация бота
```

### ❌ Старое поведение (до исправления):

```
**Похожие проблемы:**
- ticket_005: "Telegram reminders not working" ❌ (не существует!)
- ticket_012: "Local tools not found" ❌ (галлюцинация!)
```

---

## Тест 3: Другие формы слов (морфология)

### Тестовые запросы:

```bash
# Родительный падеж
curl ... '{"query":"авторизации"}'
→ Должен найти ticket_001: "Не работает авторизация"

# Другая форма
curl ... '{"query":"индексирование PDF"}'
→ Должен найти ticket_002: "Ошибка при индексировании документов"
```

---

## Проверка логов

### CRM Server logs:
```bash
tail -f /tmp/crm-mcp.log | grep -E "search_tickets|Fallback|Found"
```

### Что искать:
```
✅ [SearchService] LLM unavailable, fallback to word splitting: ['напоминания', 'telegram']
✅ [SearchService] Searching with 2 terms: ['напоминания', 'telegram']
✅ [SearchService] Found 1 tickets with scores
```

### ❌ Старое поведение:
```
❌ [SearchService] Searching with 1 terms: ['напоминания Telegram']  ← целая фраза!
❌ [SearchService] Found 0 tickets
```

---

## Проверка кода

### 1. search_service.py - Fallback исправлен

**Файл:** `mcp-servers/crm/search_service.py`

**До (строка 167-168):**
```python
token = await self.get_gigachat_token()
if not token:
    return [query]  ❌ Целая фраза!
```

**После (строка 167-171):**
```python
token = await self.get_gigachat_token()
if not token:
    # Fallback: split query into words when LLM unavailable
    words = [word.strip().lower() for word in query.split() if len(word.strip()) > 2]
    logger.info(f"[SearchService] LLM unavailable, fallback to word splitting: {words}")
    return words  ✅ Разбитые слова!
```

### 2. CommandHandler.kt - Галлюцинации исправлены

**Файл:** `ai-agent/.../CommandHandler.kt`

**Добавлено (после строки 50):**
```kotlin
## КРИТИЧЕСКИ ВАЖНО: Запрет на галлюцинации!

⛔ **НИКОГДА не придумывай ticket ID!**
- Используй ТОЛЬКО ID из реальных результатов инструментов
- Если search_tickets ничего не нашёл - напиши "Похожих тикетов не найдено"
- НЕ копируй примеры из этого промпта - они для демонстрации формата!
```

**Изменено (строка 145-146):**
```kotlin
// До:
**Похожие проблемы:**
- ticket_005: "Telegram reminders not working" ❌
- ticket_012: "Local tools not found" ❌

// После:
**Похожие проблемы:**
[Здесь выводи РЕАЛЬНЫЕ результаты из search_tickets - НЕ придумывай ticket ID!]
- Если search_tickets вернул тикеты - покажи их ID, темы и решения
- Если ничего не найдено - напиши "Похожих тикетов не найдено"
```

---

## Статус тестов

| Тест | Статус | Комментарий |
|------|--------|-------------|
| CRM API search | ✅ PASS | ticket_007 найден с score 4.0 |
| Fallback без LLM | ✅ PASS | Слова разбиваются корректно |
| AI Agent /support | ⏳ PENDING | Требует ручного запуска UI |
| Галлюцинации | ⏳ PENDING | Требует ручной проверки в UI |

---

## Следующие шаги

1. **Запустить AI Agent:**
   ```bash
   ./gradlew :ai-agent:run
   ```

2. **Ввести в UI:**
   ```
   /support Нет напоминаний в Telegram
   ```

3. **Проверить:**
   - ✅ ticket_007 упомянут в ответе
   - ❌ ticket_005, ticket_012 НЕ упомянуты
   - ✅ Решение из документации предоставлено

4. **Опционально - исправить GigaChat credentials для полной LLM-нормализации**

---

## Документация

- `SUPPORT_MORPHOLOGY_FIX.md` - Краткое резюме исправлений
- `mcp-servers/MORPHOLOGY_FIX.md` - Подробное описание проблем и решений
- `CHANGELOG_SUPPORT_FIX.md` - Changelog с техническими деталями
