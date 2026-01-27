# MCP Server Specification

## Overview

MCP-сервер для командного использования, развёрнутый на VPS с Ubuntu. Предоставляет API-интеграции внешним клиентам через MCP-протокол (Server-Sent Events).

**Цель:** Качественный, расширяемый MCP-сервер с плагинной архитектурой.

---

## Функциональные требования

### Tools

#### 1. Weather Tool (Open-Meteo API)

**Функциональность:**
- Текущая погода (температура, ветер, осадки, влажность)
- Прогноз на несколько дней
- Исторические данные о погоде

**Параметры входа:**
- `city` (string, optional) — название города для геокодинга
- `latitude` (number, optional) — широта
- `longitude` (number, optional) — долгота
- `forecast_days` (number, optional, default: 7) — количество дней прогноза
- `historical_date_start` (string, optional) — начало периода для исторических данных
- `historical_date_end` (string, optional) — конец периода

**Поведение:**
- Если указан `city`, выполняется геокодинг через Open-Meteo Geocoding API
- Если указаны координаты, используются напрямую
- При недоступности Open-Meteo API возвращается ошибка (без fallback)

---

## Архитектура

### Стек технологий

| Компонент | Технология |
|-----------|------------|
| Язык | Kotlin |
| Фреймворк | Ktor |
| JDK | 17 LTS |
| Система сборки | Gradle (Kotlin DSL) |
| Reverse proxy | Caddy |
| TLS | Let's Encrypt (автоматически через Caddy) |
| Process manager | systemd |

### MCP Transport

**Server-Sent Events (SSE)** — выбран за простоту, совместимость с HTTP/HTTPS, лёгкость прохождения через прокси и firewall.

Endpoint: `POST /sse` для инициализации, `GET /sse` для event stream.

### Плагинная система

**MVP:** Плагины как отдельные JAR-файлы в директории `/plugins`. Загрузка при старте сервера (без hot reload в MVP).

**Интерфейс плагина:**
```kotlin
interface McpPlugin {
    val name: String
    val description: String
    fun getTools(): List<McpTool>
}

interface McpTool {
    val name: String
    val description: String
    val inputSchema: JsonSchema
    suspend fun execute(arguments: JsonObject): ToolResult
}
```

**Расширение:** Hot reload плагинов запланирован на будущие версии.

---

## Безопасность

### Аутентификация

**API-ключ (Bearer token):**
- Генерируется случайный токен 32+ символов
- Передаётся в заголовке: `Authorization: Bearer <token>`
- Хранится в переменной окружения `MCP_API_KEY`
- Один ключ на всю команду

**Реализация (Ktor):**
```kotlin
install(Authentication) {
    bearer("api-key") {
        authenticate { tokenCredential ->
            if (tokenCredential.token == config.apiKey) {
                UserIdPrincipal("client")
            } else null
        }
    }
}
```

### TLS

- Терминация на Caddy
- Автоматическое получение и обновление сертификатов Let's Encrypt
- Требуется доменное имя (пользователь планирует приобрести)

### Rate Limiting

- Базовый лимит: 100 запросов/минуту
- Глобальный (не per-user)
- Реализация через Ktor-плагин или middleware

---

## Инфраструктура

### VPS

| Параметр | Значение |
|----------|----------|
| ОС | Ubuntu |
| CPU | 1 vCPU |
| RAM | 512MB - 1GB |
| JVM настройки | `-Xmx256m` или аналогичный тюнинг |

### Конфигурация

**Комбинированный подход:**

Секреты (environment variables):
- `MCP_API_KEY` — токен аутентификации

Остальное (application.conf):
```hocon
server {
    port = 8080
    host = "127.0.0.1"
}

rateLimit {
    requestsPerMinute = 100
}

plugins {
    directory = "/opt/mcp-server/plugins"
}
```

### Caddy конфигурация

```caddyfile
mcp.yourdomain.com {
    reverse_proxy localhost:8080
}
```

### systemd unit

