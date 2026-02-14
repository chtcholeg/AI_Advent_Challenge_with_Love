# Установка и запуск

## Как установить приложение?

### Системные требования

**Для Desktop версии:**
- JDK 17 или выше
- 4 GB RAM минимум
- Windows/macOS/Linux

**Для Android версии:**
- Android 7.0 (API 24) или выше
- 2 GB RAM минимум

### Установка

1. **Клонируйте репозиторий:**
   ```bash
   git clone <repository-url>
   cd day-23-user-support-assistant
   ```

2. **Настройте креденшелы:**
   Создайте файл `local.properties` в корне проекта:
   ```properties
   gigachat.clientId=YOUR_CLIENT_ID
   gigachat.clientSecret=YOUR_CLIENT_SECRET
   huggingface.apiToken=YOUR_HUGGING_FACE_TOKEN
   ```

3. **Соберите проект:**
   ```bash
   ./gradlew build
   ```

## Как запустить приложение?

### Desktop Chat
```bash
./gradlew :chat:run
```

### Android Chat
```bash
./gradlew :chat:installDebug
```
Приложение установится на подключенное устройство или эмулятор.

### AI Agent (с поддержкой RAG)
```bash
./gradlew :ai-agent:run
```

### Document Indexer
```bash
./gradlew :indexer:run
```
Требует установленный Ollama для локальных эмбеддингов.

## Проблемы при установке

### Ошибка: "SDK not found"

**Решение:**
1. Установите Android SDK через Android Studio
2. Настройте переменную окружения:
   ```bash
   export ANDROID_HOME=/path/to/android/sdk
   ```

### Ошибка: "Java version incompatible"

**Решение:**
1. Проверьте версию Java: `java -version`
2. Установите JDK 17 или выше
3. Настройте `JAVA_HOME`:
   ```bash
   export JAVA_HOME=/path/to/jdk-17
   ```

### Ошибка: "Build failed - credentials not found"

**Решение:**
1. Убедитесь, что `local.properties` существует в корне проекта
2. Проверьте формат файла (без лишних пробелов)
3. Или используйте переменные окружения:
   ```bash
   export GIGACHAT_CLIENT_ID="your_id"
   export GIGACHAT_CLIENT_SECRET="your_secret"
   ./gradlew build
   ```

### Ошибка сборки после обновления креденшелов

**Решение:**
```bash
./gradlew clean build
```
Это очистит кеш и пересоберет проект с новыми креденшелами.
