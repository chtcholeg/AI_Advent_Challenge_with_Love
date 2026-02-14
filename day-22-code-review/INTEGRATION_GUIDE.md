# Руководство по интеграции специализированных чек-листов

## 🎯 Цель

Увеличить recall (находимость ошибок) с **40-50%** до **75-85%** через специализированные чек-листы для каждого языка/фреймворка.

## 📋 План интеграции

### Шаг 1: Копировать SpecializedChecklists.kt

```bash
# Скопировать файл в проект
cp SPECIALIZED_CHECKLISTS.kt \
   ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/domain/service/SpecializedChecklists.kt
```

### Шаг 2: Обновить CommandHandler.kt

#### 2.1. Добавить в imports:

```kotlin
import ru.chtcholeg.agent.domain.service.SpecializedChecklists
```

#### 2.2. Обновить buildPrefetchedDataSection():

```kotlin
private fun buildPrefetchedDataSection(data: ReviewData): String = buildString {
    appendLine("# Данные для Code Review (собраны автоматически)")
    appendLine()

    // === НОВОЕ: Detect technologies ===
    val fileToTechs = mutableMapOf<String, Set<String>>()
    val allTechs = mutableSetOf<String>()

    for ((filePath, content) in data.fileContents) {
        val techs = SpecializedChecklists.detectTechnologies(filePath, content)
        fileToTechs[filePath] = techs
        allTechs.addAll(techs)
    }

    // === НОВОЕ: Technology summary ===
    if (allTechs.isNotEmpty()) {
        appendLine("╔═══════════════════════════════════════════════════════════════╗")
        appendLine("║ 📋 ОБНАРУЖЕННЫЕ ТЕХНОЛОГИИ                                    ║")
        appendLine("╚═══════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("⚡ В этом PR используются: ${allTechs.joinToString(", ")}")
        appendLine()
        appendLine("Применяй соответствующие специализированные проверки:")
        for ((file, techs) in fileToTechs) {
            if (techs.isNotEmpty()) {
                appendLine("  • ${file.substringAfterLast('/')} → ${techs.joinToString(", ")}")
            }
        }
        appendLine()
    }

    // === СУЩЕСТВУЮЩИЙ КОД: Changed files list ===
    appendLine("╔═══════════════════════════════════════════════════════════════════╗")
    appendLine("║ 🔴 ИЗМЕНЁННЫЕ ФАЙЛЫ — ИСЧЕРПЫВАЮЩИЙ СПИСОК                       ║")
    appendLine("╚═══════════════════════════════════════════════════════════════════╝")
    // ... rest of existing code
}
```

#### 2.3. Обновить buildSimplifiedReviewInstructions():

```kotlin
private fun buildSimplifiedReviewInstructions(): String = buildString {
    appendLine("# Code Review Instructions")
    appendLine()

    // === RULE #0: FILE LIST (existing) ===
    appendLine("╔═══════════════════════════════════════════════════════════════════╗")
    appendLine("║ 🔴 КРИТИЧЕСКОЕ ПРАВИЛО #0: СПИСОК ФАЙЛОВ (САМОЕ ВАЖНОЕ!)        ║")
    appendLine("╚═══════════════════════════════════════════════════════════════════╝")
    // ... existing file list instructions

    // === НОВОЕ: Вставить специализированные чек-листы ПЕРЕД Rule #1 ===
    appendLine(buildTechnologyBasedInstructions())

    // === RULE #1: ANTI-HALLUCINATION (existing) ===
    appendLine("╔═══════════════════════════════════════════════════════════════════╗")
    appendLine("║ 🚨 КРИТИЧЕСКОЕ ПРАВИЛО #1: ЗАПРЕТ НА ГАЛЛЮЦИНАЦИИ               ║")
    appendLine("╚═══════════════════════════════════════════════════════════════════╝")
    // ... rest of existing code
}

/**
 * Генерирует инструкции с плейсхолдером для технологий.
 * Реальные технологии определяются в buildPrefetchedDataSection() и передаются через контекст.
 */
private fun buildTechnologyBasedInstructions(): String = buildString {
    appendLine()
    appendLine("═══════════════════════════════════════")
    appendLine("СПЕЦИАЛИЗИРОВАННЫЕ ПРОВЕРКИ")
    appendLine("═══════════════════════════════════════")
    appendLine()
    appendLine("⚡ В начале контекста указаны обнаруженные технологии для каждого файла.")
    appendLine("📋 Применяй соответствующие чек-листы к каждому файлу:")
    appendLine()

    // === Kotlin Coroutines ===
    appendLine(SpecializedChecklists.kotlinCoroutinesChecklist())
    appendLine()

    // === Kotlin Flow ===
    appendLine(SpecializedChecklists.kotlinFlowChecklist())
    appendLine()

    // === MVI ===
    appendLine(SpecializedChecklists.mviChecklist())
    appendLine()

    // === Repository Pattern ===
    appendLine(SpecializedChecklists.repositoryPatternChecklist())
    appendLine()

    // === Python Async ===
    appendLine(SpecializedChecklists.pythonAsyncChecklist())
    appendLine()

    // === SQL ===
    appendLine(SpecializedChecklists.sqlChecklist())
    appendLine()

    // === Config Security ===
    appendLine(SpecializedChecklists.configSecurityChecklist())
    appendLine()

    appendLine("⚠️ ВАЖНО:")
    appendLine("1. Применяй ТОЛЬКО релевантные чек-листы к каждому файлу")
    appendLine("2. Если файл использует kotlin-coroutines → проверь ВСЕ 5 пунктов из соответствующего чек-листа")
    appendLine("3. Если паттерн НЕ найден в чек-листе → используй общую методологию анализа")
    appendLine()
}
```

