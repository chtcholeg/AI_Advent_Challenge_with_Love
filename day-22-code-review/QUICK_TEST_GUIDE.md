# 🚀 Quick Test Guide - Специализированные чек-листы интегрированы!

## ✅ Что сделано (5 минут назад)

1. ✅ Скопирован `SpecializedChecklists.kt` в проект
2. ✅ Добавлен import в `CommandHandler.kt`
3. ✅ Обновлен `buildPrefetchedDataSection()` - добавлено определение технологий
4. ✅ Обновлен `buildSimplifiedReviewInstructions()` - добавлены чек-листы
5. ✅ Создан `buildTechnologyBasedInstructions()` - генератор чек-листов
6. ✅ Обновлен fallback mode - добавлены чек-листы
7. ✅ Проверена компиляция - **BUILD SUCCESSFUL**

## 🧪 Тестирование (следующие 10 минут)

### Тест #1: Проверка определения технологий (2 минуты)

```bash
# Запустить AI Agent
./gradlew :ai-agent:run
```

В чате выполнить:
```
/review-pr 5
```

**Ожидаемый результат:**
```
# Данные для Code Review (собраны автоматически)

╔═══════════════════════════════════════════════════════════════════╗
║ 📋 ОБНАРУЖЕННЫЕ ТЕХНОЛОГИИ                                        ║
╚═══════════════════════════════════════════════════════════════════╝

⚡ В этом PR используются: kotlin, kotlin-coroutines

Применяй соответствующие специализированные проверки:
  • AgentConfig.kt → kotlin
  • CommandHandler.kt → kotlin, kotlin-coroutines

╔═══════════════════════════════════════════════════════════════════╗
║ 🔴 ИЗМЕНЁННЫЕ ФАЙЛЫ — ИСЧЕРПЫВАЮЩИЙ СПИСОК                       ║
╚═══════════════════════════════════════════════════════════════════╝

⚠️ Рецензируй ТОЛЬКО эти файлы...
```

✅ **PASS если:** Секция "ОБНАРУЖЕННЫЕ ТЕХНОЛОГИИ" появилась в ответе
❌ **FAIL если:** Секции нет или технологии определены неправильно

---

### Тест #2: Race condition detection (5 минут)

Создать test файл:

```bash
# Создать тестовую ветку
git checkout -b test-race-condition

# Создать файл с ошибкой
cat > test-race.kt << 'EOF'
package ru.chtcholeg.test

class TestRepository {
    private val cache = mutableMapOf<String, String>()

    suspend fun getData(key: String): String {
        return cache.getOrPut(key) {
            // Simulate API call
            "data-$key"
        }
    }
}
EOF

git add test-race.kt
git commit -m "Test: race condition in cache"
```

В AI Agent:
```
/review-pr test-race-condition
```

**Ожидаемый результат:**
```
╔═══════════════════════════════════════════════════════════════════╗
║ 📋 ОБНАРУЖЕННЫЕ ТЕХНОЛОГИИ                                        ║
╚═══════════════════════════════════════════════════════════════════╝

⚡ В этом PR используются: kotlin, kotlin-coroutines

Применяй соответствующие специализированные проверки:
  • test-race.kt → kotlin, kotlin-coroutines

...

**Файл:** test-race.kt:X-Y
**Severity:** 🔴 Critical
**Проблема:** Shared mutable state без синхронизации

**Текущий код:**
```kotlin
private val cache = mutableMapOf<String, String>()

suspend fun getData(key: String): String {
    return cache.getOrPut(key) { ... }  // ⚠️ НЕ СИНХРОНИЗИРОВАНО
}
```

**Как проявится:**
Две корутины вызывают getData("same-key") параллельно:
1. Обе проверяют cache["same-key"] → null
2. Обе входят в getOrPut блок
3. HashMap может быть corrupted при concurrent put → ConcurrentModificationException

**Рекомендация:**
```kotlin
private val cache = mutableMapOf<String, String>()
private val mutex = Mutex()

suspend fun getData(key: String) = mutex.withLock {
    cache.getOrPut(key) { "data-$key" }
}
```
```

✅ **PASS если:**
- Обнаружена технология kotlin-coroutines
- Найдена проблема с severity 🔴 Critical
- Описан race condition сценарий
- Предложено решение с Mutex

❌ **FAIL если:**
- Проблема не найдена
- Severity неправильный
- Нет объяснения как проявится

---

### Тест #3: NPE detection (3 минуты)

```bash
git checkout -b test-npe

cat > test-npe.kt << 'EOF'
package ru.chtcholeg.test

class UserService(private val database: Database) {
    suspend fun updateUser(id: String, name: String) {
        val user = database.findById(id)  // возвращает User?
        user.name = name
        database.save(user)
    }
}
EOF

git add test-npe.kt
git commit -m "Test: NPE in updateUser"
```

В AI Agent:
```
/review-pr test-npe
```

