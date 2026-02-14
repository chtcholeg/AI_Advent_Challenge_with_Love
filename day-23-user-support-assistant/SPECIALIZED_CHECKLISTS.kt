package ru.chtcholeg.agent.domain.service

/**
 * Библиотека специализированных чек-листов для code review.
 * Каждый чек-лист содержит конкретные паттерны ошибок с примерами.
 */
object SpecializedChecklists {

    /**
     * Определяет технологии в файле на основе расширения и содержимого.
     */
    fun detectTechnologies(filePath: String, content: String): Set<String> {
        val techs = mutableSetOf<String>()

        when {
            filePath.endsWith(".kt") -> {
                techs.add("kotlin")
                if ("suspend " in content || "suspend:" in content) {
                    techs.add("kotlin-coroutines")
                }
                if ("Flow<" in content || "StateFlow<" in content || "MutableStateFlow" in content) {
                    techs.add("kotlin-flow")
                }
                if ("Store" in filePath || "Intent" in filePath || "Reducer" in filePath) {
                    techs.add("mvi")
                }
                if ("Repository" in filePath || "Repository" in content) {
                    techs.add("repository-pattern")
                }
            }
            filePath.endsWith(".py") -> {
                techs.add("python")
                if ("async def" in content || "await " in content) {
                    techs.add("python-async")
                }
            }
            filePath.endsWith(".sql") || "SELECT " in content || "INSERT " in content -> {
                techs.add("sql")
            }
            filePath.endsWith(".toml") || filePath.endsWith(".yaml") || filePath.endsWith(".yml") -> {
                techs.add("config")
            }
        }

        return techs
    }

    /**
     * Генерирует специализированные инструкции на основе обнаруженных технологий.
     */
    fun buildTechSpecificInstructions(
        technologies: Set<String>,
        changedFiles: Map<String, Set<String>>  // file -> its technologies
    ): String = buildString {
        appendLine("═══════════════════════════════════════")
        appendLine("СПЕЦИАЛИЗИРОВАННЫЕ ПРОВЕРКИ")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("📋 Обнаружены технологии: ${technologies.joinToString(", ")}")
        appendLine()
        appendLine("Для КАЖДОГО файла применяй соответствующие проверки:")
        for ((file, fileTechs) in changedFiles) {
            appendLine("  - $file → ${fileTechs.joinToString(", ")}")
        }
        appendLine()

        if ("kotlin-coroutines" in technologies) {
            appendLine(kotlinCoroutinesChecklist())
            appendLine()
        }

        if ("kotlin-flow" in technologies) {
            appendLine(kotlinFlowChecklist())
            appendLine()
        }

        if ("mvi" in technologies) {
            appendLine(mviChecklist())
            appendLine()
        }

        if ("repository-pattern" in technologies) {
            appendLine(repositoryPatternChecklist())
            appendLine()
        }

        if ("python-async" in technologies) {
            appendLine(pythonAsyncChecklist())
            appendLine()
        }

        if ("sql" in technologies) {
            appendLine(sqlChecklist())
            appendLine()
        }

        if ("config" in technologies) {
            appendLine(configSecurityChecklist())
            appendLine()
        }
    }

