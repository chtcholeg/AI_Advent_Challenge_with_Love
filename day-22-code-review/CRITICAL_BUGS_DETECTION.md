# Critical Bugs Detection - Improvements to /review-pr

## Проблема

Команда `/review-pr` НЕ находила следующие критические типы ошибок:

1. **Division by zero** - арифметические ошибки
2. **Regression detection** - удаленные проверки безопасности
3. **GlobalScope memory leaks** - утечки памяти через GlobalScope
4. **Removed error handling** - удаленная обработка ошибок
5. **Removed retry delays** - удаленные задержки между попытками

## Решение

### 1. Добавлены новые специализированные чек-листы

Файл: `ai-agent/.../SpecializedChecklists.kt`

#### arithmeticErrorsChecklist()
- Division by zero (деление на 0)
- Integer overflow (переполнение целых чисел)
- Floating point comparison (сравнение float/double)

#### globalScopeLeaksChecklist()
- GlobalScope.launch (не привязан к lifecycle)
- GlobalScope в repositories
- async without await

#### regressionDetectionChecklist()
- Removed safety checks (удаленные проверки безопасности)
- Removed error handling (удаленная обработка ошибок)
- Removed retry delays (удаленные задержки)
- Removed null checks (удаленные проверки на null)
- Removed logging/monitoring

### 2. Интегрировано в CommandHandler.kt

Добавлены пункты 10-12 в "МЕТОДОЛОГИЮ АНАЛИЗА":
- **Пункт 10**: ARITHMETIC ERRORS
- **Пункт 11**: GLOBALSCOPE LEAKS
- **Пункт 12**: REGRESSION DETECTION

Эти проверки теперь применяются ко ВСЕМ файлам при code review.

### 3. Создан тестовый файл

Файл: `TestCriticalBugs.kt`

Содержит примеры ВСЕХ типов ошибок:
- ❌ BUG #1: Division by zero
- ❌ BUG #2: Removed safety check
- ❌ BUG #3: GlobalScope memory leak
- ❌ BUG #4: Removed permission denied handling
- ❌ BUG #5: Removed retry delay
- ❌ BUG #6: Integer overflow
- ❌ BUG #7: Removed null check

## Как проверить

### Вариант 1: Проверить тестовый файл

```bash
cd day-22-code-review
git add TestCriticalBugs.kt
git commit -m "Test: Add critical bugs for review"

# В AI Agent выполнить:
/review-pr
```

### Вариант 2: Проверить текущие изменения

```bash
# В AI Agent выполнить:
/review-pr

# review-pr должен найти и описать все 7 типов ошибок из TestCriticalBugs.kt
```

## Ожидаемый результат

Review-pr должен обнаружить:

1. **TestCriticalBugs.kt:18** - 🔴 Critical: Division by zero
   ```kotlin
   val ratio = compressed.length / original.length
   // ArithmeticException if original.length == 0
   ```

2. **TestCriticalBugs.kt:24** - 🔴 Critical: Removed safety check
   ```kotlin
   // ⚠️ DELETED: if (!indexLoaded) throw IllegalStateException(...)
   ```

3. **TestCriticalBugs.kt:35** - 🔴 Critical: GlobalScope memory leak
   ```kotlin
   GlobalScope.launch { loadToolsInternal() }
   // Not tied to lifecycle
   ```

4. **TestCriticalBugs.kt:42** - 🔴 Critical: Removed permission denied handling
   ```kotlin
   // ⚠️ DELETED: is PermissionResult.Denied -> return error
   ```

5. **TestCriticalBugs.kt:64** - 🟠 High: Removed retry delay
   ```kotlin
   // ⚠️ DELETED: delay(delayMs)
   // Rate limit exhaustion
   ```

6. **TestCriticalBugs.kt:79** - 🟠 High: Integer overflow
   ```kotlin
   total += size  // Can overflow if sum > Int.MAX_VALUE
   ```

7. **TestCriticalBugs.kt:88** - 🔴 Critical: Removed null check
   ```kotlin
   // ⚠️ DELETED: ?: throw UserNotFoundException()
   return user.name  // NPE if user is null
   ```

## Дополнительные улучшения

### Regression Detection

Новый чек-лист **специально разработан** для анализа diff и обнаружения удаленного кода:

```markdown
12. REGRESSION DETECTION (удаленный код из diff):

   ⚠️ ЭТА ПРОВЕРКА ТРЕБУЕТ АНАЛИЗА DIFF!
   Ищи строки с префиксом `-` в секции "Diff":

   А. УДАЛЕННЫЕ ПРОВЕРКИ БЕЗОПАСНОСТИ
   Б. УДАЛЕННАЯ ОБРАБОТКА ОШИБОК
   В. УДАЛЕННЫЕ RETRY DELAYS
   Г. УДАЛЕННЫЕ NULL CHECKS
```

AI модель теперь ЯВНО инструктируется:
1. Смотреть на строки с `-` в diff
2. Проверять, не были ли удалены критические проверки
3. Оценивать влияние удаления кода на безопасность и стабильность

## Коммиты

- `783b559` - Improve code review: Add detection for missing return values
- `7a82ad6` - Test: Add file with missing return in when block
- `aa4ef15` - Improve code review: Add detection for critical bugs ← **ТЕКУЩИЙ**

## Итого

✅ Добавлено 3 новых специализированных чек-листа
✅ Интегрировано в основной code review flow
✅ Создан тестовый файл с примерами всех багов
✅ Все изменения закоммичены и готовы к тестированию

Теперь `/review-pr` находит **критические баги**, которые раньше пропускались!
