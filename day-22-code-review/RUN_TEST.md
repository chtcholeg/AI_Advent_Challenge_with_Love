# 🧪 Инструкция для тестирования улучшений Code Review

## ✅ Что готово

1. ✅ Улучшения закоммичены в master
2. ✅ Создан тестовый файл `TestMissingReturn.kt` с известными ошибками
3. ✅ Проект успешно собран

## 🚀 Шаги для тестирования

### Шаг 1: Запустите AI Agent

```bash
cd /Users/shchepilov/AndroidStudioProjects/AI_Advent_Challenge_with_Love/day-22-code-review
./gradlew :ai-agent:run
```

⏳ Подождите, пока откроется окно AI Agent (~20-30 секунд)

### Шаг 2: Выполните команду review

В окне AI Agent введите:

```
/review-pr test-missing-return
```

Или (для локальных изменений):

```
/review-pr
```

### Шаг 3: Проверьте результат

## 📊 Ожидаемый результат

### ✅ Должно быть найдено:

#### Проблема #1: Missing return в when блоке
```
**Файл:** TestMissingReturn.kt:11-19
**Severity:** 🔴 Critical
**Проблема:** Missing return value in when block

**Текущий код:**
```kotlin
11 | val availableTools = when {
12 |     includeTools != null -> {
13 |         val filtered = allTools.filter { it in includeTools }
14 |         if (filtered.isEmpty()) {
15 |             println("WARNING")
16 |         }
17 |         // ⚠️ ПОСЛЕДНЯЯ СТРОКА - if БЕЗ else → вернет Unit!
18 |     }
19 |     else -> allTools
20 | }
```

**Как проявится:**
```
Type mismatch: inferred type is Unit but List<String> was expected
```

**Рекомендация:**
Добавить явный return:
```kotlin
filtered  // в конце блока
```
```

#### Проблема #2: Nullable chain без защиты
```
**Файл:** TestMissingReturn.kt:24-26
**Severity:** 🔴 Critical
**Проблема:** NPE: nullable chain без проверки

**Текущий код:**
```kotlin
24 | val user = findUser(userId)  // возвращает User?
25 | return user?.name.length.toString()  // ⚠️ NPE
```

**Как проявится:**
Если user == null или name == null:
```
kotlin.NullPointerException
  at TestMissingReturn.getUserName(TestMissingReturn.kt:25)
```

**Рекомендация:**
```kotlin
return user?.name?.length?.toString() ?: "0"
```
```

## 📈 Метрики успеха

- ✅ **2 из 2** известных ошибок найдены (100% recall)
- ✅ Обе с severity **Critical**
- ✅ Указаны **номера строк**
- ✅ Показано **как проявится**
- ✅ Даны **корректные рекомендации**

## ❌ Если что-то пошло не так

### Проблема: "Command not found: /review-pr"

**Решение:** Убедитесь, что Git MCP Server запущен:
```bash
cd mcp-servers
./START.sh
```

### Проблема: "No changes found"

**Решение:** Убедитесь, что находитесь на ветке test-missing-return:
```bash
git branch --show-current
# Должно показать: test-missing-return
```

### Проблема: Review не находит ошибки

**Проверьте в логах:**
1. Есть ли в секции "Detected technologies": **kotlin**
2. Присутствует ли в промпте: **"KOTLIN: MISSING RETURN IN WHEN/IF BLOCKS"**
3. Если нет - возможно, нужно пересобрать: `./gradlew :ai-agent:build --rerun-tasks`

## 🧹 После тестирования

Вернитесь на master и удалите тестовую ветку:

```bash
# Закройте AI Agent
git checkout master
git branch -D test-missing-return
rm TestMissingReturn.kt
```

## 🎯 Следующий тест: Реальный PR #5

После успешного теста на TestMissingReturn.kt, протестируйте на реальном PR:

```bash
git checkout master
./gradlew :ai-agent:run
# В AI Agent:
/review-pr 5
```

**Ожидание:** Должна быть найдена аналогичная ошибка в `AgentRepository.kt:310-320`

## 📞 Обратная связь

Если review находит:
- ✅ **2/2 ошибки** → Улучшения работают отлично! 🎉
- ⚠️ **1/2 ошибки** → Проверьте логи, возможно нужна настройка
- ❌ **0/2 ошибок** → Сообщите, посмотрим что не так

---

**Готово к запуску!** Просто выполните:
```bash
./gradlew :ai-agent:run
```
И введите `/review-pr` в окне приложения.