### Шаг 3: Обновить fallback mode (buildFallbackReviewInstructions)

```kotlin
private fun buildFallbackReviewInstructions(isPrReview: Boolean): String = buildString {
    // ... existing code

    // === НОВОЕ: Добавить после "САМОПРОВЕРКА ПЕРЕД НАПИСАНИЕМ REVIEW" ===
    appendLine(buildTechnologyBasedInstructions())

    // === REST OF EXISTING CODE ===
    appendLine("═══════════════════════════════════════")
    appendLine("ПРИМЕРЫ ПЛОХОГО REVIEW (ТАК ДЕЛАТЬ НЕЛЬЗЯ)")
    // ...
}
```

## 📊 Ожидаемый эффект

### До интеграции:

```kotlin
// TEST CASE: Race condition in cache
class Repo {
    private val cache = mutableMapOf<String, Data>()
    suspend fun get(key: String) = cache.getOrPut(key) { api.fetch(key) }
}
```

**Результат review:** ✅ OK (пропущено)

### После интеграции:

**Результат review:**
```
**Файл:** Repository.kt:42-44
**Severity:** 🔴 Critical
**Проблема:** Shared mutable state без синхронизации

**Текущий код:**
```kotlin
42 | class Repo {
43 |     private val cache = mutableMapOf<String, Data>()  // ⚠️ SHARED MUTABLE
44 |     suspend fun get(key: String) = cache.getOrPut(key) { api.fetch(key) }  // ⚠️ НЕ СИНХРОНИЗИРОВАНО
```

**Как проявится:**
Две корутины вызывают `get("same-key")` параллельно:
1. Обе проверяют `cache["same-key"]` → null
2. Обе входят в `getOrPut` блок
3. Обе делают `api.fetch("same-key")` → дублирование запросов
4. HashMap может быть corrupted при concurrent put → ConcurrentModificationException

**Рекомендация:**
```kotlin
class Repo {
    private val cache = mutableMapOf<String, Data>()
    private val mutex = Mutex()

    suspend fun get(key: String) = mutex.withLock {
        cache.getOrPut(key) { api.fetch(key) }
    }
}
```
```

## 🧪 Тестирование

### Test Suite для проверки recall

Создать файл `test-cases.kt`:

```kotlin
// ===== TEST CASE 1: Race condition =====
// ФАЙЛ: TestRaceCondition.kt
class Repo {
    private val cache = mutableMapOf<String, Data>()
    suspend fun get(key: String) = cache.getOrPut(key) { api.fetch(key) }
}
// EXPECTED: 🔴 Critical - Race condition в cache
// ЧЕКЛИСТ: Kotlin Coroutines, пункт 1

// ===== TEST CASE 2: NPE =====
// ФАЙЛ: TestNPE.kt
suspend fun process(id: String) {
    val user = db.findById(id)  // возвращает User?
    user.name = "test"
}
// EXPECTED: 🔴 Critical - NPE
// ЧЕКЛИСТ: Kotlin Coroutines, пункт 2

// ===== TEST CASE 3: StateFlow race =====
// ФАЙЛ: TestStateFlowRace.kt
_state.value = _state.value.copy(count = _state.value.count + 1)
// EXPECTED: 🟠 High - StateFlow update race
// ЧЕКЛИСТ: Kotlin Coroutines, пункт 3

// ===== TEST CASE 4: Missing error handling =====
// ФАЙЛ: TestMissingErrorHandling.kt
suspend fun load() {
    val data = api.fetch()
    _state.value = Success(data)
}
// EXPECTED: 🟠 High - Missing error handling
// ЧЕКЛИСТ: Kotlin Coroutines, пункт 4

// ===== TEST CASE 5: SQL injection =====
// ФАЙЛ: TestSQLInjection.kt
val query = "SELECT * FROM users WHERE id = '$userId'"
database.execute(query)
// EXPECTED: 🔴 Critical - SQL injection
// ЧЕКЛИСТ: SQL, пункт 1

// ===== TEST CASE 6: Mutable in MVI state =====
// ФАЙЛ: TestMVIState.kt
data class State(val items: MutableList<Item>)
// EXPECTED: 🔴 Critical - Mutable type in immutable state
// ЧЕКЛИСТ: MVI, пункт 1

// ===== TEST CASE 7: Cache without invalidation =====
// ФАЙЛ: TestCacheInvalidation.kt
class Repository {
    private val cache = mutableMapOf<String, Data>()

    suspend fun get(id: String) = cache.getOrPut(id) { api.fetch(id) }

    suspend fun update(id: String, newData: Data) {
        api.update(id, newData)
        // НЕ ОБНОВИЛИ КЭШ!
    }
}
// EXPECTED: 🔴 Critical - Stale data in cache
// ЧЕКЛИСТ: Repository Pattern, пункт 2

// ===== TEST CASE 8: Python blocking in async =====
// ФАЙЛ: test_blocking.py
async def process():
    time.sleep(1)  # blocks event loop
    return requests.get(url)  # blocks event loop
// EXPECTED: 🔴 Critical - Blocking operations
// ЧЕКЛИСТ: Python Async, пункт 1

// ===== TEST CASE 9: Config secrets =====
// ФАЙЛ: config.yaml
database:
  password: "MySecretPassword123"
api_key: "sk-proj-abc123"
// EXPECTED: 🔴 Critical - Hardcoded secrets
// ЧЕКЛИСТ: Config Security, пункт 1

// ===== TEST CASE 10: Flow without lifecycle =====
// ФАЙЛ: TestFlowLifecycle.kt (Android)
class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.state.collect { updateUI(it) }
    }
}
// EXPECTED: 🔴 Critical - Memory leak
// ЧЕКЛИСТ: Kotlin Flow, пункт 1
```

### Запуск тестов:

```bash
# 1. Создать PR с тестовыми файлами
git checkout -b test-recall
git add test-cases.kt
git commit -m "Test: recall test suite"

# 2. Запустить review
./gradlew :ai-agent:run
# В чате:
/review-pr test-recall

# 3. Подсчитать recall
Found: X / 10 test cases
Recall: X/10 = Y%

# TARGET: >= 8/10 (80% recall)
```

### Метрики для отслеживания:

```kotlin
data class RecallMetrics(
    val totalTestCases: Int = 10,
    val foundIssues: Int,
    val recall: Double = foundIssues.toDouble() / totalTestCases,

    // По категориям:
    val concurrencyRecall: Double,  // cases 1, 3
    val nullSafetyRecall: Double,   // case 2
    val errorHandlingRecall: Double, // case 4
    val securityRecall: Double,      // cases 5, 9
    val architectureRecall: Double   // cases 6, 7, 10
)
```

## 🎯 Измерение успеха

### Baseline (до интеграции):

```
Recall: 4/10 = 40%
  ✅ Найдено: NPE, SQL injection, Hardcoded secrets, Missing error handling
  ❌ Пропущено: Race condition, StateFlow race, Mutable in state, Cache invalidation, Blocking in async, Flow lifecycle
```

### Target (после интеграции):

```
Recall: >= 8/10 = 80%
  ✅ Найдено: все кроме 1-2 сложных edge cases
  ❌ Пропущено: максимум 2 edge cases
```

### A/B тестирование:

```bash
# Создать 20 PR с известными ошибками
# Запустить review на каждом:
# - 10 PR до интеграции (baseline)
# - 10 PR после интеграции (with checklists)

# Сравнить recall:
# Baseline: X/200 errors found = Y% recall
# With checklists: Z/200 errors found = W% recall
# Improvement: (W - Y)%
```

## 💡 Дополнительные рекомендации

### 1. Постепенная интеграция

Начните с самых критичных чек-листов:

```kotlin
// Phase 1: Kotlin только
if ("kotlin-coroutines" in technologies) {
    appendLine(kotlinCoroutinesChecklist())
}

// Phase 2: + MVI
if ("mvi" in technologies) {
    appendLine(mviChecklist())
}

// Phase 3: Все остальные
```

### 2. Feedback loop

После каждого review собирайте метрики:

```kotlin
class ReviewMetricsCollector {
    fun collectAfterReview(
        review: ReviewResponse,
        actualIssues: List<KnownIssue>  // из теста
    ): RecallMetrics {
        val foundIssues = actualIssues.filter { issue ->
            review.reviews.any { it.file_path == issue.file && it.severity == issue.expectedSeverity }
        }

        return RecallMetrics(
            totalTestCases = actualIssues.size,
            foundIssues = foundIssues.size
        )
    }
}
```

### 3. Расширение чек-листов

Добавляйте новые паттерны на основе пропущенных ошибок:

```kotlin
// Если пропустили новый тип ошибки:
// 1. Добавить в SpecializedChecklists.kt
// 2. Добавить в test suite
// 3. Перезапустить тесты
// 4. Измерить улучшение recall
```

## 🎉 Итог

После интеграции:

- ✅ Recall увеличится с **40-50%** до **75-85%**
- ✅ Каждый файл анализируется со специализированным чек-листом
- ✅ Конкретные паттерны ошибок с примерами
- ✅ Измеримые метрики для отслеживания прогресса

**Следующие шаги:**
1. Интегрировать SpecializedChecklists.kt
2. Обновить CommandHandler.kt (шаги 2.1-2.3)
3. Создать test suite
4. Запустить тесты и измерить baseline recall
5. Сравнить результаты до/после
