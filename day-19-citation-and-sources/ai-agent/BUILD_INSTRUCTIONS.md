# Как собрать ai-agent с новыми кнопками копирования

## 🚨 Важно: Нужна пересборка!

После изменений в коде необходимо пересобрать проект, чтобы увидеть новые кнопки.

## 📱 Сборка для Android

### Вариант 1: Android Studio (рекомендуется)

1. **Откройте проект**:
   ```
   File → Open → day-15-environment
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
cd day-15-environment

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
cd day-15-environment

# Clean
./gradlew :ai-agent:clean

# Run Desktop
./gradlew :ai-agent:runDesktop
```

## 🔍 Проверка изменений

### После успешной сборки вы должны увидеть:

#### 1. Кнопки на сообщениях
```
> Ваше сообщение                           [📋]
                                            ↑
                                    Новая кнопка!
```

#### 2. Кнопка в toolbar
```
Было:    [🔄] [🗑️] [⚙️]
Стало:   [🔄] [📋] [🗑️] [⚙️]
               ↑
         Новая кнопка "Copy All"!
```

## 🐛 Troubleshooting

### Проблема: Кнопок всё ещё нет

**Решение 1: Полная пересборка**
```bash
cd day-15-environment

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

**Решение 2: Убедитесь, что запущена правильная версия**
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

**Проверьте imports в MessageItem.kt**:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import ru.chtcholeg.agent.util.ClipboardManager
```

**Проверьте imports в AgentScreen.kt**:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
```

### Проблема: Gradle sync failed

**Решение**:
```bash
# Очистите Gradle кэш
rm -rf ~/.gradle/caches

# Обновите Gradle wrapper
cd day-15-environment
./gradlew wrapper --gradle-version=8.2

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

### 3. Отправьте сообщение
```
Напишите: "Hello"
Нажмите Enter
```

### 4. Проверьте UI
```
✅ Справа от сообщения есть маленькая иконка 📋
✅ В toolbar есть зеленая иконка 📋 (между Reload и Clear)
✅ Нажатие на иконку копирует текст
✅ Можно вставить в текстовый редактор (Ctrl+V)
```

## 📊 Версии

### Убедитесь в правильных версиях:

```kotlin
// build.gradle.kts
kotlin = "1.9.20" или новее
compose = "1.5.10" или новее
```

### JDK
```bash
java -version
# Должен быть JDK 17 или новее
```

## 🚀 Quick Fix

**Самый быстрый способ увидеть изменения**:

```bash
cd day-15-environment

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
cd day-15-environment
./gradlew clean
./gradlew :ai-agent:runDesktop
```

## 📝 Проверочный список

Перед сборкой убедитесь:

- [ ] Файлы MessageItem.kt и AgentScreen.kt изменены
- [ ] Imports добавлены (ContentCopy)
- [ ] Gradle sync выполнен
- [ ] Clean build сделан
- [ ] Старая версия приложения остановлена
- [ ] Установлена новая версия
- [ ] Приложение запущено заново

## 💡 Совет

**Если всё ещё не видите кнопки:**

1. Проверьте, что редактируете правильные файлы:
   ```bash
   # Должен быть ai-agent, НЕ composeApp
   ls -la ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/presentation/
   ```

2. Проверьте изменения:
   ```bash
   grep "ContentCopy" ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/presentation/components/MessageItem.kt
   # Должны быть 2 совпадения

   grep "Copy all" ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/presentation/agent/AgentScreen.kt
   # Должно быть 1 совпадение
   ```

3. Убедитесь, что запущена свежая версия:
   ```bash
   # Проверьте время установки
   adb shell dumpsys package ru.chtcholeg.agent | grep firstInstall
   # Должно быть недавнее время
   ```

---

## ✅ После успешной сборки

Вы увидите:
- 📋 Маленькая кнопка справа от каждого сообщения
- 📋 Зеленая кнопка в toolbar (когда есть сообщения)
- Клик копирует текст в буфер обмена
- Можно вставить куда угодно

**Готово! Наслаждайтесь новой функцией!** 🎉
