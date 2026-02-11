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

        if ("kotlin" in technologies) {
            appendLine(kotlinGeneralChecklist())
            appendLine()
        }

        // ALWAYS check for arithmetic errors and GlobalScope leaks in all Kotlin code
        if ("kotlin" in technologies || "kotlin-coroutines" in technologies) {
            appendLine(arithmeticErrorsChecklist())
            appendLine()

            if ("kotlin-coroutines" in technologies) {
                appendLine(globalScopeLeaksChecklist())
                appendLine()
            }
        }

        // Regression detection - ALWAYS include for ALL file types
        appendLine(regressionDetectionChecklist())
        appendLine()
    }

    internal fun kotlinGeneralChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ KOTLIN GENERAL - ОБЯЗАТЕЛЬНЫЕ ПРОВЕРКИ                        ║
╚═══════════════════════════════════════════════════════════════╝

▶ 1. КОГДА-БЛОКИ БЕЗ ВОЗВРАЩАЕМОГО ЗНАЧЕНИЯ

ЧТО ИСКАТЬ:
```kotlin
val result = when {
    condition -> {
        val temp = compute()
        if (temp > 0) {
            println("positive")
        }
        // ⚠️ ПОСЛЕДНЯЯ СТРОКА - if БЕЗ ELSE (вернет Unit!)
    }
}
```

ОСОБЕННО ОПАСНО:
- Многострочные блоки где последняя строка - if без else
- Многострочные блоки где последняя строка - println/logging
- when для присваивания, но не все ветки возвращают значение

КАК ПРОВЕРИТЬ:
Для КАЖДОГО `val x = when` или `fun f(): T = when`:
1. Последняя строка каждой ветки - выражение (не statement)?
2. Если многострочный блок - явно указано возвращаемое значение?

SEVERITY: 🔴 Critical

▶ 2. NULLABLE-ЦЕПОЧКИ БЕЗ ЗАЩИТЫ

ЧТО ИСКАТЬ:
```kotlin
val name = user?.profile?.name  // nullable
// Позже в коде:
name.length  // ⚠️ NPE если user==null или profile==null
```

КАК ПРОВЕРИТЬ:
Если используется `?.` для получения значения, проверь дальнейшее использование:
- Есть ли `?.` при обращении к методам/свойствам?
- Есть ли `?: default` для fallback значения?

SEVERITY: 🔴 Critical (если NPE возможен), 🟠 High (если редкий случай)

▶ 3. LET/ALSO/RUN/APPLY НЕПРАВИЛЬНЫЙ ВЫБОР

ЧТО ИСКАТЬ:
```kotlin
user?.let {
    it.name = newName
    it.save()
    it  // ⚠️ ЗАБЫЛИ ВЕРНУТЬ или неправильная функция
}
```

КАК ПРОВЕРИТЬ:
- `let` - трансформация (возвращает результат лямбды)
- `also` - side effect (возвращает исходный объект)
- `run` - выполнить блок кода (возвращает результат лямбды)
- `apply` - конфигурация (возвращает исходный объект)

Убедись, что выбрана правильная функция для use case.

SEVERITY: 🟡 Medium (logic smell)

▶ 4. SMART CAST НЕ РАБОТАЕТ

ЧТО ИСКАТЬ:
```kotlin
if (value is String) {
    // ... другой код ...
    value.length  // ⚠️ может не работать если value - var или mutable property
}
```

КАК ПРОВЕРИТЬ:
Smart cast не работает для:
- `var` переменных (могут измениться между проверкой и использованием)
- open/public mutable properties

ПРАВИЛЬНО:
```kotlin
if (value is String) {
    val str = value  // копия для smart cast
    str.length
}
```