    private fun kotlinCoroutinesChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ KOTLIN COROUTINES - ОБЯЗАТЕЛЬНЫЕ ПРОВЕРКИ                      ║
╚═══════════════════════════════════════════════════════════════╝

⚠️ Применяй эти проверки к КАЖДОМУ файлу с suspend функциями.

▶ 1. SHARED MUTABLE STATE БЕЗ СИНХРОНИЗАЦИИ

ЧТО ИСКАТЬ:
```kotlin
class Repository {
    private var cache = mutableMapOf<K, V>()      // ⚠️ mutable на уровне класса
    private val list = mutableListOf<T>()         // ⚠️ mutable на уровне класса

    suspend fun update() {
        cache[key] = value  // ⚠️ доступ без синхронизации
    }
}
```

КАК ПРОВЕРИТЬ:
1. Найди все `var` и `mutable*` (mutableMapOf, mutableListOf) на уровне класса
2. Проверь, защищены ли они `Mutex.withLock {}` или `synchronized()`
3. ❌ ИСКЛЮЧЕНИЯ (это безопасно, НЕ отмечай):
   - `private val _flow = MutableStateFlow()` + публичный `val flow = _flow.asStateFlow()`
   - `val local = mutableListOf()` внутри функции (локальная переменная)

ПРИМЕР ПРОБЛЕМЫ:
```kotlin
class Repo {
    private val cache = mutableMapOf<String, Data>()  // ⚠️ SHARED

    suspend fun get(key: String) = cache.getOrPut(key) {  // ⚠️ НЕ СИНХРОНИЗИРОВАНО
        api.fetch(key)
    }
}
```

КАК ПРОЯВИТСЯ:
Две корутины вызывают `get("same-key")` параллельно:
1. Обе проверяют `cache["same-key"]` → null
2. Обе входят в `getOrPut` блок
3. Обе делают `api.fetch("same-key")` → дублирование запросов
4. HashMap может быть corrupted при concurrent put → ConcurrentModificationException

SEVERITY: 🔴 Critical

▶ 2. NULLABLE RETURN БЕЗ ПРОВЕРКИ

ЧТО ИСКАТЬ:
```kotlin
fun findUser(id: String): User?  // возвращает nullable

suspend fun process(id: String) {
    val user = findUser(id)
    user.name = "test"  // ⚠️ NPE если findUser вернёт null
}
```

КАК ПРОВЕРИТЬ:
1. Найди вызовы функций, возвращающих `T?`
2. Проверь, что после вызова есть:
   - `?.let { }` или
   - `?: return` или
   - `?: throw Exception()` или
   - явная проверка `if (x != null)`

SEVERITY: 🔴 Critical (если NPE возможен), 🟠 High (если редкий случай)

▶ 3. STATEFLOW UPDATE RACE CONDITION

ЧТО ИСКАТЬ:
```kotlin
_state.value = _state.value.copy(count = _state.value.count + 1)  // ⚠️ RACE
```

КАК ПРОВЕРИТЬ:
Используется ли `_state.update { it.copy(...) }` вместо прямого присваивания?

ПРИМЕР ПРОБЛЕМЫ:
```kotlin
// Три корутины делают increment параллельно:
_state.value = _state.value.copy(count = _state.value.count + 1)
// Ожидание: count = 3
// Реальность: count = 1 (lost updates)
```

КАК ПРОЯВИТСЯ:
Read-modify-write без атомарности → lost updates.

ПРАВИЛЬНО:
```kotlin
_state.update { it.copy(count = it.count + 1) }
```

SEVERITY: 🟠 High

▶ 4. MISSING ERROR HANDLING В SUSPEND ФУНКЦИЯХ

ЧТО ИСКАТЬ:
```kotlin
suspend fun load() {
    val data = api.fetch()  // может бросить HttpException, TimeoutException
    _state.value = Success(data)
}
```

КАК ПРОВЕРИТЬ:
Есть ли `try-catch` или `runCatching` вокруг suspend вызовов API/БД?

SEVERITY: 🟠 High (если ошибка может сломать UI), 🟡 Medium (если есть fallback)

▶ 5. WITHCONTEXT НЕПРАВИЛЬНЫЙ ДИСПАТЧЕР

ЧТО ИСКАТЬ:
```kotlin
suspend fun heavyComputation() {
    // Выполняется на Dispatchers.Main (если вызвана из UI)
    val result = complexCalculation()  // ⚠️ блокирует UI
}
```

КАК ПРОВЕРИТЬ:
- CPU-интенсивная работа → должна быть в `withContext(Dispatchers.Default)`
- IO операции (сеть, файлы, БД) → должна быть в `withContext(Dispatchers.IO)`

SEVERITY: 🟡 Medium (performance issue)
    """.trimIndent()

    private fun kotlinFlowChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ KOTLIN FLOW - ОБЯЗАТЕЛЬНЫЕ ПРОВЕРКИ                           ║
╚═══════════════════════════════════════════════════════════════╝

▶ 1. FLOW COLLECTION БЕЗ LIFECYCLE (Android)

ЧТО ИСКАТЬ:
```kotlin
class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.state.collect { state ->  // ⚠️ УТЕЧКА
            updateUI(state)
        }
    }
}
```

КАК ПРОВЕРИТЬ:
В Android должно быть:
- `lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collect {} } }`
или
- `flow.flowWithLifecycle(lifecycle).launchIn(lifecycleScope)`

SEVERITY: 🔴 Critical (memory leak)

▶ 2. FLOW БЕЗ CANCELLATION (Desktop)

ЧТО ИСКАТЬ:
```kotlin
class MyViewModel {
    init {
        flow.collect { }  // ⚠️ КТО ОСТАНОВИТ?
    }
}
```

КАК ПРОВЕРИТЬ:
Есть ли `scope.cancel()` в cleanup методе?

SEVERITY: 🔴 Critical (memory leak)

▶ 3. SHAREDFLOW БЕЗ REPLAY/BUFFER STRATEGY

ЧТО ИСКАТЬ:
```kotlin
private val _events = MutableSharedFlow<Event>()  // replay=0, buffer=0
```

КАК ПРОВЕРИТЬ:
- Если события важны (например, navigation) → нужен `replay = 1`
- Если может быть burst → нужен `extraBufferCapacity`

SEVERITY: 🟡 Medium (lost events)
    """.trimIndent()

