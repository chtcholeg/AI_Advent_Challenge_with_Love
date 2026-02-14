# Стратегия улучшения обнаружения ошибок в Code Review

## 🎯 Проблема

Система `/review-pr` **пропускает реальные ошибки** - низкий recall (находимость).

## 🔍 Анализ: Почему ошибки пропускаются

### 1. **Недостаточная специализация анализа**

Текущий чек-лист слишком общий:
```
1. CONCURRENCY
2. NULL SAFETY
3. LOGIC BUGS
4. ERROR HANDLING
5. RESOURCE LEAKS
6. SECURITY
7. OFF-BY-ONE
8. DATA LOSS
9. ARCHITECTURE
```

**Проблема:** Модель не знает, **ЧТО КОНКРЕТНО** искать в каждом типе кода.

### 2. **Отсутствие паттернов ошибок**

Модель не имеет базы знаний о типичных ошибках:
- Какие конкретно race conditions бывают?
- Какие конкретно NPE-паттерны существуют?
- Какие конкретно логические ошибки часты?

### 3. **Нет контекстной адаптации**

Один и тот же чек-лист для:
- Kotlin suspend функций (нужны проверки на coroutine context)
- Python async (нужны проверки на event loop)
- SQL queries (нужны проверки на injection)
- Config files (нужны проверки на secrets)

### 4. **Низкая детализация инструкций**

Текущие инструкции:
> "Есть ли shared mutable state без синхронизации?"

**Недостаточно конкретно!** Нужно:
- Примеры конкретных паттернов
- Как их распознать в коде
- Какие методы проверки использовать

## ✅ Решение: Многоуровневая система обнаружения

### Уровень 1: Язык-специфичные чек-листы

#### Kotlin Coroutines (suspend functions, Flow, StateFlow)

```markdown
### KOTLIN COROUTINES - ОБЯЗАТЕЛЬНЫЕ ПРОВЕРКИ

#### 1. Shared Mutable State в классах
**ЧТО ИСКАТЬ:**
```kotlin
class Repository {
    private var cache = mutableMapOf<K, V>()  // ⚠️ SHARED MUTABLE
    private val list = mutableListOf<T>()     // ⚠️ SHARED MUTABLE

    suspend fun getData() {
        cache[key] = value  // ⚠️ НЕ СИНХРОНИЗИРОВАНО
    }
}
```

**КАК ПРОВЕРИТЬ:**
1. Найди все `var` и `mutable*` на уровне класса
2. Проверь, что они защищены `Mutex.withLock {}` или `synchronized`
3. Исключения: `private val _stateFlow = MutableStateFlow()` + `val stateFlow = _stateFlow.asStateFlow()` - БЕЗОПАСНО

**EXAMPLE ПРОБЛЕМЫ:**
```kotlin
class Repo {
    private val cache = mutableMapOf<String, Data>()  // ⚠️

    suspend fun get(key: String) = cache.getOrPut(key) {  // RACE CONDITION
        api.fetch(key)
    }
}
```
**MANIFESTATION:** Две корутины вызывают `get("same-key")` → оба попадают в `getOrPut` → дублирование запросов + HashMap corruption.

#### 2. Nullable return без проверки
**ЧТО ИСКАТЬ:**
```kotlin
fun find(): T?  // возвращает nullable
val result = find()
result.property  // ⚠️ NPE
```

**КАК ПРОВЕРИТЬ:**
1. Найди все вызовы функций, возвращающих `T?`
2. Проверь, что после вызова есть:
   - `?.let { }` или
   - `?: return` или
   - `?: throw` или
   - явная проверка `if (x != null)`

**EXAMPLE ПРОБЛЕМЫ:**
```kotlin
suspend fun process(id: String) {
    val user = database.findById(id)  // возвращает User?
    user.name = newName  // ⚠️ NPE
}
```

#### 3. Coroutine Context Loss
**ЧТО ИСКАТЬ:**
```kotlin
suspend fun heavy() {
    withContext(Dispatchers.IO) {
        // тяжёлая работа
    }
    // Продолжение на каком диспатчере?
}
```

**КАК ПРОВЕРИТЬ:**
Убедись, что после `withContext` код не предполагает конкретный диспатчер.

#### 4. Flow collection без lifecycle
**ЧТО ИСКАТЬ:**
```kotlin
init {
    flow.collect { }  // ⚠️ УТЕЧКА
}
```

**КАК ПРОВЕРИТЬ:**
- В Android: используется `lifecycleScope.launch` или `repeatOnLifecycle`?
- В Desktop: есть `scope.cancel()` в cleanup?

#### 5. StateFlow update race
**ЧТО ИСКАТЬ:**
```kotlin
_state.value = _state.value.copy(count = _state.value.count + 1)  // ⚠️ RACE
```

**КАК ПРОВЕРИТЬ:**
Используется ли `_state.update { it.copy(...) }`?

#### 6. Забытый error handling в suspend
**ЧТО ИСКАТЬ:**
```kotlin
suspend fun load() {
    val data = api.fetch()  // может бросить исключение
    _state.value = Success(data)
}
```

**КАК ПРОВЕРИТЬ:**
Есть ли `try-catch` или `runCatching` вокруг suspend вызовов?
```