SEVERITY: 🟠 High (compilation error или runtime crash)
    """.trimIndent()

    internal fun kotlinCoroutinesChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ KOTLIN COROUTINES - ОБЯЗАТЕЛЬНЫЕ ПРОВЕРКИ                      ║
╚═══════════════════════════════════════════════════════════════╝

⚠️ Применяй эти проверки к КАЖДОМУ файлу с suspend функциями.

▶ 0. MISSING RETURN VALUE В WHEN/IF БЛОКАХ (КРИТИЧЕСКИ ВАЖНО!)

ЧТО ИСКАТЬ:
```kotlin
val result = when {
    condition1 -> {
        val computed = doSomething()
        // ⚠️ ЗАБЫЛИ ВЕРНУТЬ computed!
    }
    else -> defaultValue
}
// result будет Unit вместо ожидаемого типа!
```

КАК ПРОВЕРИТЬ:
1. Найди все присваивания вида `val x = when { ... }`
2. Убедись, что КАЖДАЯ ветка when ЯВНО возвращает значение
3. Если ветка содержит несколько строк, последняя строка должна быть выражение (не println, не if без else)

ПРИМЕР ПРОБЛЕМЫ (из реального кода):
```kotlin
val availableTools = when {
    includeTools != null -> {
        val filtered = allTools.filter { it.name in includeTools }
        if (filtered.isEmpty() && includeTools.isNotEmpty()) {
            println("WARNING")
        }
        // ⚠️ НЕТ RETURN! availableTools станет Unit
    }
    else -> allTools
}
```

КАК ПРОЯВИТСЯ:
```
Type mismatch: inferred type is Unit but List<Tool> was expected
```
Компиляция упадет с ошибкой типов.

ПРАВИЛЬНО:
```kotlin
val availableTools = when {
    includeTools != null -> {
        val filtered = allTools.filter { it.name in includeTools }
        if (filtered.isEmpty() && includeTools.isNotEmpty()) {
            println("WARNING")
        }
        filtered  // ✅ явный return
    }
    else -> allTools
}
```

SEVERITY: 🔴 Critical (код не скомпилируется или приведет к ClassCastException в runtime)

⚠️ ДОПОЛНИТЕЛЬНО ПРОВЕРЬ:
- `val x = if (...) { ... } else { ... }` - каждая ветка возвращает значение?
- `fun foo(): T = when { ... }` - каждая ветка возвращает T?
- Многострочные блоки в when/if - последняя строка это выражение, а не statement?

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

    internal fun kotlinFlowChecklist() = """
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

    internal fun mviChecklist() = """
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

    internal fun repositoryPatternChecklist() = """
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

    internal fun pythonAsyncChecklist() = """
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

    internal fun sqlChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ SQL QUERIES - ОБЯЗАТЕЛЬНЫЕ ПРОВЕРКИ                           ║
╚═══════════════════════════════════════════════════════════════╝

▶ 1. SQL INJECTION

ЧТО ИСКАТЬ:
```kotlin
val query = "SELECT * FROM users WHERE name = '${'$'}userName'"  // ⚠️ INJECTION
database.execute(query)
```

КАК ПРОВЕРИТЬ:
- Используются ли prepared statements или параметризованные запросы?
- Строки НЕ должны конкатенироваться с пользовательским вводом через "${'$'}" или +

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
val query = ${'\"'}${'\"'}${'\"'}
    SELECT u.*, p.*
    FROM users u
    LEFT JOIN posts p ON p.user_id = u.id
${'\"'}${'\"'}${'\"'}
```

SEVERITY: 🟠 High (performance)
    """.trimIndent()

    internal fun configSecurityChecklist() = """
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
  password: ${'$'}{DB_PASSWORD}  # из environment variable
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

    internal fun arithmeticErrorsChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ ARITHMETIC ERRORS - CRITICAL CHECKS                          ║
╚═══════════════════════════════════════════════════════════════╝

▶ 1. DIVISION BY ZERO

ЧТО ИСКАТЬ:
```kotlin
val ratio = numerator / denominator  // ⚠️ What if denominator == 0?
val percent = count / total * 100     // ⚠️ What if total == 0?
```

КАК ПРОВЕРИТЬ:
1. Найди все операции деления (/ или %)
2. Проверь, есть ли защита от нулевого делителя:
   - `if (denominator != 0)` перед делением
   - `denominator.takeIf { it != 0 } ?: return default`
3. Особенно опасно:
   - Деление на результат функции (может вернуть 0)
   - Деление на поле объекта (может быть 0)
   - Деление на length/size коллекций (может быть пустой)

ПРИМЕР ПРОБЛЕМЫ:
```kotlin
fun calculateCompressionRatio(original: String, compressed: String): Double {
    val ratio = compressed.length / original.length  // ⚠️ ArithmeticException если original пустой
    return ratio.toDouble()
}
```

КАК ПРОЯВИТСЯ:
```
java.lang.ArithmeticException: / by zero
    at TestCriticalBugs.calculateCompressionRatio(TestCriticalBugs.kt:18)
```

ПРАВИЛЬНО:
```kotlin
fun calculateCompressionRatio(original: String, compressed: String): Double {
    if (original.isEmpty()) return 0.0
    return compressed.length.toDouble() / original.length
}
```

SEVERITY: 🔴 Critical (runtime crash)

▶ 2. INTEGER OVERFLOW

ЧТО ИСКАТЬ:
```kotlin
var total = 0
for (size in sizes) {
    total += size  // ⚠️ Can overflow if sum > Int.MAX_VALUE
}
```

КАК ПРОВЕРИТЬ:
1. Суммирование в цикле — может ли сумма превысить Int.MAX_VALUE?
2. Умножение больших чисел — может ли результат превысить лимит?
3. Должен ли использоваться Long вместо Int?

ПРИМЕР ПРОБЛЕМЫ:
```kotlin
fun calculateTotalSize(sizes: List<Int>): Int {
    var total = 0
    for (size in sizes) {
        total += size  // ⚠️ Overflow: 2147483647 + 1 = -2147483648
    }
    return total
}
```

ПРАВИЛЬНО:
```kotlin
fun calculateTotalSize(sizes: List<Int>): Long {
    var total = 0L  // Use Long to prevent overflow
    for (size in sizes) {
        total += size
    }
    return total
}
```

SEVERITY: 🟠 High (silent data corruption)

▶ 3. FLOATING POINT COMPARISON

ЧТО ИСКАТЬ:
```kotlin
if (value == 0.1 + 0.2) {  // ⚠️ May not work due to floating point precision
    // This block may never execute
}
```

КАК ПРОВЕРИТЬ:
Используется ли `==` для сравнения Float/Double?

ПРАВИЛЬНО:
```kotlin
import kotlin.math.abs
if (abs(value - (0.1 + 0.2)) < 0.00001) {  // Use epsilon for comparison
    // ...
}
```

SEVERITY: 🟡 Medium (logic bug)
    """.trimIndent()

    internal fun globalScopeLeaksChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ GLOBALSCOPE LEAKS - MEMORY LEAK DETECTION                    ║
