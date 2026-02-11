# MCP Server Error Handling and Management Improvements

## Обзор изменений

В AI Agent добавлены улучшения для работы с MCP-серверами:

1. **Развёрнутые сообщения об ошибках** - при ошибках подключения показывается детальная информация
2. **Копирование ошибок** - возможность скопировать полное сообщение об ошибке в буфер обмена
3. **Редактирование серверов** - возможность изменять параметры уже добавленных MCP-серверов

## Что изменилось

### 1. Модель данных

#### `McpServer.kt`
Добавлено поле `errorMessage: String?` для хранения детальных сообщений об ошибках подключения.

```kotlin
data class McpServer(
    // ... existing fields
    val errorMessage: String? = null
)
```

### 2. База данных

#### `McpServer.sq`
- Добавлена колонка `errorMessage TEXT` в таблицу `McpServerEntity`
- Добавлен SQL-запрос `updateErrorMessage` для обновления сообщения об ошибке

### 3. Репозитории

#### `McpLocalRepository.kt` и `McpLocalRepositoryImpl.kt`
- Добавлен метод `updateErrorMessage(serverId: String, errorMessage: String?)`
- Обновлены методы работы с базой данных для поддержки нового поля

#### `McpRepositoryImpl.kt`
Улучшена функция `connectServer`:
- При старте подключения очищается предыдущая ошибка
- При успешном подключении ошибка очищается
- При ошибке сохраняется детальное сообщение:
  - Текст ошибки
  - Причина (cause)
  - Первые 5 строк stack trace для отладки

### 4. UI компоненты

#### `EditMcpServerDialog.kt` (новый файл)
Диалог для редактирования существующих MCP-серверов:
- Позволяет изменить имя сервера
- Позволяет изменить тип сервера (HTTP/STDIO)
- Позволяет изменить параметры подключения (URL, токен, команда, аргументы)
- Сохраняет изменения через `McpRepository.updateServer()`

#### `SettingsScreen.kt`
Обновлена карточка сервера `McpServerCard`:

**Новые функции:**
1. **Кнопка редактирования** - иконка Edit рядом с кнопкой Delete
2. **Секция ошибок** (показывается при `status == ERROR` и наличии `errorMessage`):
   - Заголовок с иконкой предупреждения
   - Раскрывающаяся панель с детальным текстом ошибки
   - Кнопка "Copy Error Message" для копирования в буфер обмена
   - Красная цветовая схема для визуального выделения

**Изменения в логике:**
- Добавлено состояние `serverToEdit` для отслеживания редактируемого сервера
- Добавлен callback `onEditServer` в `ExpandableMcpServersCard`
- При клике на Edit открывается `EditMcpServerDialog`

## Использование

### Просмотр ошибки подключения

1. Если сервер не смог подключиться (статус ERROR), в карточке появится секция "Connection Error"
2. Нажмите на секцию, чтобы развернуть детали ошибки
3. Прочитайте полное сообщение об ошибке с stack trace

### Копирование ошибки

1. Разверните секцию с ошибкой
2. Нажмите кнопку "Copy Error Message"
3. Сообщение об ошибке скопировано в буфер обмена

### Редактирование сервера

1. Откройте Settings → MCP Servers
2. Разверните список серверов
3. Найдите нужный сервер
4. Нажмите иконку "Edit" (карандаш)
5. Измените параметры сервера
6. Нажмите "Save"

**Примечание:** При изменении параметров подключения сервер будет переподключен автоматически.

## Примеры сообщений об ошибках

### Успешное подключение
```
Status: CONNECTED
(errorMessage: null)
```

### Сервер не запущен
```
Status: ERROR
errorMessage: "Connection error: Connection refused
Cause: java.net.ConnectException: Connection refused
  at java.net.PlainSocketImpl.socketConnect(Native Method)
  at java.net.AbstractPlainSocketImpl.doConnect(...)
  ..."
```

### Неверные параметры
```
Status: ERROR
errorMessage: "Failed to connect to MCP server. Please check server configuration and ensure the server is running."
```

## Технические детали

### Обработка ошибок в McpRepositoryImpl

```kotlin
override suspend fun connectServer(serverId: String): Result<Unit> {
    // Clear previous error
    mcpLocalRepository.updateErrorMessage(serverId, null)

    return try {
        val connected = mcpClientManager.connect(server)
        if (connected) {
            // Success - clear error
            mcpLocalRepository.updateErrorMessage(serverId, null)
            Result.success(Unit)
        } else {
            // Failed - save generic error
            val errorMsg = "Failed to connect to MCP server. ..."
            mcpLocalRepository.updateErrorMessage(serverId, errorMsg)
            Result.failure(Exception(errorMsg))
        }
    } catch (e: Exception) {
        // Exception - save detailed error with stack trace
        val detailedError = buildString {
            append("Connection error: ${e.message ?: "Unknown error"}")
            e.cause?.let { cause -> append("\nCause: ${cause.message}") }
            e.stackTraceToString().lines().take(5).forEach { line ->
                append("\n  $line")
            }
        }
        mcpLocalRepository.updateErrorMessage(serverId, detailedError)
        Result.failure(e)
    }
}
```

### UI для копирования ошибки

```kotlin
Button(
    onClick = {
        server.errorMessage?.let { error ->
            ClipboardManager.copyToClipboard(error)
        }
    },
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error
    )
) {
    Icon(Icons.Default.ContentCopy, contentDescription = null)
    Text("Copy Error Message")
}
```

## Совместимость

Изменения полностью обратно совместимы:
- Старые записи в базе данных будут иметь `errorMessage = null`
- Миграция базы данных происходит автоматически
- UI скрывает секцию ошибок, если `errorMessage` отсутствует

## Следующие шаги

Возможные улучшения:
- [ ] Добавить автоматическое переподключение с экспоненциальной задержкой
- [ ] Добавить уведомления о проблемах с подключением
- [ ] Добавить логирование ошибок в файл
- [ ] Добавить тестирование подключения перед сохранением
- [ ] Добавить валидацию URL и команд