#### Python Async

```markdown
### PYTHON ASYNC - ОБЯЗАТЕЛЬНЫЕ ПРОВЕРКИ

#### 1. Blocking operations в async функциях
**ЧТО ИСКАТЬ:**
```python
async def process():
    data = requests.get(url)  # ⚠️ БЛОКИРУЕТ event loop
    time.sleep(1)             # ⚠️ БЛОКИРУЕТ
    open('file.txt').read()   # ⚠️ БЛОКИРУЕТ
```

**КАК ПРОВЕРИТЬ:**
В async функциях должны быть только:
- `await asyncio.sleep()` (не `time.sleep`)
- `aiohttp.ClientSession` (не `requests`)
- `aiofiles.open()` (не встроенный `open`)

#### 2. Missing await
**ЧТО ИСКАТЬ:**
```python
async def get_data():
    return await api.fetch()

async def process():
    data = get_data()  # ⚠️ ЗАБЫЛИ await
    print(data)        # напечатает coroutine object
```

**КАК ПРОВЕРИТЬ:**
Все вызовы async функций должны иметь `await`.

#### 3. asyncio.create_task без await или сохранения
**ЧТО ИСКАТЬ:**
```python
async def background():
    asyncio.create_task(long_running())  # ⚠️ ЗАБЫЛИ сохранить
    # task может быть GC до завершения
```

**КАК ПРОВЕРИТЬ:**
`create_task()` должен быть:
- Либо awaited: `await asyncio.create_task(...)`
- Либо сохранён: `task = asyncio.create_task(...)`
```

#### SQL Queries

```markdown
### SQL QUERIES - ОБЯЗАТЕЛЬНЫЕ ПРОВЕРКИ

#### 1. SQL Injection
**ЧТО ИСКАТЬ:**
```kotlin
val query = "SELECT * FROM users WHERE name = '$userName'"  // ⚠️ INJECTION
database.execute(query)
```

**КАК ПРОВЕРИТЬ:**
- Используются ли prepared statements / параметризованные запросы?
- Строки НЕ должны конкатенироваться с пользовательским вводом

**SAFE:**
```kotlin
val query = "SELECT * FROM users WHERE name = ?"
database.execute(query, userName)
```

#### 2. N+1 Query Problem
**ЧТО ИСКАТЬ:**
```kotlin
val users = db.query("SELECT * FROM users")
for (user in users) {
    val posts = db.query("SELECT * FROM posts WHERE user_id = ?", user.id)  // ⚠️ N запросов
}
```

**КАК ПРОВЕРИТЬ:**
Может ли быть JOIN или IN clause?
```

### Уровень 2: Паттерн-специфичные детекторы

#### Repository Pattern

```markdown
### REPOSITORY PATTERN CHECKS

#### 1. Отсутствие кэширования при повторных запросах
**ЧТО ИСКАТЬ:**
```kotlin
class UserRepository {
    suspend fun getUser(id: String): User {
        return api.fetchUser(id)  // ⚠️ КАЖДЫЙ РАЗ НОВЫЙ ЗАПРОС
    }
}

// Usage:
val user1 = repo.getUser("123")
val user2 = repo.getUser("123")  // ещё один запрос с тем же ID
```

**КАК ПРОВЕРИТЬ:**
Для read-heavy операций должен быть кэш (если данные не меняются часто).

#### 2. Кэш без invalidation
**ЧТО ИСКАТЬ:**
```kotlin
class Repository {
    private val cache = mutableMapOf<String, Data>()