╚═══════════════════════════════════════════════════════════════╝

▶ 1. GLOBALSCOPE.LAUNCH

ЧТО ИСКАТЬ:
```kotlin
class AgentStore {
    fun loadTools() {
        GlobalScope.launch {  // ⚠️ NOT TIED TO LIFECYCLE
            loadToolsInternal()
        }
    }
}
```

КАК ПРОВЕРИТЬ:
1. Найди использование `GlobalScope.launch`
2. GlobalScope НЕ привязан к lifecycle компонента
3. Корутина продолжит работу даже после уничтожения класса

КАК ПРОЯВИТСЯ:
- Корутина продолжает работать после закрытия экрана/компонента
- Обновления приходят в уничтоженный UI → crash
- Memory leak: объект не может быть собран GC

ПРАВИЛЬНО:
```kotlin
class AgentStore(private val coroutineScope: CoroutineScope) {
    fun loadTools() {
        coroutineScope.launch {  // ✅ Tied to lifecycle
            loadToolsInternal()
        }
    }
}
```

Или с ViewModelScope (Android):
```kotlin
class AgentViewModel : ViewModel() {
    fun loadTools() {
        viewModelScope.launch {  // ✅ Cancelled when ViewModel cleared
            loadToolsInternal()
        }
    }
}
```

SEVERITY: 🔴 Critical (memory leak)

▶ 2. GLOBALSCOPE IN REPOSITORIES

ЧТО ИСКАТЬ:
```kotlin
class Repository {
    fun startBackgroundSync() {
        GlobalScope.launch {  // ⚠️ WHO WILL CANCEL THIS?
            while (true) {
                sync()
                delay(60000)
            }
        }
    }
}
```

КАК ПРОВЕРИТЬ:
Repository/Service classes НЕ ДОЛЖНЫ использовать GlobalScope.
Они должны принимать CoroutineScope в конструкторе.

SEVERITY: 🔴 Critical (memory leak, zombie coroutines)

▶ 3. ASYNC WITHOUT AWAIT

ЧТО ИСКАТЬ:
```kotlin
GlobalScope.async {  // ⚠️ Deferred never awaited
    heavyComputation()
}
// Result is lost, coroutine may leak
```

КАК ПРОВЕРИТЬ:
`GlobalScope.async {}` ДОЛЖЕН быть awaited где-то в коде.
Иначе это потенциальный leak.

SEVERITY: 🟠 High (resource waste)
    """.trimIndent()

    internal fun regressionDetectionChecklist() = """
╔═══════════════════════════════════════════════════════════════╗
║ REGRESSION DETECTION - REMOVED CODE CHECKS                   ║
╚═══════════════════════════════════════════════════════════════╝

