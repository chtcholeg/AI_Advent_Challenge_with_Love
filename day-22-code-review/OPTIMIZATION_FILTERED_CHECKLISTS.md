# Оптимизация: Фильтрация чек-листов по технологиям

## 🎯 Текущая реализация

**Что сейчас:**
```kotlin
private fun buildTechnologyBasedInstructions(): String = buildString {
    // Генерирует ВСЕ 7 чек-листов
    appendLine(SpecializedChecklists.kotlinCoroutinesChecklist())
    appendLine(SpecializedChecklists.kotlinFlowChecklist())
    appendLine(SpecializedChecklists.mviChecklist())
    // ... и т.д.
}
```

**Результат:**
- Размер промпта: +5-7K токенов
- Для Kotlin проекта показываем Python чек-листы (избыточно)
- Для Python проекта показываем Kotlin чек-листы (избыточно)

---

## ✅ Оптимизированная реализация

### Вариант 1: Фильтрация по обнаруженным технологиям

```kotlin
/**
 * Генерирует ТОЛЬКО релевантные чек-листы на основе обнаруженных технологий.
 */
private fun buildTechnologyBasedInstructions(
    allTechs: Set<String>  // передаём из buildPrefetchedDataSection
): String = buildString {
    appendLine()
    appendLine("═══════════════════════════════════════")
    appendLine("СПЕЦИАЛИЗИРОВАННЫЕ ПРОВЕРКИ")
    appendLine("═══════════════════════════════════════")
    appendLine()
    appendLine("⚡ Обнаруженные технологии: ${allTechs.joinToString(", ")}")
    appendLine("📋 Применяй соответствующие чек-листы:")
    appendLine()

    // Kotlin Coroutines
    if ("kotlin-coroutines" in allTechs) {
        appendLine(SpecializedChecklists.kotlinCoroutinesChecklist())
        appendLine()
    }

    // Kotlin Flow
    if ("kotlin-flow" in allTechs) {
        appendLine(SpecializedChecklists.kotlinFlowChecklist())
        appendLine()
    }

    // MVI
    if ("mvi" in allTechs) {
        appendLine(SpecializedChecklists.mviChecklist())
        appendLine()
    }

    // Repository Pattern
    if ("repository-pattern" in allTechs) {
        appendLine(SpecializedChecklists.repositoryPatternChecklist())
        appendLine()
    }

    // Python Async
    if ("python-async" in allTechs) {
        appendLine(SpecializedChecklists.pythonAsyncChecklist())
        appendLine()
    }

    // SQL
    if ("sql" in allTechs) {
        appendLine(SpecializedChecklists.sqlChecklist())
        appendLine()
    }

    // Config Security
    if ("config" in allTechs) {
        appendLine(SpecializedChecklists.configSecurityChecklist())
        appendLine()
    }

    // Если ничего не найдено - добавить общие проверки
    if (allTechs.isEmpty()) {
        appendLine("⚠️ Технологии не определены. Используй общую методологию анализа.")
        appendLine()
    }
}
```

### Изменения в buildPrefetchedDataSection:

```kotlin
private fun buildPrefetchedDataSection(data: ReviewData): String = buildString {
    // ... existing code ...

    // === DETECT TECHNOLOGIES ===
    val fileToTechs = mutableMapOf<String, Set<String>>()
    val allTechs = mutableSetOf<String>()

    for ((filePath, content) in data.fileContents) {
        val techs = SpecializedChecklists.detectTechnologies(filePath, content)
        fileToTechs[filePath] = techs
        allTechs.addAll(techs)
    }

    // Сохраняем в поле класса для использования в buildSimplifiedReviewInstructions
    this@CommandHandler.detectedTechnologies = allTechs  // НОВОЕ

    // ... rest of code ...
}
```

### Изменения в buildSimplifiedReviewInstructions:

```kotlin
private fun buildSimplifiedReviewInstructions(): String = buildString {
    // ... existing code ...

    // === SPECIALIZED CHECKLISTS ===
    appendLine(buildTechnologyBasedInstructions(detectedTechnologies))  // ПЕРЕДАЁМ ТЕХНОЛОГИИ

    // ... rest of code ...
}
```

### Добавить поле в класс:

```kotlin
class CommandHandler(
    private val projectRootProvider: ProjectRootProvider,
    private val mcpRepository: McpRepository
) {
    // НОВОЕ: кэшируем обнаруженные технологии
    private var detectedTechnologies: Set<String> = emptySet()

    // ... rest of code ...
}
```