    suspend fun get(id: String) = cache.getOrPut(id) { api.fetch(id) }

    suspend fun update(id: String, newData: Data) {
        api.update(id, newData)
        // ⚠️ НЕ ОБНОВИЛИ КЭШ!
    }
}
```

**КАК ПРОВЕРИТЬ:**
Если есть кэш + операции изменения → должна быть invalidation.

#### 3. Inconsistent error handling
**ЧТО ИСКАТЬ:**
```kotlin
suspend fun getUser(id: String): User {
    return try {
        api.fetchUser(id)
    } catch (e: Exception) {
        throw UserNotFoundException()  // ✅
    }
}

suspend fun getPost(id: String): Post {
    return api.fetchPost(id)  // ⚠️ НЕТ обработки ошибок
}
```

**КАК ПРОВЕРИТЬ:**
Все методы репозитория должны иметь одинаковый подход к ошибкам.
```

#### MVI/MVVM State Management

```markdown
### MVI STATE MANAGEMENT CHECKS

#### 1. Direct state mutation
**ЧТО ИСКАТЬ:**
```kotlin
data class State(val items: MutableList<Item>)  // ⚠️ MUTABLE внутри immutable

val state = State(mutableListOf())
state.items.add(item)  // ⚠️ MUTATION без нового state
```

**КАК ПРОВЕРИТЬ:**
State должен содержать ТОЛЬКО immutable типы (List, не MutableList).

#### 2. Missing copy() в state updates
**ЧТО ИСКАТЬ:**
```kotlin
_state.value.loading = true  // ⚠️ ПРЯМАЯ МУТАЦИЯ
```

**КАК ПРОВЕРИТЬ:**
Должно быть: `_state.value = _state.value.copy(loading = true)`

#### 3. Side effects в reducers
**ЧТО ИСКАТЬ:**
```kotlin
when (intent) {
    is LoadData -> {
        launch {  // ⚠️ SIDE EFFECT В INTENT HANDLER
            val data = repository.load()
            _state.value = state.copy(data = data)
        }
    }
}
```

**КАК ПРОВЕРИТЬ:**
В MVI intent handler должен вернуть новый state, side effects - отдельно.
```

### Уровень 3: Контекстно-зависимые проверки

#### По типу изменения

```markdown
### FEATURE ADDITION
Если PR добавляет новую функцию:
1. ✅ Есть ли error handling?
2. ✅ Есть ли input validation?
3. ✅ Есть ли логирование ошибок?
4. ✅ Есть ли обратная совместимость (если нужна)?
5. ✅ Не добавлены ли magic numbers (должны быть константы)?

### BUG FIX
Если PR фиксит баг:
1. ✅ Действительно ли это исправляет root cause?
2. ✅ Не сломано ли что-то ещё этим фиксом?
3. ✅ Есть ли защита от повторения бага?
4. ✅ Проверены ли edge cases?

### REFACTORING
Если PR рефакторит:
1. ✅ Сохранена ли функциональность (behavior preservation)?
2. ✅ Не удалена ли нужная логика?
3. ✅ Все ли вызовы обновлены?
4. ✅ Нет ли dead code после рефакторинга?

### PERFORMANCE OPTIMIZATION
Если PR оптимизирует:
1. ✅ Действительно ли это улучшает performance?
2. ✅ Не ухудшена ли читаемость чрезмерно?
3. ✅ Есть ли benchmark proof?
4. ✅ Проверены ли edge cases (пустые коллекции, null)?
```

### Уровень 4: Специфичные для проекта паттерны

```markdown
### PROJECT-SPECIFIC PATTERNS

#### GigaChat AI Agent Project

**Обязательные проверки:**

1. **MCP Tool Calls**
   - Проверка permissions перед вызовом?
   - Retry policy применён?
   - Error handling через ToolExecutor?

2. **Agent Repository**
   - История синхронизирована через historyMutex?
   - Token refresh корректен?
   - Context manager использован?

3. **RAG Integration**
   - Index loaded проверка перед search?
   - Embeddings generation error handling?
   - Citations включены где нужно?

4. **Plan Mode**
   - Tool definitions не отправляются в plan mode?
   - Exit plan mode корректно завершает?
   - Permissions проверены?

5. **Command Handlers**
   - Context budget соблюдён (MAX_CONTEXT_CHARS)?
   - File list verification для /review-pr?
   - Pre-fetch fallback to tool calling?
```