```ini
[Unit]
Description=MCP Server
After=network.target

[Service]
Type=simple
User=mcp
WorkingDirectory=/opt/mcp-server
ExecStart=/usr/bin/java -Xmx256m -jar mcp-server.jar
Restart=always
RestartSec=10
EnvironmentFile=/opt/mcp-server/.env

[Install]
WantedBy=multi-user.target
```

---

## Развёртывание

### Процесс деплоя (вручную)

1. Сборка: `./gradlew shadowJar`
2. Копирование: `scp build/libs/mcp-server-all.jar user@server:/opt/mcp-server/`
3. Рестарт: `ssh user@server 'sudo systemctl restart mcp-server'`

### Зависимости на сервере

- JDK 17 (`apt install openjdk-17-jre-headless`)
- Caddy (`apt install caddy`)

---

## Мониторинг

### Health endpoint

`GET /health` — возвращает `200 OK` с JSON:
```json
{
    "status": "ok",
    "uptime": 12345,
    "plugins_loaded": 1
}
```

### Внешний мониторинг

**UptimeRobot:**
- Проверка `/health` каждые 5 минут
- Уведомления при недоступности

### Логирование

- Минимальный уровень: stdout/stderr
- Формат: текстовый (не JSON)
- Просмотр: `journalctl -u mcp-server -f`

---

## Клиенты

MCP-сервер будет использоваться **собственным приложением** (пока в разработке). Сервер должен быть универсальным и совместимым со стандартом MCP.

**Ожидаемая нагрузка:** 1-3 одновременных пользователя.

---

## Ограничения и компромиссы

### Принятые ограничения

| Ограничение | Причина |
|-------------|---------|
| JVM на малых ресурсах | Тюнинг -Xmx, мониторинг потребления |
| Один API-ключ | Достаточно для маленькой команды |
| Нет hot reload плагинов | Упрощает MVP, добавим позже |
| Нет тестов | Ручное тестирование на MVP |
| Нет fallback для Open-Meteo | Возврат ошибки при недоступности |

### Будущие улучшения

- Hot reload плагинов
- Per-user API ключи
- Structured logging (JSON)
- CI/CD pipeline
- Автоматические тесты

---

## Структура проекта

```
mcp-server/
├── build.gradle.kts
├── settings.gradle.kts
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── com/example/mcp/
│       │       ├── Application.kt          # Entry point
│       │       ├── config/
│       │       │   └── Config.kt           # Configuration loading
│       │       ├── auth/
│       │       │   └── ApiKeyAuth.kt       # Authentication
│       │       ├── mcp/
│       │       │   ├── McpProtocol.kt      # MCP protocol implementation
│       │       │   ├── SseTransport.kt     # SSE transport
│       │       │   └── ToolRegistry.kt     # Tool management
│       │       ├── plugins/
│       │       │   ├── PluginLoader.kt     # Plugin loading
│       │       │   └── PluginApi.kt        # Plugin interfaces
│       │       └── tools/
│       │           └── weather/
│       │               ├── WeatherTool.kt  # Weather tool implementation
│       │               └── OpenMeteoClient.kt
│       └── resources/
│           └── application.conf
├── plugins/                                 # Plugin JARs directory
└── README.md
```

---

## Зависимости (Gradle)

```kotlin
dependencies {
    // Ktor
    implementation("io.ktor:ktor-server-core:2.3.x")
    implementation("io.ktor:ktor-server-netty:2.3.x")
    implementation("io.ktor:ktor-server-auth:2.3.x")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.x")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.x")

    // HTTP client для Open-Meteo
    implementation("io.ktor:ktor-client-core:2.3.x")
    implementation("io.ktor:ktor-client-cio:2.3.x")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.x")

    // Configuration
    implementation("com.typesafe:config:1.4.x")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.x")
}
```

---

## Checklist перед запуском

- [ ] Купить домен и настроить DNS A-запись на IP VPS
- [ ] Установить JDK 17 на VPS
- [ ] Установить и настроить Caddy
- [ ] Создать systemd unit
- [ ] Сгенерировать API-ключ
- [ ] Настроить firewall (открыть 80, 443)
- [ ] Зарегистрироваться в UptimeRobot
- [ ] Задеплоить и протестировать

---

*Документ сгенерирован на основе интервью. Версия: 1.0*
