# Проблемы с авторизацией

## Почему не работает авторизация?

### Проблема: "Invalid credentials" или "Unauthorized"

**Причины:**
1. Неверные или устаревшие креденшелы GigaChat API
2. Отсутствует файл `local.properties`
3. Истек срок действия токена

**Решение:**

1. **Проверьте креденшелы в `local.properties`:**
   ```properties
   gigachat.clientId=YOUR_CLIENT_ID
   gigachat.clientSecret=YOUR_CLIENT_SECRET
   ```

2. **Или используйте переменные окружения:**
   ```bash
   export GIGACHAT_CLIENT_ID="ваш_client_id"
   export GIGACHAT_CLIENT_SECRET="ваш_client_secret"
   ```

3. **Пересоберите проект после изменения креденшелов:**
   ```bash
   ./gradlew clean build
   ```

4. **Проверьте, что креденшелы валидны:**
   - Зайдите на сайт GigaChat API
   - Убедитесь, что токен не истек
   - При необходимости создайте новый токен

### Проблема: "Token expired"

**Решение:**
Приложение автоматически обновляет токен при использовании OAuth 2.0. Если проблема сохраняется:

1. Удалите кеш приложения
2. Пересоздайте креденшелы в личном кабинете GigaChat
3. Обновите `local.properties`
4. Выполните `./gradlew clean build`

## Как получить креденшелы GigaChat API?

1. Зарегистрируйтесь на платформе GigaChat
2. Перейдите в раздел "API Keys" / "Ключи API"
3. Создайте новое приложение
4. Скопируйте `clientId` и `clientSecret`
5. Добавьте их в `local.properties` или переменные окружения

## Проблема с Hugging Face API Token

**Симптомы:** Ошибки при использовании моделей Hugging Face

**Решение:**
1. Получите токен на https://huggingface.co/settings/tokens
2. Добавьте в `local.properties`:
   ```properties
   huggingface.apiToken=YOUR_TOKEN
   ```
3. Пересоберите проект: `./gradlew clean build`