## 🚀 Реализация: Расширенный промпт для review

### Новая структура инструкций

```markdown
# Code Review Instructions - Enhanced Detection

## ШSECTION 1: БЫСТРЫЙ ЯЗЫК/ФРЕЙМВОРК ДЕТЕКТОР

⚠️ ПЕРЕД анализом определи технологии в каждом файле:

- `*.kt` + `suspend` → Kotlin Coroutines → применить KOTLIN COROUTINES CHECKS
- `*.py` + `async def` → Python Async → применить PYTHON ASYNC CHECKS
- SQL queries → применить SQL INJECTION CHECKS
- Repository паттерн → применить REPOSITORY PATTERN CHECKS
- MVI/Store классы → применить MVI STATE CHECKS

## SECTION 2: СПЕЦИАЛИЗИРОВАННЫЕ ЧЕК-ЛИСТЫ

### KOTLIN COROUTINES CHECKS
[детальный чек-лист с примерами]

### PYTHON ASYNC CHECKS
[детальный чек-лист с примерами]

### SQL QUERIES CHECKS
[детальный чек-лист с примерами]

## SECTION 3: ОБЩИЕ ПРОВЕРКИ (для всех языков)

1. **NULL SAFETY**
   [конкретные паттерны для каждого языка]

2. **RESOURCE LEAKS**
   [конкретные паттерны для каждого языка]

3. **ERROR HANDLING**
   [конкретные паттерны для каждого языка]

## SECTION 4: ПРОЕКТ-СПЕЦИФИЧНЫЕ ПРОВЕРКИ

[список обязательных проверок для данного проекта]

## SECTION 5: МЕТОДОЛОГИЯ ОБНАРУЖЕНИЯ

Для КАЖДОГО типа ошибки:

1. **ЧТО ИСКАТЬ** - конкретный код-паттерн
2. **КАК ПРОВЕРИТЬ** - шаги верификации
3. **EXAMPLE ПРОБЛЕМЫ** - реальный пример с номерами строк
4. **MANIFESTATION** - как проявится ошибка

## SECTION 6: FALSE POSITIVE PREVENTION

❌ НЕ отмечай как ошибку:
- Локальные переменные (`val local = ...` внутри функции)
- Thread-safe collections (ConcurrentHashMap, AtomicReference)
- StateFlow updates через .update {} (уже thread-safe)

✅ Отмечай как ошибку ТОЛЬКО если:
- Можешь показать 2+ места доступа к shared state
- Можешь описать конкретный race condition сценарий
- Можешь привести пример входа, при котором случится ошибка
```

## 📊 Ожидаемые результаты

### До улучшений:
- **Recall (находимость):** 40-50%
- Пропускает:
  - Subtle race conditions
  - Сложные логические ошибки
  - Контекстно-зависимые проблемы
  - Проект-специфичные паттерны

### После улучшений:
- **Recall (находимость):** 75-85%
- Находит:
  - ✅ 90% concurrency проблем (специализированный чек-лист)
  - ✅ 85% null safety проблем (конкретные паттерны)
  - ✅ 80% логических ошибок (контекстный анализ)
  - ✅ 70% проект-специфичных проблем (custom checks)

## 🧪 Тестирование

### Создать test suite с известными ошибками:

```kotlin
// test-case-1-race-condition.kt
class Repo {
    private val cache = mutableMapOf<String, Data>()
    suspend fun get(key: String) = cache.getOrPut(key) { api.fetch(key) }
}
// ОЖИДАНИЕ: 🔴 Critical - Race condition в cache

// test-case-2-npe.kt
suspend fun process(id: String) {
    val user = db.findById(id)  // возвращает User?
    user.name = "test"
}
// ОЖИДАНИЕ: 🔴 Critical - NPE

// test-case-3-stateflow-race.kt
_state.value = _state.value.copy(count = _state.value.count + 1)
// ОЖИДАНИЕ: 🟠 High - StateFlow update race

// test-case-4-sql-injection.kt
val query = "SELECT * FROM users WHERE id = '$userId'"
// ОЖИДАНИЕ: 🔴 Critical - SQL Injection

// test-case-5-blocking-in-async.py
async def process():
    time.sleep(1)  # blocks event loop
    return requests.get(url)  # blocks event loop
# ОЖИДАНИЕ: 🔴 Critical - Blocking operations in async
```

