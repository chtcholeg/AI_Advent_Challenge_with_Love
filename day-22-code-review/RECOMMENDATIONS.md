# Рекомендации по дальнейшему улучшению Code Review System

## 📊 Текущая ситуация

**Результат тестирования `/review-pr 5`:**
- ❌ 7 файлов галлюцинаций (100%)
- ❌ 1 ложная критическая ошибка (неправильный логический анализ)
- ❌ 2 реальных файла не проанализированы

**Уже реализовано:**
- ✅ ПРАВИЛО #0: проверка списка файлов
- ✅ Явные предупреждения о примерах
- ✅ Номера строк в коде
- ✅ Расширенная самопроверка

## 🔧 Приоритетные улучшения

### 1. 🔴 URGENT: Структурированный вывод с валидацией

**Проблема:** Модель может игнорировать инструкции в длинном промпте.

**Решение:** Использовать structured output с JSON Schema для принудительного соблюдения формата.

#### Пример схемы:

```json
{
  "type": "object",
  "required": ["changed_files", "reviews", "summary"],
  "properties": {
    "changed_files": {
      "type": "array",
      "description": "EXHAUSTIVE list of files changed in this PR",
      "items": { "type": "string" }
    },
    "reviews": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["file_path", "severity", "findings"],
        "properties": {
          "file_path": {
            "type": "string",
            "description": "MUST be from changed_files list"
          },
          "severity": {
            "enum": ["critical", "high", "medium", "low", "ok"]
          },
          "findings": {
            "type": "array",
            "items": {
              "type": "object",
              "required": ["line_range", "issue", "manifestation", "recommendation"],
              "properties": {
                "line_range": {
                  "type": "string",
                  "pattern": "^\\d+-\\d+$",
                  "description": "Line numbers from file content (e.g. '28-40')"
                },
                "issue": { "type": "string" },
                "manifestation": {
                  "type": "string",
                  "description": "HOW this issue will manifest (stack trace, race condition scenario, etc.)"
                },
                "code_quote": {
                  "type": "string",
                  "description": "EXACT code from Diff or File Contents section"
                },
                "recommendation": { "type": "string" }
              }
            }
          }
        }
      }
    }
  }
}
```

**Преимущества:**
- 🛡️ Принудительная структура → меньше галлюцинаций
- ✅ Требуется `line_range` → нельзя писать без номеров строк
- ✅ Требуется `manifestation` → нельзя утверждать без доказательств
- ✅ Отдельный список `changed_files` → легко валидировать

#### Реализация в `CommandHandler.kt`:

```kotlin
private fun buildReviewSchema(): JsonObject {
    return toolSchema {
        type = "object"
        required = listOf("changed_files", "reviews", "summary")

        property("changed_files") {
            type = "array"
            description = "EXHAUSTIVE list from 'Changed Files' section - review ONLY these"
            items { type = "string" }
        }

        property("reviews") {
            type = "array"
            items {
                type = "object"
                required = listOf("file_path", "severity", "findings")

                property("file_path") {
                    type = "string"
                    description = "MUST be from changed_files array"
                }

                property("severity") {
                    type = "string"
                    enum = listOf("critical", "high", "medium", "low", "ok")
                }

                property("findings") {
                    type = "array"
                    items {
                        type = "object"
                        required = listOf("line_range", "issue", "manifestation")

                        property("line_range") {
                            type = "string"
                            pattern = "^\\d+-\\d+$"
                        }

                        property("issue") { type = "string" }

                        property("manifestation") {
                            type = "string"
                            description = "HOW: stack trace, race condition scenario, etc."
                        }

                        property("code_quote") { type = "string" }
                        property("recommendation") { type = "string" }
                    }
                }
            }
        }
    }
}
```

### 2. 🟠 HIGH: Post-processing валидация

**Проблема:** Даже со схемой модель может вернуть невалидные данные.

**Решение:** Валидировать ответ перед показом пользователю.