    private fun mviChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ MVI STATE MANAGEMENT - ОБЯЗАТЕЛЬНЫЕ ПРОВЕРКИ                  ║
╚═══════════════════════════════════════════════════════════════╝

▶ 1. MUTABLE ТИПЫ ВНУТРИ STATE

ЧТО ИСКАТЬ:
```kotlin
data class State(
    val items: MutableList<Item>  // ⚠️ MUTABLE внутри immutable
)
```

КАК ПРОВЕРИТЬ:
State data class должен содержать ТОЛЬКО immutable типы:
- ✅ List, Set, Map (не Mutable*)
- ✅ String, Int, Boolean
- ✅ другие data classes (тоже immutable)

SEVERITY: 🔴 Critical (нарушение immutability)

▶ 2. DIRECT STATE MUTATION

ЧТО ИСКАТЬ:
```kotlin
_state.value.loading = true  // ⚠️ ПРЯМАЯ МУТАЦИЯ
```

КАК ПРОВЕРИТЬ:
Должно быть: `_state.value = _state.value.copy(loading = true)`

SEVERITY: 🔴 Critical

▶ 3. SIDE EFFECTS В INTENT HANDLERS

ЧТО ИСКАТЬ:
```kotlin
when (intent) {
    is LoadData -> {
        launch {  // ⚠️ SIDE EFFECT В REDUCER
            val data = repository.load()
            _state.value = state.copy(data = data)
        }
    }
}
```

КАК ПРОВЕРИТЬ:
В чистом MVI intent handler должен:
- Вернуть новый state (синхронно)
- Side effects (launch, suspend calls) должны быть в отдельном слое

SEVERITY: 🟡 Medium (architecture smell)
    """.trimIndent()

    private fun repositoryPatternChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ REPOSITORY PATTERN - ОБЯЗАТЕЛЬНЫЕ ПРОВЕРКИ                    ║
╚═══════════════════════════════════════════════════════════════╝

▶ 1. ОТСУТСТВИЕ КЭШИРОВАНИЯ ПРИ ПОВТОРНЫХ ЗАПРОСАХ

ЧТО ИСКАТЬ:
```kotlin
class UserRepository {
    suspend fun getUser(id: String): User {
        return api.fetchUser(id)  // ⚠️ КАЖДЫЙ РАЗ НОВЫЙ ЗАПРОС
    }
}
```

КАК ПРОВЕРИТЬ:
Для read-heavy операций с редко меняющимися данными должен быть кэш.

SEVERITY: 🟡 Medium (performance), 🔵 Low (если данные часто меняются)

▶ 2. КЭШ БЕЗ INVALIDATION

ЧТО ИСКАТЬ:
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

КАК ПРОВЕРИТЬ:
Если есть кэш + операции изменения (update/delete) → должна быть invalidation:
```kotlin
suspend fun update(id: String, newData: Data) {
    api.update(id, newData)
    cache[id] = newData  // или cache.remove(id)
}
```

SEVERITY: 🔴 Critical (stale data)

▶ 3. INCONSISTENT ERROR HANDLING

ЧТО ИСКАТЬ:
```kotlin
suspend fun getUser(id: String): User {
    return try {
        api.fetchUser(id)
    } catch (e: Exception) {
        throw UserNotFoundException()  // ✅ обработано
    }
}

suspend fun getPost(id: String): Post {
    return api.fetchPost(id)  // ⚠️ НЕТ обработки
}
```

КАК ПРОВЕРИТЬ:
Все публичные методы репозитория должны иметь:
- Одинаковый подход к error handling
- Либо все бросают доменные исключения
- Либо все возвращают Result<T>

SEVERITY: 🟡 Medium (inconsistency)
    """.trimIndent()