---

## 📊 Сравнение

### Текущая реализация (все чек-листы):

**Плюсы:**
- ✅ Универсально - работает всегда
- ✅ Просто - не нужна логика фильтрации
- ✅ LLM видит все паттерны

**Минусы:**
- ❌ Размер промпта: ~7K токенов
- ❌ Избыточность для специализированных проектов

**Стоимость:**
- ~7K токенов × $0.003/1K = **~$0.021 per review**

### Оптимизированная реализация (фильтрация):

**Плюсы:**
- ✅ Меньше токенов (~2-3K вместо 7K)
- ✅ Только релевантные проверки
- ✅ Быстрее обработка

**Минусы:**
- ❌ Сложнее код (нужно передавать технологии)
- ❌ Может пропустить edge cases (файл без расширения)

**Стоимость:**
- ~3K токенов × $0.003/1K = **~$0.009 per review**

**Экономия:** ~$0.012 per review (~57%)

---

## 🎯 Рекомендация

### Для начала: Оставить текущую реализацию (все чек-листы)

**Причины:**
1. Проще тестировать и отлаживать
2. Гарантированно работает
3. Разница в стоимости незначительна для малых проектов
4. Позволяет собрать статистику использования

### После тестирования: Добавить фильтрацию

**Когда:**
- После прохождения всех тестов
- После измерения recall
- Когда система стабильно работает

**Как:**
1. Измерить текущий размер промптов (в логах)
2. Реализовать фильтрацию (код выше)
3. Сравнить recall до/после
4. Если recall не упал → оставить фильтрацию

---

## 🚀 План реализации фильтрации

### Шаг 1: Измерить текущие метрики (5 минут)

```kotlin
// В buildSimplifiedReviewInstructions() добавить:
val instructions = buildString { /* existing code */ }
println("[Review] Instructions size: ${instructions.length} chars (~${instructions.length / 4} tokens)")
return instructions
```

### Шаг 2: Добавить поле detectedTechnologies (5 минут)

```kotlin
class CommandHandler(...) {
    private var detectedTechnologies: Set<String> = emptySet()
}
```

### Шаг 3: Обновить buildPrefetchedDataSection (5 минут)

```kotlin
// После определения allTechs:
this@CommandHandler.detectedTechnologies = allTechs
```

### Шаг 4: Обновить buildTechnologyBasedInstructions (10 минут)

```kotlin
private fun buildTechnologyBasedInstructions(
    allTechs: Set<String> = detectedTechnologies
): String = buildString {
    // Добавить if-ы для каждого чек-листа
}
```

### Шаг 5: Протестировать (10 минут)

```bash
# Запустить review
/review-pr 5

# Проверить:
# 1. Только kotlin + kotlin-coroutines чек-листы?
# 2. Recall не упал?
# 3. Размер промпта меньше?
```

**Общее время:** ~35 минут

---

## 💡 Альтернатива: Адаптивная фильтрация

Можно сделать гибридный подход:

```kotlin
private fun buildTechnologyBasedInstructions(
    allTechs: Set<String>
): String = buildString {
    // Всегда включаем базовые чек-листы
    val alwaysInclude = setOf("kotlin", "python", "sql")

    // Добавляем специализированные только если найдены
    val toInclude = allTechs + alwaysInclude

    if ("kotlin" in toInclude || "kotlin-coroutines" in allTechs) {
        appendLine(SpecializedChecklists.kotlinCoroutinesChecklist())
    }

    // ... и т.д.
}
```

---

## 📊 Итоговые рекомендации

### ✅ Сейчас (первые тесты):
**Оставить как есть** - все чек-листы всегда
- Проще
- Надёжнее
- Разница в $0.01 несущественна

### ✅ Потом (после стабилизации):
**Добавить фильтрацию** - только релевантные
- Экономия токенов (~57%)
- Более чистые инструкции
- Быстрее обработка

### ✅ Ещё потом (оптимизация):
**Адаптивная фильтрация** - умный выбор чек-листов
- Всегда показывать базовые (kotlin, python, sql)
- Специализированные только при обнаружении
- Балансировать между полнотой и размером

---

## 🎓 Выводы

**Текущая реализация правильная для MVP!**

Причины:
1. Простота > оптимизация на ранних этапах
2. Нужна статистика использования
3. Разница в стоимости незначительна
4. Гарантированная полнота проверок

**Оптимизация - следующий шаг после тестирования.**