⚠️ Эти проверки требуют анализа diff (что было УДАЛЕНО).
   Применяй ТОЛЬКО если видишь удаленные строки в diff (с префиксом `-`).

▶ 1. REMOVED SAFETY CHECKS

ЧТО ИСКАТЬ В DIFF:
```diff
 suspend fun getRelevantChunks(query: String): List<String> {
-    if (!indexLoaded) throw IllegalStateException("Index not loaded")
     val embedding = generateEmbedding(query)
     return searchVector(embedding)
 }
```

КАК ПРОВЕРИТЬ:
1. Найди удаленные строки вида:
   - `if (!condition) throw Exception`
   - `require(condition) { "..." }`
   - `check(condition) { "..." }`
2. Эти проверки защищали от некорректного состояния
3. Их удаление может привести к NPE или data corruption

ПРИМЕР:
```kotlin
// BEFORE:
suspend fun getRelevantChunks(query: String): List<String> {
    if (!indexLoaded) throw IllegalStateException("Index not loaded. Call loadIndex() first.")
    return vectorStore.search(query)
}

// AFTER:
suspend fun getRelevantChunks(query: String): List<String> {
    // ⚠️ DELETED SAFETY CHECK
    return vectorStore.search(query)  // May NPE if vectorStore not initialized
}
```

SEVERITY: 🔴 Critical (safety regression)

▶ 2. REMOVED ERROR HANDLING

ЧТО ИСКАТЬ В DIFF:
```diff
 when (permission) {
-    is PermissionResult.Denied -> {
-        return McpToolResult(content = "Permission denied", isError = true)
-    }
     is PermissionResult.AllowedWithWarning -> { ... }
 }
```

КАК ПРОВЕРИТЬ:
1. Найди удаленные case в when/if для обработки ошибок
2. Удаленные catch блоки
3. Это может привести к выполнению кода без проверки прав

ПРИМЕР:
```kotlin
// BEFORE:
when (permission) {
    is PermissionResult.Denied -> return error("Permission denied")
    is PermissionResult.Allowed -> executeTool()
}

// AFTER:
when (permission) {
    // ⚠️ DELETED: Denied case
    is PermissionResult.Allowed -> executeTool()
}
// Tool will execute even if permission denied!
```

SEVERITY: 🔴 Critical (security breach)

▶ 3. REMOVED RETRY DELAYS

ЧТО ИСКАТЬ В DIFF:
```diff
     if (attempt < maxRetries) {
         val delayMs = initialDelayMs * (1L shl attempt)
         println("Retrying...")
-        delay(delayMs)
     }
```

КАК ПРОВЕРИТЬ:
1. Найди удаленные `delay()` в retry loops
2. Без delay между попытками:
   - Rate limits будут исчерпаны
   - Сервер получит burst запросов
   - Retry становится бесполезным (все попытки моментально)

ПРИМЕР:
```kotlin
// BEFORE:
repeat(maxRetries) { attempt ->
    try {
        return apiCall()
    } catch (e: Exception) {
        if (attempt < maxRetries - 1) {
            delay(1000L * (attempt + 1))  // Exponential backoff
        }
    }
}

// AFTER:
repeat(maxRetries) { attempt ->
    try {
        return apiCall()
    } catch (e: Exception) {
        // ⚠️ DELETED: delay() — no backoff!
        // All retries happen instantly, may trigger rate limit
    }
}
```

SEVERITY: 🟠 High (rate limit exhaustion, banned IP)

▶ 4. REMOVED NULL CHECKS

ЧТО ИСКАТЬ В DIFF:
```diff
 fun processUser(userId: String): String {
     val user = findUser(userId)
-    ?: throw UserNotFoundException("User not found: ${'$'}userId")
     return user.name  // ⚠️ NPE if user is null
 }
```

КАК ПРОВЕРИТЬ:
1. Найди удаленные `?: throw` или `?: return`
2. Это защита от null values
3. Их удаление приведет к NPE

SEVERITY: 🔴 Critical (NPE crash)

▶ 5. REMOVED LOGGING/MONITORING

ЧТО ИСКАТЬ В DIFF:
```diff
     } catch (e: Exception) {
-        logger.error("Failed to process: ${'$'}{e.message}", e)
         throw e
     }
```

КАК ПРОВЕРИТЬ:
Удаление логирования затруднит debugging в production.

SEVERITY: 🟡 Medium (observability loss)
    """.trimIndent()
}
