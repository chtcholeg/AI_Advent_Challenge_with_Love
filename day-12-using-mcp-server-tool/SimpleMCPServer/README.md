# MCP Server

MCP (Model Context Protocol) сервер на Kotlin/Ktor с поддержкой плагинов и SSE-транспортом.

## Последние изменения (v1.1)

### Улучшения стабильности SSE
- Исправлена обработка отключений клиентов (ChannelWriteException)
- Корректная отмена корутин при закрытии соединения
- Чистые логи без спама ERROR для нормальных disconnection
- Добавлена обработка CancellationException, IOException, ClosedReceiveChannelException

## Возможности

- SSE (Server-Sent Events) транспорт для MCP
- Аутентификация через API-ключ
- Rate limiting
- Плагинная архитектура
- Встроенный Weather Tool (Open-Meteo API)

## Требования

- JDK 17+
- Gradle 8.x

## Быстрый старт

### Локальный запуск

#### Стандартный режим (с аутентификацией)

1. Установите API-ключ:
```bash
export MCP_API_KEY="your-secret-api-key-here"
```

2. Соберите и запустите:
```bash
./gradlew run
```

#### Режим без аутентификации (для разработки/тестирования)

⚠️ **ВНИМАНИЕ:** Используйте только для локальной разработки и тестирования!

```bash
./gradlew run --args="--no-auth"
```

Или с собранным JAR:
```bash
java -jar build/libs/mcp-server-all.jar --no-auth
```

В этом режиме сервер не требует API-ключ для доступа к защищённым эндпоинтам.

Сервер запустится на `http://localhost:8081`

### Сборка JAR

```bash
./gradlew shadowJar
```

Результат: `build/libs/mcp-server-all.jar`

### Запуск JAR

```bash
java -Xmx256m -jar build/libs/mcp-server-all.jar
```

## Конфигурация

### Аргументы командной строки

| Аргумент | Описание |
|----------|----------|
| `--no-auth` или `--disable-auth` | Отключить аутентификацию (не требует API-ключ) |

### Environment Variables

| Переменная | Описание | По умолчанию |
|------------|----------|--------------|
| `MCP_API_KEY` | API-ключ для аутентификации | (обязательно, если не используется `--no-auth`) |
| `SERVER_HOST` | Хост для прослушивания | `127.0.0.1` |
| `SERVER_PORT` | Порт сервера | `8081` |
| `PLUGINS_DIR` | Директория плагинов | `./plugins` |
| `RATE_LIMIT_RPM` | Лимит запросов в минуту | `100` |

### application.conf

Файл `src/main/resources/application.conf` содержит настройки по умолчанию.
Environment variables имеют приоритет над значениями в файле.

## API Endpoints

### Публичные

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/` | Информация о сервере |
| GET | `/health` | Health check |

### Защищённые (требуют `Authorization: Bearer <API_KEY>`)

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/sse` | SSE-соединение для MCP |
| POST | `/message?sessionId=...` | Отправка MCP-сообщений |
| GET | `/tools` | Список доступных tools |

## Использование с MCP-клиентом

### Health Check

```bash
curl http://localhost:8081/health
```

### Список tools

С аутентификацией:
```bash
curl -H "Authorization: Bearer YOUR_API_KEY" http://localhost:8081/tools
```

Без аутентификации (если запущено с `--no-auth`):
```bash
curl http://localhost:8081/tools
```

### MCP через SSE

1. Установить SSE-соединение:

С аутентификацией:
```bash
curl -N -H "Authorization: Bearer YOUR_API_KEY" http://localhost:8081/sse
```

Без аутентификации (если запущено с `--no-auth`):
```bash
curl -N http://localhost:8081/sse
```

2. Отправить сообщение (в другом терминале):

С аутентификацией:
```bash
curl -X POST \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' \
  "http://localhost:8081/message?sessionId=SESSION_ID_FROM_SSE"
```

Без аутентификации (если запущено с `--no-auth`):
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' \
  "http://localhost:8081/message?sessionId=SESSION_ID_FROM_SSE"
```

## Weather Tool

Встроенный tool для получения погоды через Open-Meteo API.

### Параметры

| Параметр | Тип | Описание |
|----------|-----|----------|
| `city` | string | Название города |
| `latitude` | number | Широта |
| `longitude` | number | Долгота |
| `type` | string | `current`, `forecast`, `historical`, `all` |
| `forecast_days` | integer | Дни прогноза (1-16) |
| `start_date` | string | Начало периода (YYYY-MM-DD) |
| `end_date` | string | Конец периода (YYYY-MM-DD) |

### Примеры

Текущая погода в Москве:
```json
{
  "name": "get_weather",
  "arguments": {
    "city": "Moscow",
    "type": "current"
  }
}
```

7-дневный прогноз по координатам:
```json
{
  "name": "get_weather",
  "arguments": {
    "latitude": 55.75,
    "longitude": 37.62,
    "type": "forecast",
    "forecast_days": 7
  }
}
```

## Плагины

Плагины загружаются из директории `./plugins` при старте сервера.

### Создание плагина

1. Создайте Kotlin-проект с зависимостью на API:
```kotlin
dependencies {
    compileOnly("com.example.mcp:mcp-server-api:1.0.0")
}
```

2. Реализуйте интерфейс `McpPlugin`:
```kotlin
class MyPlugin : McpPlugin {
    override val name = "my-plugin"
    override val version = "1.0.0"
    override val description = "My custom plugin"

    override fun getTools(): List<McpTool> = listOf(MyTool())
}
```

3. Зарегистрируйте плагин в `META-INF/services/com.example.mcp.plugins.McpPlugin`

4. Соберите JAR и поместите в директорию `plugins/`

## Структура проекта

```
mcp-server/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/kotlin/com/example/mcp/
│   ├── Application.kt          # Entry point
│   ├── auth/
│   │   └── ApiKeyAuth.kt       # Authentication
│   ├── config/
│   │   └── Config.kt           # Configuration
│   ├── mcp/
│   │   ├── McpProtocol.kt      # MCP protocol
│   │   ├── SseTransport.kt     # SSE transport
│   │   └── ToolRegistry.kt     # Tool management
│   ├── plugins/
│   │   ├── PluginApi.kt        # Plugin interfaces
│   │   └── PluginLoader.kt     # Plugin loader
│   └── tools/weather/
│       ├── OpenMeteoClient.kt  # Open-Meteo client
│       └── WeatherTool.kt      # Weather tool
├── src/main/resources/
│   ├── application.conf        # Configuration
│   └── logback.xml             # Logging
└── plugins/                    # Plugin JARs
```

## Лицензия

MIT
