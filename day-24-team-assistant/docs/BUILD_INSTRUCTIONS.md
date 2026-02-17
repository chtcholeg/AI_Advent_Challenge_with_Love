# Как собрать и запустить AI Agent

## Важно: Нужна пересборка!

После изменений в коде необходимо пересобрать проект.

## 📱 Сборка для Android

### Вариант 1: Android Studio (рекомендуется)

1. **Откройте проект**:
   ```
   File → Open → day-21-developer-assistant
   ```

2. **Gradle Sync**:
   ```
   File → Sync Project with Gradle Files
   ```
   Или нажмите кнопку "Sync Now" если появится

3. **Clean Build** (если предыдущая версия запущена):
   ```
   Build → Clean Project
   ```
   Подождите завершения

4. **Rebuild**:
   ```
   Build → Rebuild Project
   ```
   Подождите завершения (может занять 1-2 минуты)

5. **Запустите**:
   ```
   Run → Run 'ai-agent' (или Shift+F10)
   ```

### Вариант 2: Командная строка

```bash
cd day-21-developer-assistant

# Clean
./gradlew :ai-agent:clean

# Build
./gradlew :ai-agent:assembleDebug

# Install
./gradlew :ai-agent:installDebug

# Run
adb shell am start -n ru.chtcholeg.agent/.MainActivity
```

## 🖥️ Сборка для Desktop

### Вариант 1: IntelliJ IDEA

1. **Откройте проект**
2. **Gradle Sync**: Кнопка "Sync" в правом верхнем углу
3. **Clean**:
   ```
   Build → Clean Project
   ```
4. **Run**:
   ```
   Run → Run 'ai-agent desktop'
   ```

### Вариант 2: Командная строка

```bash
cd day-21-developer-assistant

# Clean
./gradlew :ai-agent:clean

# Run Desktop
./gradlew :ai-agent:run
```

## Проверка после сборки

### После успешной сборки вы должны увидеть:

#### 1. Главный экран с полем ввода и кнопками
#### 2. Toolbar с кнопками: Reload, Copy All, Clear, Settings
#### 3. Команда `/help` показывает информацию о проекте

## 🐛 Troubleshooting

### Проблема: Изменения не отображаются

**Решение 1: Полная пересборка**
```bash
cd day-21-developer-assistant

# Удалить build кэш
rm -rf ai-agent/build
rm -rf build

# Gradle clean
./gradlew clean

# Build заново
./gradlew :ai-agent:assembleDebug

# Переустановить
./gradlew :ai-agent:installDebug
```

**Решение 2: Переустановка на Android**
```bash
# Остановите все запущенные экземпляры
adb shell am force-stop ru.chtcholeg.agent

# Переустановите
./gradlew :ai-agent:installDebug

# Запустите заново
adb shell am start -n ru.chtcholeg.agent/.MainActivity
```

**Решение 3: Проверьте модуль**
```bash
# Убедитесь, что собираете ai-agent, а не composeApp
./gradlew :ai-agent:tasks --all | grep "run"

# Должны быть:
# runDesktop
# installDebug
```

### Проблема: Ошибка компиляции

**Проверьте:**
- Все модули подключены в `settings.gradle.kts`
- Gradle sync выполнен без ошибок
- JDK 17+ установлен: `java -version`

### Проблема: Gradle sync failed

**Решение**:
```bash
# Очистите Gradle кэш
rm -rf ~/.gradle/caches

# Обновите Gradle wrapper
cd day-21-developer-assistant
./gradlew wrapper --gradle-version=8.10

# Sync заново
./gradlew clean build
```

## ✅ Проверка успешной сборки

После сборки выполните:

### 1. Проверьте APK (Android)
```bash
ls -lh ai-agent/build/outputs/apk/debug/
# Должен быть файл ai-agent-debug.apk
# Проверьте дату - должна быть свежая
```

### 2. Запустите приложение
- Android: Открывается app
- Desktop: Открывается окно

### 3. Проверьте команду /help
```
В поле ввода введите: /help
Нажмите Enter
```

### 4. Проверьте UI
```
✅ Появилась информация о проекте (зеленым цветом)
✅ Toolbar содержит кнопки: Reload, Copy All, Clear, Settings
✅ Отправка сообщения AI работает
✅ Настройки доступны через кнопку Settings
```

## 📊 Версии

### Убедитесь в правильных версиях:

```kotlin
// gradle/libs.versions.toml
kotlin = "2.1.0"
compose = "1.7.3"
```

### JDK
```bash
java -version
# Должен быть JDK 17 или новее
```

## 🚀 Quick Fix

**Самый быстрый способ увидеть изменения**:

```bash
cd day-21-developer-assistant

# 1. Полная очистка
./gradlew clean
rm -rf ai-agent/build

# 2. Сборка
./gradlew :ai-agent:assembleDebug

# 3. Установка
./gradlew :ai-agent:installDebug

# 4. Запуск
adb shell am start -n ru.chtcholeg.agent/.MainActivity
```

**Для Desktop**:
```bash
cd day-21-developer-assistant
./gradlew clean
./gradlew :ai-agent:run
```

## Проверочный список

Перед сборкой убедитесь:

- [ ] `local.properties` содержит credentials (gigachat.clientId, gigachat.clientSecret)
- [ ] Gradle sync выполнен
- [ ] Clean build сделан
- [ ] Старая версия приложения остановлена
- [ ] Установлена новая версия
- [ ] Приложение запущено заново

## Совет

**Проверьте, что собираете правильный модуль:**
```bash
# Должен быть ai-agent, НЕ chat
./gradlew :ai-agent:tasks --all | grep "run"
```

---

## После успешной сборки

Доступные возможности:
- Команда `/help` показывает информацию о проекте
- Отправка сообщений в GigaChat AI
- Кнопки копирования на каждом сообщении
- Настройки AI параметров (temperature, topP, maxTokens)
- Подключение MCP серверов (Git, Weather, etc.)
- RAG с семантическим поиском по документам