**Ожидаемый результат:**
```
**Файл:** test-npe.kt:X-Y
**Severity:** 🔴 Critical
**Проблема:** Nullable return без проверки

**Текущий код:**
```kotlin
val user = database.findById(id)  // возвращает User?
user.name = name  // ⚠️ NPE если user == null
```

**Как проявится:**
Если пользователь с id не найден, database.findById() вернёт null.
При попытке user.name = name произойдёт:
  kotlin.NullPointerException: user is null
  at UserService.updateUser(test-npe.kt:6)

**Рекомендация:**
```kotlin
suspend fun updateUser(id: String, name: String) {
    val user = database.findById(id)
        ?: throw UserNotFoundException("User not found: $id")
    user.name = name
    database.save(user)
}
```
```

✅ **PASS если:** Найден NPE с правильным объяснением
❌ **FAIL если:** NPE не найден

---

## 📊 Результаты тестирования

Заполните после прохождения тестов:

```
Тест #1 (Определение технологий): [ ] PASS  [ ] FAIL
Тест #2 (Race condition):          [ ] PASS  [ ] FAIL
Тест #3 (NPE):                      [ ] PASS  [ ] FAIL

Общий результат: ___/3 (____%)
```

**Target:** >= 2/3 (67%) для начала
**Goal:** 3/3 (100%)

---

## 🎯 Следующие шаги (если тесты прошли)

### 1. Создать полный test suite (20 минут)

См. файл `INTEGRATION_GUIDE.md`, раздел "Тестирование"

Создать 10 тестовых случаев:
- Race conditions (2 теста)
- NPE (2 теста)
- StateFlow race (1 тест)
- SQL injection (1 тест)
- MVI mutable state (1 тест)
- Cache without invalidation (1 тест)
- Python async blocking (1 тест)
- Config secrets (1 тест)

### 2. Измерить baseline recall

```bash
# Запустить review на всех 10 тестах
for i in {1..10}; do
    /review-pr test-case-$i
done

# Подсчитать: сколько из 10 найдено?
Recall = X/10 = Y%
```

**Target:** >= 8/10 (80% recall)

### 3. Опционально: Structured output + validation

См. файл `RECOMMENDATIONS.md`, секция #1

Добавить:
- JSON Schema для response
- Post-processing валидацию
- Retry механизм

---

## 🐛 Troubleshooting

### Проблема: "Технологии не определяются"

**Симптом:** Секция "ОБНАРУЖЕННЫЕ ТЕХНОЛОГИИ" пустая

**Решение:**
1. Проверить, что файлы содержат ключевые слова:
   - `suspend` для kotlin-coroutines
   - `Flow<` или `StateFlow<` для kotlin-flow
   - `Repository` в имени для repository-pattern

2. Проверить путь к файлу:
   - Должен заканчиваться на `.kt` для Kotlin
   - Должен заканчиваться на `.py` для Python

### Проблема: "Ошибки не находятся"

**Симптом:** Review возвращает ✅ OK для файла с ошибкой

**Причины:**
1. Модель не применила специализированный чек-лист
2. Паттерн ошибки не описан в чек-листе
3. Недостаточно контекста в diff

**Решение:**
1. Проверить, что технология определена правильно
2. Прочитать весь чек-лист и убедиться, что паттерн описан
3. Если паттерна нет - добавить в `SpecializedChecklists.kt`

### Проблема: "Компиляция не проходит"

**Симптом:** BUILD FAILED

**Решение:**
```bash
# Проверить синтаксис
./gradlew :ai-agent:compileKotlinDesktop --console=plain

# Если ошибки - проверить:
# 1. Import добавлен правильно
# 2. Методы вызываются правильно
# 3. Нет опечаток в названиях
```

---

## 📚 Полезные ссылки

- `SPECIALIZED_CHECKLISTS.kt` - библиотека чек-листов (готовый код)
- `INTEGRATION_GUIDE.md` - полная инструкция по интеграции
- `IMPROVED_DETECTION_STRATEGY.md` - стратегия повышения recall
- `SUMMARY_ALL_IMPROVEMENTS.md` - сводка всех улучшений

---

## 💡 Быстрые команды

```bash
# Запустить AI Agent
./gradlew :ai-agent:run

# Проверить компиляцию
./gradlew :ai-agent:compileKotlinDesktop

# Создать тестовую ветку
git checkout -b test-recall

# Запустить review
# В чате AI Agent:
/review-pr test-recall

# Откатить тестовые изменения
git checkout master
git branch -D test-race-condition test-npe test-recall
```

---

## 🎉 Поздравляем!

Вы успешно интегрировали специализированные чек-листы!

**Ожидаемые улучшения:**
- Recall: 40-50% → **75-85%**
- Находит race conditions, NPE, SQL injection
- Применяет специализированные проверки для каждого языка

**Время до результата:** 10 минут тестирования

Начните с Теста #1 прямо сейчас! 🚀