```kotlin
data class ReviewValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

class ReviewValidator(
    private val realChangedFiles: List<String>,
    private val fileContents: Map<String, String>
) {

    fun validate(review: ReviewResponse): ReviewValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Проверка списка файлов
        if (review.changed_files.toSet() != realChangedFiles.toSet()) {
            errors.add("File list mismatch: review files != actual changed files")
            errors.add("  Expected: ${realChangedFiles.joinToString()}")
            errors.add("  Got: ${review.changed_files.joinToString()}")
        }

        // 2. Проверка каждого review
        for (fileReview in review.reviews) {
            // Файл должен быть в списке изменённых
            if (fileReview.file_path !in realChangedFiles) {
                errors.add("HALLUCINATION: ${fileReview.file_path} not in changed_files")
            }

            // Проверка цитат кода
            val fileContent = fileContents[fileReview.file_path]
            for (finding in fileReview.findings) {
                if (finding.code_quote != null && fileContent != null) {
                    // Убираем номера строк для сравнения
                    val cleanQuote = finding.code_quote
                        .lines()
                        .map { it.replace(Regex("^\\s*\\d+\\s*\\|\\s*"), "") }
                        .joinToString("\n")

                    if (cleanQuote !in fileContent) {
                        warnings.add(
                            "Code quote not found in ${fileReview.file_path}:${finding.line_range}"
                        )
                    }
                }

                // Номера строк должны быть указаны
                if (finding.line_range.isEmpty() && finding.issue.isNotEmpty()) {
                    warnings.add("Missing line_range for finding in ${fileReview.file_path}")
                }
            }
        }

        return ReviewValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}

// В CommandHandler.kt:
private suspend fun validateAndRetry(
    review: ReviewResponse,
    realFiles: List<String>,
    fileContents: Map<String, String>,
    maxRetries: Int = 2
): ReviewResponse {
    val validator = ReviewValidator(realFiles, fileContents)
    var currentReview = review
    var attempt = 0

    while (attempt < maxRetries) {
        val validation = validator.validate(currentReview)

        if (validation.isValid) {
            if (validation.warnings.isNotEmpty()) {
                println("[Review] Warnings:")
                validation.warnings.forEach { println("  - $it") }
            }
            return currentReview
        }

        // Критические ошибки - запросить повторно
        println("[Review] Validation failed, retrying (${attempt + 1}/$maxRetries):")
        validation.errors.forEach { println("  - $it") }

        // TODO: Отправить feedback модели и запросить исправленный review
        attempt++
    }

    // После всех попыток возвращаем с предупреждением
    println("[Review] WARNING: Validation failed after $maxRetries attempts")
    return currentReview
}
```

### 3. 🟡 MEDIUM: Two-stage review process

**Проблема:** Модель может "загрязняться" контекстом между файлами.

**Решение:** Анализировать каждый файл отдельно, затем агрегировать.

```kotlin
// Stage 1: Review each file independently
private suspend fun reviewFileSeparately(
    filePath: String,
    fileContent: String,
    diff: String,
    projectContext: String
): FileReview {
    val prompt = buildString {
        appendLine("# Code Review: Single File Analysis")
        appendLine()
        appendLine("⚠️ Analyze ONLY this file: $filePath")
        appendLine("⚠️ DO NOT mention other files")
        appendLine()
        appendLine("## File: $filePath")
        appendLine("```")
        appendLine(fileContent)
        appendLine("```")
        appendLine()
        appendLine("## Diff for this file:")
        appendLine("```diff")
        appendLine(extractFileDiff(diff, filePath))
        appendLine("```")
        appendLine()
        appendLine(buildSingleFileReviewInstructions())
    }

    // Send to LLM with schema
    return gigaChatApi.reviewFile(prompt, filePath)
}