    private fun pythonAsyncChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ PYTHON ASYNC - ОБЯЗАТЕЛЬНЫЕ ПРОВЕРКИ                          ║
╚═══════════════════════════════════════════════════════════════╝

▶ 1. BLOCKING OPERATIONS В ASYNC ФУНКЦИЯХ

ЧТО ИСКАТЬ:
```python
async def process():
    data = requests.get(url)      # ⚠️ БЛОКИРУЕТ event loop
    time.sleep(1)                 # ⚠️ БЛОКИРУЕТ
    with open('file.txt') as f:   # ⚠️ БЛОКИРУЕТ
        content = f.read()
```

КАК ПРОВЕРИТЬ:
В async функциях должны быть только:
- `await asyncio.sleep()` (НЕ `time.sleep`)
- `async with aiohttp.ClientSession()` (НЕ `requests`)
- `async with aiofiles.open()` (НЕ встроенный `open`)

SEVERITY: 🔴 Critical (блокирует event loop)

▶ 2. MISSING AWAIT

ЧТО ИСКАТЬ:
```python
async def get_data():
    return await api.fetch()

async def process():
    data = get_data()  # ⚠️ ЗАБЫЛИ await
    print(data)        # напечатает <coroutine object>
```

КАК ПРОВЕРИТЬ:
Все вызовы async функций должны иметь `await`.

SEVERITY: 🔴 Critical (логическая ошибка)

▶ 3. ASYNCIO.CREATE_TASK БЕЗ СОХРАНЕНИЯ

ЧТО ИСКАТЬ:
```python
async def background():
    asyncio.create_task(long_running())  # ⚠️ ЗАБЫЛИ сохранить
    # task может быть garbage collected до завершения
```

КАК ПРОВЕРИТЬ:
`create_task()` должен быть:
- Либо awaited: `await asyncio.create_task(...)`
- Либо сохранён: `task = asyncio.create_task(...)`

SEVERITY: 🟠 High (task cancellation)
    """.trimIndent()

    private fun sqlChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ SQL QUERIES - ОБЯЗАТЕЛЬНЫЕ ПРОВЕРКИ                           ║
╚═══════════════════════════════════════════════════════════════╝

▶ 1. SQL INJECTION

ЧТО ИСКАТЬ:
```kotlin
val query = "SELECT * FROM users WHERE name = '$userName'"  // ⚠️ INJECTION
database.execute(query)
```

КАК ПРОВЕРИТЬ:
- Используются ли prepared statements или параметризованные запросы?
- Строки НЕ должны конкатенироваться с пользовательским вводом через "$" или +

ПРАВИЛЬНО:
```kotlin
val query = "SELECT * FROM users WHERE name = ?"
database.execute(query, userName)
```

SEVERITY: 🔴 Critical (security vulnerability)

▶ 2. N+1 QUERY PROBLEM

ЧТО ИСКАТЬ:
```kotlin
val users = db.query("SELECT * FROM users")
for (user in users) {
    val posts = db.query("SELECT * FROM posts WHERE user_id = ?", user.id)  // ⚠️ N запросов
}
```

КАК ПРОВЕРИТЬ:
Может ли быть JOIN или IN clause для получения всех данных за 1 запрос?

ПРАВИЛЬНО:
```kotlin
val query = """
    SELECT u.*, p.*
    FROM users u
    LEFT JOIN posts p ON p.user_id = u.id
"""
```

SEVERITY: 🟠 High (performance)
    """.trimIndent()

    private fun configSecurityChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ CONFIG FILES - SECURITY CHECKS                                ║
╚═══════════════════════════════════════════════════════════════╝

▶ 1. SECRETS В КОНФИГАХ

ЧТО ИСКАТЬ:
```yaml
database:
  password: "MySecretPassword123"  # ⚠️ HARDCODED SECRET
api_key: "sk-proj-abc123def456"    # ⚠️ API KEY
```

КАК ПРОВЕРИТЬ:
- Нет ли hardcoded passwords, API keys, tokens?
- Используются ли env variables или secret management?

ПРАВИЛЬНО:
```yaml
database:
  password: ${DB_PASSWORD}  # из environment variable
```

SEVERITY: 🔴 Critical (security)

▶ 2. ОПАСНЫЕ DEFAULTS

ЧТО ИСКАТЬ:
```yaml
security:
  enabled: false  # ⚠️ ОПАСНО в production
debug: true       # ⚠️ ОПАСНО в production
```

КАК ПРОВЕРИТЬ:
Убедись, что defaults безопасны для production.

SEVERITY: 🟠 High (security)
    """.trimIndent()
}
