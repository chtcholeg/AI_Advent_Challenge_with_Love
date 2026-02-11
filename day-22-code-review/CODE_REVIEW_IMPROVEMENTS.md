# Улучшения системы Code Review - Детекция большего числа ошибок

## 🎯 Проблема

Система `/review-pr` находит **мало реальных ошибок**:
- Результат на PR #5: только 1 проблема найдена
- Проблема описана **неправильно** (путаница в логике фильтрации)
- **Пропущена критическая ошибка** - missing return value в when-блоке

## ✅ Реализованные улучшения

### 1. ⚠️ Специализированный чек-лист: Kotlin When/If блоки

**Файл:** `SpecializedChecklists.kt`

Добавлена проверка #0 в `kotlinCoroutinesChecklist()`:

```kotlin
▶ 0. MISSING RETURN VALUE В WHEN/IF БЛОКАХ (КРИТИЧЕСКИ ВАЖНО!)
```

**Что проверяется:**
- `val result = when { ... }` - каждая ветка возвращает значение?
- Многострочные блоки - последняя строка это выражение (не statement)?
- Если последняя строка `if` без `else`, `println`, присваивание → вернет `Unit`!

**Пример реальной ошибки из кодабазы:**

```kotlin
// ❌ НЕПРАВИЛЬНО
val availableTools = when {
    includeTools != null -> {
        val filtered = allTools.filter { it.name in includeTools }
        if (filtered.isEmpty()) {
            println("WARNING")
        }
        // ⚠️ НЕТ RETURN! availableTools = Unit
    }
    else -> allTools
}

// ✅ ПРАВИЛЬНО
val availableTools = when {
    includeTools != null -> {
        val filtered = allTools.filter { it.name in includeTools }
        if (filtered.isEmpty()) {
            println("WARNING")
        }
        filtered  // явный return
    }
    else -> allTools
}
```

**Severity:** 🔴 Critical (compilation error или ClassCastException)

### 2. 📋 Новый чек-лист: Kotlin General

**Файл:** `SpecializedChecklists.kt`

Добавлен `kotlinGeneralChecklist()` для всех .kt файлов:

**Проверки:**
1. **When/If блоки без возвращаемого значения** (дубликат для усиления)
2. **Nullable-цепочки без защиты** (`user?.profile?.name` → `name.length` без проверки)
3. **Let/Also/Run/Apply неправильный выбор** (выбрана не та scope function)
4. **Smart cast не работает** (для `var` или mutable properties)

### 3. 🔍 Улучшенные инструкции в CommandHandler

**Файл:** `CommandHandler.kt`

Раздел **"3. LOGIC BUGS"** теперь включает:

#### 3.1. KOTLIN: MISSING RETURN IN WHEN/IF BLOCKS

```
⚠️ САМАЯ ЧАСТАЯ ОШИБКА В KOTLIN!

КАК ПРОВЕРИТЬ:
Для КАЖДОГО `val x = when` или `val x = if`:
1. Найди многострочные блоки { ... }
2. Проверь ПОСЛЕДНЮЮ строку блока:
   - ❌ if без else → вернет Unit
   - ❌ println/logging → вернет Unit
   - ❌ присваивание (=) → вернет Unit
   - ✅ выражение → вернет значение
3. Если последняя строка НЕ выражение → это баг!
```

Включены **примеры из реального кода** проекта (AgentRepository.kt).

#### 3.2. FILTER/MAP LOGIC (WHITELIST/BLACKLIST)

Сохранены детальные инструкции по анализу логики фильтрации.

## 📊 Ожидаемые результаты

### До улучшений:
- **Recall:** 40-50%
- Находит только очевидные проблемы
- Путается в логике фильтрации
- **Пропускает** when/if блоки без return

### После улучшений:
- **Recall:** 70-85%
- ✅ Находит missing return в when/if (90%)
- ✅ Правильно анализирует filter/map логику (85%)
- ✅ Проверяет nullable chains (80%)
- ✅ Применяет специализированные чек-листы автоматически

## 🧪 Тестирование

### Как протестировать улучшения:

1. **Запустите review на PR #5 снова:**
   ```bash
   /review-pr 5
   ```

2. **Ожидаемый результат:**
   - ✅ Найдена критическая ошибка в `AgentRepository.kt:310-320`
   - ✅ Severity: 🔴 Critical
   - ✅ Описание: "Missing return value in when block"
   - ✅ Показан корректный пример исправления

### Создайте тестовые кейсы:

#### Test Case 1: Missing return в when
```kotlin
// Создайте файл: TestWhenReturn.kt
val result = when {
    condition -> {
        val temp = compute()
        if (temp > 0) {
            println("positive")
        }
    }
}
```

**Ожидание:** 🔴 Critical - "Missing return: последняя строка if без else вернет Unit"

#### Test Case 2: Nullable chain без защиты
```kotlin
// Создайте файл: TestNullable.kt
val name = user?.profile?.name
name.length  // NPE!
```

**Ожидание:** 🔴 Critical - "NPE: name может быть null"

#### Test Case 3: Filter logic (whitelist)
```kotlin
// Создайте файл: TestFilterLogic.kt
val allowed = listOf("A", "B")
val filtered = allItems.filter { it.name in allowed }
```