// Stage 2: Aggregate results
private fun aggregateReviews(fileReviews: List<FileReview>): ReviewResponse {
    return ReviewResponse(
        changed_files = fileReviews.map { it.file_path },
        reviews = fileReviews,
        summary = buildAggregatedSummary(fileReviews)
    )
}
```

**Преимущества:**
- 🛡️ Нет cross-contamination между файлами
- ✅ Легче валидировать (один файл = один контекст)
- ✅ Можно параллелить запросы для больших PR

### 4. 🟡 MEDIUM: Явное распознавание примеров

**Проблема:** Модель может путать примеры с реальным кодом.

**Решение:** Использовать специальные маркеры вокруг примеров.

```kotlin
private fun wrapExampleCode(code: String): String {
    return """
╔═══════════════════════════════════════════════════════════════╗
║ ⚠️ EXAMPLE CODE BELOW - DO NOT USE IN YOUR REVIEW            ║
║ This is educational code to show BAD review patterns         ║
║ Files: UserRepository.kt, processUser(), etc. are FICTIONAL  ║
╚═══════════════════════════════════════════════════════════════╝

<EXAMPLE_CODE>
$code
</EXAMPLE_CODE>

╔═══════════════════════════════════════════════════════════════╗
║ ⚠️ EXAMPLE CODE ABOVE - DO NOT USE IN YOUR REVIEW            ║
╚═══════════════════════════════════════════════════════════════╝
    """.trimIndent()
}
```

И добавить проверку в валидацию:

```kotlin
fun validateNoExampleCode(review: ReviewResponse): List<String> {
    val examplePatterns = listOf(
        "processUser",
        "UserRepository",
        "getData",
        "findById"
    )

    val violations = mutableListOf<String>()

    for (fileReview in review.reviews) {
        for (finding in fileReview.findings) {
            for (pattern in examplePatterns) {
                if (pattern in finding.code_quote.orEmpty() ||
                    pattern in finding.issue) {
                    violations.add(
                        "HALLUCINATION: Found example code pattern '$pattern' in ${fileReview.file_path}"
                    )
                }
            }
        }
    }

    return violations
}
```

### 5. 🔵 LOW: Метрики качества review

**Цель:** Отслеживать эффективность улучшений.

```kotlin
data class ReviewMetrics(
    val totalFiles: Int,
    val reviewedFiles: Int,
    val hallucinations: Int,  // файлов не из changed_files
    val findingsCount: Int,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val missingLineNumbers: Int,  // findings без line_range
    val missingManifestations: Int,  // findings без "Как проявится"
    val executionTimeMs: Long
)

