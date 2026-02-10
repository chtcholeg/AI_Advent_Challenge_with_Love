# 🚀 Быстрое тестирование команды /help

## Шаг 1: Проверка окружения

```bash
# Проверяем, что README.md существует
./test_readme_search.sh
```

**Ожидаемый результат:**
```
✅ Found: /path/to/.../day-21-developer-assistant/README.md
✅ Git root found: /path/to/AI_Advent_Challenge
📊 Size: 1419 lines
```

## Шаг 2: Запуск приложения

```bash
./gradlew :ai-agent:run
```

**Что произойдет:**
1. Gradle соберет проект
2. Запустится AI Agent Desktop приложение
3. В консоли увидите логи ProjectRootProvider:
   ```
   [ProjectRootProvider] Working directory: /path/to/...
   [ProjectRootProvider] Found Git root: /path/to/...
   [ProjectRootProvider] Checking level 0: ...
   [ProjectRootProvider] Found README.md at: ...
   ```

## Шаг 3: Тестирование команды /help

В открывшемся приложении:

1. **В поле ввода внизу** введите:
   ```
   /help
   ```

2. **Нажмите Enter** или кнопку отправки

3. **Проверьте результат:**
   - Команда `> /help` отображается синим цветом
   - Результат отображается **зеленым цветом**
   - Видны секции:
     ```
     📚 GigaChat AI Agent - Project Information
     ═══════════════════════════════════════════

     📖 Project Overview:
     A cross-platform chat application...

     ✨ Current Features:
     - Git MCP Server (Day 21)
     - RAG Reranking (Day 18)
     ...

     🆕 Day 21 Updates:
     Added Git MCP Server...

     🚀 Quick Start:
     ./gradlew :ai-agent:run

     💡 Available Commands:
       /help - Show this help information
     ```

## Шаг 4: Проверка логов

Вернитесь в консоль, где запускали приложение. Должны увидеть:

```
[ProjectRootProvider] Working directory: /Users/.../build/classes/kotlin/desktop/main
[ProjectRootProvider] Found Git root: /Users/.../AI_Advent_Challenge
[ProjectRootProvider] README.md not found in Git root, searching hierarchy...
[ProjectRootProvider] Checking level 0: .../main
[ProjectRootProvider] Checking level 1: .../desktop
...
[ProjectRootProvider] Found README.md at: .../day-21-developer-assistant/README.md
```

## Шаг 5: Тест несуществующей команды

В приложении введите:
```
/unknown
```

**Ожидаемый результат:**
- Ошибка **красным цветом**: `Unknown command: /unknown. Type /help for available commands.`

## Шаг 6: Проверка, что команды не идут в AI

1. Введите команду:
   ```
   /help
   ```

2. **Проверьте:** команда НЕ отправляется в AI модель (нет запроса к API)

3. Для сравнения введите обычное сообщение:
   ```
   Hello, AI!
   ```

4. **Проверьте:** обычное сообщение отправляется в AI (появляется "..." и затем ответ)

## ✅ Чеклист проверки

- [ ] Скрипт `test_readme_search.sh` нашел README.md
- [ ] Приложение запустилось без ошибок
- [ ] В логах видны шаги поиска ProjectRootProvider
- [ ] Команда `/help` отображается зеленым цветом
- [ ] Информация извлечена из реального README.md
- [ ] Все секции присутствуют (Overview, Features, Updates, Quick Start)
- [ ] Команда `/unknown` показывает ошибку красным
- [ ] Команды НЕ отправляются в AI
- [ ] Обычные сообщения работают как раньше

## 🐛 Возможные проблемы

### Проблема: README.md не найден
```
IllegalStateException: README.md not found in project directory hierarchy
```

**Решение:**
1. Проверьте, что файл существует: `ls -la README.md`
2. Проверьте working directory в логах
3. Убедитесь, что Git root определяется корректно

### Проблема: Пустой или некорректный контент

**Решение:**
1. Проверьте кодировку файла: `file README.md`
2. Проверьте содержимое: `head -20 README.md`
3. Убедитесь, что заголовки в Markdown формате (`# Header`)

### Проблема: Команда не распознается

**Решение:**
1. Убедитесь, что команда начинается с `/`
2. Проверьте, что нет пробелов перед `/`
3. Команда чувствительна к регистру? Нет, используется `lowercase()`

## 📝 Дополнительно

### Добавление новой команды

Хотите добавить `/status`?

1. Откройте `CommandHandler.kt`
2. Добавьте в `when`:
   ```kotlin
   "status" -> handleStatusCommand()
   ```
3. Реализуйте:
   ```kotlin
   private suspend fun handleStatusCommand(): CommandResult {
       val status = buildString {
           appendLine("📊 System Status")
           appendLine("✅ AI Agent: Running")
           appendLine("✅ MCP Servers: ${/* count */}")
           appendLine("✅ RAG Mode: ${/* on/off */}")
       }
       return CommandResult.Success(status)
   }
   ```
4. Пересоберите: `./gradlew :ai-agent:build`
5. Тестируйте: `/status`

## 🎯 Итог

Если все чеклисты пройдены - система команд работает идеально! 🎉

**Команда `/help` реально читает README.md из проекта и красиво отображает информацию!**