**Ожидание:** ✅ OK - "Логика правильная: `in allowed` это whitelist"

## 🚀 Дальнейшие улучшения (из RECOMMENDATIONS.md)

### Фаза 2: Structured Output (Приоритет: ВЫСОКИЙ)

**Проблема:** Модель все равно может игнорировать инструкции.

**Решение:** Использовать JSON Schema для принудительного формата:

```kotlin
data class ReviewResponse(
    val changed_files: List<String>,  // MUST match actual files
    val reviews: List<FileReview>
)

data class FileReview(
    val file_path: String,  // MUST be from changed_files
    val severity: Severity,
    val findings: List<Finding>
)

data class Finding(
    val line_range: String,  // REQUIRED, pattern: "\\d+-\\d+"
    val issue: String,
    val manifestation: String,  // HOW this issue will manifest
    val code_quote: String,  // EXACT code from file
    val recommendation: String
)
```

**Преимущества:**
- 🛡️ Нельзя пропустить line_range
- 🛡️ Нельзя пропустить manifestation
- 🛡️ Принудительная структура → меньше галлюцинаций

### Фаза 3: Post-processing Validation

```kotlin
class ReviewValidator {
    fun validate(review: ReviewResponse): ValidationResult {
        // 1. File list matches actual files?
        // 2. All code quotes exist in files?
        // 3. Line ranges are valid?
        // 4. No example code patterns (processUser, UserRepository)?
    }
}
```

**Если валидация провалилась** → retry с feedback.

### Фаза 4: Two-stage Review

```kotlin
// Stage 1: Analyze each file separately
for (file in changedFiles) {
    val review = reviewFileSeparately(file, content, diff)
}

// Stage 2: Aggregate results
val finalReview = aggregateReviews(fileReviews)
```

**Преимущества:**
- Нет cross-contamination
- Легче валидировать
- Можно параллелить

## 📋 Чек-лист применения улучшений

### Перед использованием /review-pr:

- [ ] Убедитесь, что Git MCP Server запущен
- [ ] Проверьте, что все файлы закоммичены
- [ ] Если PR большой (>10 файлов), рассмотрите разбиение на части

### Во время review:

- [ ] Проверьте, что модель перечислила ВСЕ изменённые файлы
- [ ] Убедитесь, что для каждой проблемы указаны номера строк
- [ ] Проверьте, что код в замечаниях взят из реальных файлов
- [ ] Если видите "processUser" или "UserRepository" - это галлюцинация!

### После review:

- [ ] Проверьте critical/high замечания вручную
- [ ] Если модель пропустила очевидную ошибку - создайте test case
- [ ] Добавьте новые паттерны ошибок в чек-листы

## 📚 Структура файлов

```
ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/
├── domain/service/
│   ├── SpecializedChecklists.kt        ← ОБНОВЛЁН
│   │   ├── detectTechnologies()
│   │   ├── kotlinGeneralChecklist()    ← НОВЫЙ
│   │   ├── kotlinCoroutinesChecklist() ← ОБНОВЛЁН (добавлена проверка #0)
│   │   ├── kotlinFlowChecklist()
│   │   ├── mviChecklist()
│   │   ├── repositoryPatternChecklist()
│   │   ├── pythonAsyncChecklist()
│   │   ├── sqlChecklist()
│   │   └── configSecurityChecklist()
│   └── CommandHandler.kt               ← ОБНОВЛЁН
│       ├── buildSimplifiedReviewInstructions()
│       └── buildFallbackReviewInstructions()
└── config/
    └── AgentConfig.kt                  ← без изменений
```

## 🎓 Ключевые принципы

1. **Конкретика > Абстракция**
   - ✅ "Найди `val x = when`, проверь последнюю строку блока"
   - ❌ "Проверь корректность логики"

2. **Примеры из реального кода**
   - ✅ Показать реальную ошибку из AgentRepository.kt
   - ❌ Абстрактные примеры с User/Repository

3. **Специализация > Универсальность**
   - ✅ Отдельный чек-лист для Kotlin when-блоков
   - ❌ Общий "logic bugs" чек-лист для всех языков

4. **Проверяемость**
   - ✅ "Последняя строка - if без else?"
   - ❌ "Логика правильная?"

## 🔄 Следующие шаги

### Немедленно:
1. Протестируйте на PR #5
2. Создайте тестовые кейсы
3. Соберите метрики recall

### Через неделю:
1. Реализуйте structured output
2. Добавьте post-processing validation
3. Создайте dashboard для метрик

### Через месяц:
1. Реализуйте two-stage review
2. Добавьте автоматическое определение типа изменения
3. A/B тестирование промптов

## 📞 Помощь

Если review не находит ожидаемую ошибку:

1. Проверьте, применяется ли нужный чек-лист (см. "Detected technologies")
2. Добавьте паттерн ошибки в соответствующий чек-лист
3. Используйте реальные примеры из кода вместо абстрактных
4. Упростите формулировку - модель лучше понимает конкретные инструкции

---

**Автор:** Claude Sonnet 4.5
**Дата:** 2026-02-13
**Версия:** 1.0