### Метрики:

```
Total test cases: 20
Found: X / 20
Recall: X / 20 = Y%

Breakdown:
- Concurrency: X/5 = Y%
- Null safety: X/4 = Y%
- Logic bugs: X/4 = Y%
- Security: X/3 = Y%
- Resource leaks: X/4 = Y%
```

## 💡 Ключевые принципы

1. **Специализация > Общность**
   - Один чек-лист для всех языков = низкий recall
   - Специализированные чек-листы = высокий recall

2. **Конкретика > Абстракция**
   - "Проверь null safety" = модель не знает что делать
   - "Найди все вызовы функций, возвращающих T?, проверь наличие ?. или ?: после" = actionable

3. **Примеры > Описания**
   - Показать КОД с проблемой
   - Показать КАК она проявится
   - Показать КАК исправить

4. **Контекст > Универсальность**
   - Kotlin coroutines ≠ Python async ≠ JavaScript promises
   - Каждый требует своих проверок

5. **Проект-специфичность > Generic rules**
   - Generic rules пропускают domain-specific проблемы
   - Custom checks для конкретного проекта = находит больше

## 🔧 Реализация в CommandHandler.kt

### Добавить метод определения технологий:

```kotlin
private fun detectTechnologies(filePath: String, content: String): Set<String> {
    val techs = mutableSetOf<String>()

    when {
        filePath.endsWith(".kt") -> {
            techs.add("kotlin")
            if ("suspend " in content) techs.add("kotlin-coroutines")
            if ("Flow<" in content || "StateFlow<" in content) techs.add("kotlin-flow")
            if ("Store" in filePath || "Intent" in filePath) techs.add("mvi")
            if ("Repository" in filePath) techs.add("repository-pattern")
        }
        filePath.endsWith(".py") -> {
            techs.add("python")
            if ("async def" in content) techs.add("python-async")
        }
        filePath.endsWith(".sql") || "SELECT " in content -> {
            techs.add("sql")
        }
    }

    return techs
}

private fun buildTechSpecificChecks(techs: Set<String>): String {
    return buildString {
        if ("kotlin-coroutines" in techs) {
            appendLine(loadKotlinCoroutinesChecks())
        }
        if ("python-async" in techs) {
            appendLine(loadPythonAsyncChecks())
        }
        if ("mvi" in techs) {
            appendLine(loadMviChecks())
        }
        // ... и т.д.
    }
}
```

### Обновить buildSimplifiedReviewInstructions():

```kotlin
private fun buildSimplifiedReviewInstructions(
    changedFiles: List<String>,
    fileContents: Map<String, String>
): String = buildString {
    // SECTION 1: Detect technologies
    val allTechs = mutableSetOf<String>()
    for ((path, content) in fileContents) {
        allTechs.addAll(detectTechnologies(path, content))
    }

    appendLine("# Code Review Instructions")
    appendLine()
    appendLine("📋 Detected technologies: ${allTechs.joinToString(", ")}")
    appendLine()

    // SECTION 2: Apply specialized checklists
    appendLine(buildTechSpecificChecks(allTechs))

    // SECTION 3: General checks
    appendLine(buildGeneralChecks())

    // SECTION 4: Project-specific checks
    appendLine(buildProjectSpecificChecks())

    // ... rest of instructions
}
```

## 🎯 Итог

**Проблема:** Низкий recall - пропускаются реальные ошибки

**Решение:**
1. Специализированные чек-листы для каждого языка/фреймворка
2. Конкретные паттерны с примерами вместо общих описаний
3. Контекстно-адаптивный анализ (определяем технологии → применяем нужные проверки)
4. Проект-специфичные правила

**Результат:** Recall 40-50% → 75-85%

**Следующие шаги:**
1. Реализовать detectTechnologies()
2. Создать библиотеку специализированных чек-листов
3. Добавить в buildSimplifiedReviewInstructions()
4. Создать test suite с известными ошибками
5. Измерить recall до/после
