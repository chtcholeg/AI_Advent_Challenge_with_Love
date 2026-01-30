# Тестирование MCP Серверов с Аутентификацией

Это руководство показывает, как проверить работоспособность MCP серверов, когда установлен API ключ (`MCP_API_KEY`).

## Содержание

- [Запуск сервера с API ключом](#запуск-сервера-с-api-ключом)
- [Проверка через curl](#проверка-через-curl)
- [Проверка через Python](#проверка-через-python)
- [Проверка SSE соединения](#проверка-sse-соединения)
- [Устранение неполадок](#устранение-неполадок)

## Запуск сервера с API ключом

### Через переменную окружения

```bash
# Установить API ключ
export MCP_API_KEY="your-secret-api-key-here"

# Запустить сервер (аутентификация включена автоматически)
python launcher.py telegram

# Или отдельный сервер
python -m telegram.main
```

### Проверка, что ключ установлен

```bash
# В терминале, где запущен сервер, вы увидите:
# INFO: Telegram MCP Server v1.0.0
#       Address: 0.0.0.0:8001
#       Auth: enabled          ← Аутентификация включена
```

## Проверка через curl

### 1. Health Endpoint (без авторизации)

Health endpoint всегда доступен без API ключа:

```bash
curl http://localhost:8001/health
```

**Ожидаемый ответ:**
```json
{
  "status": "ok",
  "tools_count": 3,
  "active_sessions": 0,
  "auth_enabled": true
}
```

### 2. Root Endpoint (без авторизации)

```bash
curl http://localhost:8001/
```

**Ожидаемый ответ:**
```json
{
  "name": "telegram-mcp-server",
  "version": "1.0.0",
  "protocol": "MCP 2024-11-05",
  "endpoints": {
    "sse": "/sse (requires X-API-Key)",
    "message": "/message (requires X-API-Key)",
    "health": "/health (public)",
    "tools": "/tools (requires X-API-Key)",
    "docs": "/docs (public)"
  }
}
```

### 3. Tools Endpoint (с API ключом)

#### Вариант А: API ключ в заголовке (рекомендуется)

```bash
curl -H "X-API-Key: your-secret-api-key-here" \
     http://localhost:8001/tools
```

#### Вариант Б: API ключ в query параметре

```bash
curl "http://localhost:8001/tools?api_key=your-secret-api-key-here"
```

**Ожидаемый ответ:**
```json
{
  "tools": [
    {
      "name": "get_channel_messages",
      "description": "Get recent messages from a Telegram channel",
      "required_params": ["channel"]
    }
  ]
}
```

### 4. Проверка ошибки авторизации

Попробуйте без ключа:

```bash
curl http://localhost:8001/tools
```

**Ожидаемый ответ (401 Unauthorized):**
```json
{
  "error": "Unauthorized: missing or invalid X-API-Key header"
}
```

## Проверка через Python

### Простая проверка с requests

```python
import requests

API_KEY = "your-secret-api-key-here"
BASE_URL = "http://localhost:8001"

# 1. Проверить health (без ключа)
response = requests.get(f"{BASE_URL}/health")
print(f"Health: {response.json()}")

# 2. Получить список инструментов (с ключом в заголовке)
headers = {"X-API-Key": API_KEY}
response = requests.get(f"{BASE_URL}/tools", headers=headers)
print(f"Tools: {response.json()}")

# 3. Альтернатива - ключ в параметрах
response = requests.get(f"{BASE_URL}/tools", params={"api_key": API_KEY})
print(f"Tools (query): {response.json()}")
```

### Полная проверка MCP протокола

```python
import requests
import json
import time

API_KEY = "your-secret-api-key-here"
BASE_URL = "http://localhost:8001"
headers = {"X-API-Key": API_KEY}

# 1. Подключиться к SSE endpoint для получения session ID
print("1. Connecting to SSE endpoint...")
sse_response = requests.get(f"{BASE_URL}/sse", headers=headers, stream=True)

session_id = None
for line in sse_response.iter_lines():
    if line:
        decoded = line.decode('utf-8')
        if decoded.startswith('data:'):
            data = decoded[5:].strip()
            if '/message?sessionId=' in data:
                session_id = data.split('sessionId=')[1]
                print(f"✅ Got session ID: {session_id}")
                break

if not session_id:
    print("❌ Failed to get session ID")
    exit(1)

# 2. Отправить initialize запрос
print("\n2. Sending initialize request...")
initialize_request = {
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
        "protocolVersion": "2024-11-05",
        "clientInfo": {"name": "test-client", "version": "1.0.0"}
    }
}

response = requests.post(
    f"{BASE_URL}/message?sessionId={session_id}",
    headers={**headers, "Content-Type": "application/json"},
    json=initialize_request
)

print(f"Initialize response: {response.json()}")

# 3. Отправить tools/list запрос
print("\n3. Requesting tools list...")
tools_request = {
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
}

response = requests.post(
    f"{BASE_URL}/message?sessionId={session_id}",
    headers={**headers, "Content-Type": "application/json"},
    json=tools_request
)

tools = response.json()
print(f"Available tools: {len(tools.get('result', {}).get('tools', []))}")
for tool in tools.get('result', {}).get('tools', []):
    print(f"  - {tool['name']}: {tool['description']}")

print("\n✅ All tests passed!")
```

## Проверка SSE соединения

### Bash скрипт для проверки SSE

Создайте файл `test_sse_with_auth.sh`:

```bash
#!/bin/bash

# Конфигурация
API_KEY="your-secret-api-key-here"
SERVER=${1:-telegram}
declare -A SERVER_PORTS=(
    ["github"]=8000
    ["telegram"]=8001
    ["weather"]=8002
    ["timeservice"]=8003
    ["currency"]=8004
    ["fileops"]=8005
    ["docker"]=8006
    ["adb"]=8007
)

PORT=${SERVER_PORTS[$SERVER]}
BASE_URL="http://localhost:${PORT}"

echo "=== Testing MCP Server with Authentication ==="
echo "Server: $SERVER (port $PORT)"
echo "API Key: ${API_KEY:0:10}..."
echo ""

# 1. Test health endpoint (public)
echo "1. Testing health endpoint (no auth required)..."
curl -s "$BASE_URL/health" | python3 -m json.tool
echo ""

# 2. Test root endpoint (public)
echo "2. Testing root endpoint (no auth required)..."
curl -s "$BASE_URL/" | python3 -m json.tool
echo ""

# 3. Test tools endpoint with auth header
echo "3. Testing tools endpoint (with X-API-Key header)..."
curl -s -H "X-API-Key: $API_KEY" "$BASE_URL/tools" | python3 -m json.tool
echo ""

# 4. Test SSE connection with auth
echo "4. Testing SSE connection (with auth)..."
SESSION_ID=$(curl -s -N -H "X-API-Key: $API_KEY" "$BASE_URL/sse" | \
    grep "data: /message" | \
    sed 's/.*sessionId=\(.*\)/\1/' | \
    head -n 1)

if [ -z "$SESSION_ID" ]; then
    echo "❌ Failed to get session ID"
    exit 1
fi

echo "✅ Got session ID: $SESSION_ID"

# 5. Test initialize request
echo ""
echo "5. Testing initialize request..."
INIT_REQUEST='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"test-client","version":"1.0.0"}}}'

curl -s -X POST \
    -H "X-API-Key: $API_KEY" \
    -H "Content-Type: application/json" \
    -d "$INIT_REQUEST" \
    "$BASE_URL/message?sessionId=$SESSION_ID" | python3 -m json.tool

echo ""
echo "=== All tests passed! ==="
```

### Использование скрипта

```bash
# Сделать исполняемым
chmod +x test_sse_with_auth.sh

# Запустить для telegram сервера
./test_sse_with_auth.sh telegram

# Запустить для других серверов
./test_sse_with_auth.sh github
./test_sse_with_auth.sh weather
./test_sse_with_auth.sh docker
./test_sse_with_auth.sh adb
```

## Проверка в Kotlin приложении

### Пример конфигурации MCP сервера в приложении

```kotlin
// В Settings → MCP Servers → Add Server
McpServer(
    id = UUID.randomUUID().toString(),
    name = "Telegram MCP",
    type = McpServerType.SSE,
    config = McpServerConfig.Sse(
        url = "http://localhost:8001/sse",
        headers = mapOf(
            "X-API-Key" to "your-secret-api-key-here"
        )
    ),
    enabled = true
)
```

### Проверка подключения

1. Добавьте сервер в настройках
2. Включите сервер (toggle switch)
3. Проверьте статус подключения - должно показать "Connected"
4. Откройте чат и отправьте запрос, использующий инструмент

## Устранение неполадок

### Ошибка: 401 Unauthorized

**Причина:** API ключ не передан или неверный

**Решения:**
```bash
# Проверить, что ключ установлен на сервере
echo $MCP_API_KEY

# Проверить, что ключ совпадает в запросе
curl -v -H "X-API-Key: wrong-key" http://localhost:8001/tools
# Вы увидите 401 и "Unauthorized: missing or invalid X-API-Key header"

# Использовать правильный ключ
curl -H "X-API-Key: correct-key-here" http://localhost:8001/tools
```

### Ошибка: Connection refused

**Причина:** Сервер не запущен

**Решение:**
```bash
# Запустить сервер с API ключом
export MCP_API_KEY="your-key"
python launcher.py telegram
```

### Проверка логов сервера

При проблемах с аутентификацией проверьте логи сервера:

```bash
# Сервер показывает попытки подключения
INFO: 127.0.0.1:52342 - "GET /sse HTTP/1.1" 200 OK
INFO: 127.0.0.1:52343 - "POST /message?sessionId=abc123 HTTP/1.1" 200 OK

# При ошибке аутентификации
WARNING: Unauthorized access attempt from 127.0.0.1
INFO: 127.0.0.1:52344 - "GET /tools HTTP/1.1" 401 Unauthorized
```

## Тестирование разных серверов

### GitHub MCP (порт 8000)

```bash
export MCP_API_KEY="github-secret-key"
export GITHUB_TOKEN="ghp_your_github_token"  # опционально
python launcher.py github

# Проверка
curl -H "X-API-Key: github-secret-key" http://localhost:8000/tools
```

### Telegram MCP (порт 8001)

```bash
export MCP_API_KEY="telegram-secret-key"
export TELEGRAM_API_ID="your_api_id"
export TELEGRAM_API_HASH="your_api_hash"
python launcher.py telegram

# Проверка
curl -H "X-API-Key: telegram-secret-key" http://localhost:8001/tools
```

### Weather MCP (порт 8002)

```bash
export MCP_API_KEY="weather-secret-key"
python launcher.py weather

# Проверка
curl -H "X-API-Key: weather-secret-key" http://localhost:8002/tools
```

### Docker MCP (порт 8006)

```bash
export MCP_API_KEY="docker-secret-key"
python launcher.py docker

# Проверка
curl -H "X-API-Key: docker-secret-key" http://localhost:8006/tools
```

### ADB MCP (порт 8007)

```bash
export MCP_API_KEY="adb-secret-key"
python launcher.py adb

# Проверка
curl -H "X-API-Key: adb-secret-key" http://localhost:8007/tools
```

## Рекомендации по безопасности

1. **Используйте сложные ключи:** Генерируйте случайные ключи длиной минимум 32 символа
   ```bash
   # Генерация случайного ключа
   python3 -c "import secrets; print(secrets.token_urlsafe(32))"
   ```

2. **Храните ключи в переменных окружения:** Никогда не коммитьте ключи в git

3. **Используйте HTTPS в продакшене:** Для локальной разработки HTTP допустим

4. **Разные ключи для разных серверов:** Каждый сервер должен иметь уникальный ключ

5. **Ротация ключей:** Периодически меняйте API ключи

## Быстрая справка

### Запуск с аутентификацией
```bash
export MCP_API_KEY="your-key"
python launcher.py [server_name]
```

### Запуск без аутентификации (для тестирования)
```bash
python launcher.py [server_name] --no-auth
```

### Проверка с API ключом
```bash
# Заголовок (рекомендуется)
curl -H "X-API-Key: your-key" http://localhost:PORT/endpoint

# Query параметр (альтернатива)
curl "http://localhost:PORT/endpoint?api_key=your-key"
```

### Публичные endpoint'ы (без ключа)
- `GET /health` - Статус сервера
- `GET /` - Информация о сервере
- `GET /docs` - OpenAPI документация
- `GET /redoc` - ReDoc документация

### Защищённые endpoint'ы (требуют ключ)
- `GET /sse` - SSE подключение
- `POST /message` - Отправка MCP сообщений
- `GET /tools` - Список инструментов