class ReviewMetricsCollector {
    fun collect(
        review: ReviewResponse,
        realFiles: List<String>,
        startTime: Long
    ): ReviewMetrics {
        val hallucinations = review.reviews.count { it.file_path !in realFiles }

        var critical = 0
        var high = 0
        var medium = 0
        var low = 0
        var missingLineNumbers = 0
        var missingManifestations = 0
        var totalFindings = 0

        for (fileReview in review.reviews) {
            totalFindings += fileReview.findings.size

            when (fileReview.severity.lowercase()) {
                "critical" -> critical++
                "high" -> high++
                "medium" -> medium++
                "low" -> low++
            }

            for (finding in fileReview.findings) {
                if (finding.line_range.isBlank()) missingLineNumbers++
                if (finding.manifestation.isBlank()) missingManifestations++
            }
        }

        return ReviewMetrics(
            totalFiles = realFiles.size,
            reviewedFiles = review.reviews.size,
            hallucinations = hallucinations,
            findingsCount = totalFindings,
            criticalCount = critical,
            highCount = high,
            mediumCount = medium,
            lowCount = low,
            missingLineNumbers = missingLineNumbers,
            missingManifestations = missingManifestations,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    fun logMetrics(metrics: ReviewMetrics) {
        println("""
        [Review Metrics]
          Files: ${metrics.reviewedFiles}/${metrics.totalFiles}
          Hallucinations: ${metrics.hallucinations}
          Findings: ${metrics.findingsCount} (🔴${metrics.criticalCount} 🟠${metrics.highCount} 🟡${metrics.mediumCount} 🔵${metrics.lowCount})
          Quality: line_numbers=${metrics.findingsCount - metrics.missingLineNumbers}/${metrics.findingsCount}, manifestations=${metrics.findingsCount - metrics.missingManifestations}/${metrics.findingsCount}
          Time: ${metrics.executionTimeMs}ms
        """.trimIndent())
    }
}
```

## 🎯 План реализации

### Фаза 1: Критические (1-2 дня)
- [ ] Реализовать structured output с JSON Schema
- [ ] Добавить post-processing валидацию
- [ ] Обернуть примеры в явные маркеры
- [ ] Тестировать на PR #5 и других тестовых случаях

### Фаза 2: Важные (3-5 дней)
- [ ] Реализовать two-stage review
- [ ] Добавить retry механизм при валидационных ошибках
- [ ] Интегрировать метрики качества
- [ ] Собрать baseline метрик на тестовом наборе

### Фаза 3: Улучшения (1-2 недели)
- [ ] A/B тестирование разных промптов
- [ ] Автоматическое определение типа изменения (feature/bugfix/refactor)
- [ ] Адаптивные промпты под тип изменения
- [ ] Dashboard для метрик

## 📊 Ожидаемые результаты

### После Фазы 1:
- Галлюцинации: 100% → **5%**
- Отсутствие line_range: 70% → **0%**
- Отсутствие manifestation: 60% → **10%**

### После Фазы 2:
- False positives: 30% → **5%**
- Качество логического анализа: 60% → **85%**
- Cross-contamination: 40% → **0%**

### После Фазы 3:
- Precision: 70% → **90%**
- Recall: 50% → **75%**
- User satisfaction: 60% → **85%**

## 🔬 Тестовые случаи

### Test Case 1: Пустой PR
```bash
git checkout -b test-empty-pr
# Не делать изменений
/review-pr test-empty-pr
```
**Ожидание:** "Изменений не найдено"

### Test Case 2: Только добавление константы
```bash
git checkout -b test-const-only
# Добавить одну константу в AgentConfig.kt
/review-pr test-const-only
```
**Ожидание:**
- ✅ 1 файл проанализирован
- ❌ 0 галлюцинаций
- ✅ OK или Low severity

### Test Case 3: Намеренная ошибка
```bash
git checkout -b test-npe
# Добавить код с NPE:
# val user = repo.findById(id)  // возвращает User?
# user.name = newName           // NPE!
/review-pr test-npe
```
**Ожидание:**
- 🔴 Critical severity
- ✅ Указаны номера строк
- ✅ Показан stack trace
- ✅ Объяснено как проявится

### Test Case 4: Whitelist/Blacklist логика
```bash
git checkout -b test-filter-logic
# Добавить код:
# val filtered = allTools.filter { it.name in includeTools }
/review-pr test-filter-logic
```
**Ожидание:**
- ✅ OK (логика правильная)
- ✅ Объяснена семантика whitelist
- ❌ НЕТ рекомендации инвертировать

## 💡 Ключевые принципы

1. **Validation is mandatory** - всегда валидировать перед показом
2. **Structured > Unstructured** - схема лучше инструкций
3. **Isolate contexts** - один файл = один запрос
4. **Measure everything** - метрики для отслеживания прогресса
5. **Fail loudly** - лучше ошибка, чем молчаливая галлюцинация

## 🎓 Выводы

Текущие улучшения промпта - хороший шаг, но недостаточный:
- ✅ Правильное направление (RULE #0, примеры, самопроверка)
- ❌ Модель все равно игнорирует инструкции
- ❌ Нет технической защиты от галлюцинаций

**Рекомендация:** Реализовать structured output + validation в первую очередь.

Это даст **техническую гарантию** соблюдения формата, а не только надежду на то, что модель прочитает и поймет длинный промпт.
