# MCP Серверы

## Что такое MCP?

MCP (Model Context Protocol) - это протокол для интеграции AI-агентов с внешними инструментами и источниками данных. В нашем приложении MCP используется для:

- Интеграции с Git репозиториями
- Работы с документацией
- Интеграции с CRM системами
- Расширения возможностей AI-агента

## Как запустить Git MCP Server?

### Быстрый старт

```bash
cd mcp-servers
./START.sh
```

### Ручной запуск

```bash
cd mcp-servers
python -m git.main --no-auth
```

### С аутентификацией

```bash
python -m git.main --auth-token YOUR_GITHUB_TOKEN
```

## Проблемы с MCP серверами

### Ошибка: "Connection refused" или "MCP server not responding"

**Причины:**
1. MCP сервер не запущен
2. Неверный порт или адрес
3. Firewall блокирует соединение

**Решение:**

1. **Запустите MCP сервер:**
   ```bash
   cd mcp-servers
   ./START.sh
   ```

2. **Проверьте, что сервер запущен:**
   ```bash
   ps aux | grep python | grep mcp
   ```

3. **Проверьте порт (по умолчанию 8080):**
   ```bash
   lsof -i :8080
   ```

4. **Если порт занят, укажите другой:**
   ```bash
   python -m git.main --port 8081
   ```

### Ошибка: "Module not found" при запуске MCP сервера

**Решение:**

1. **Установите зависимости:**
   ```bash
   cd mcp-servers
   pip install -r requirements.txt
   ```

2. **Или используйте виртуальное окружение:**
   ```bash
   cd mcp-servers
   python -m venv venv
   source venv/bin/activate  # Linux/macOS
   # или venv\Scripts\activate  # Windows
   pip install -r requirements.txt
   ```

### Ошибка: "Permission denied" при работе с Git

**Решение:**

1. **Проверьте права доступа к репозиторию**
2. **Настройте SSH ключи для Git**
3. **Или используйте HTTPS с токеном:**
   ```bash
   python -m git.main --auth-token YOUR_GITHUB_TOKEN
   ```

## Как добавить свой MCP сервер?

1. Создайте Python модуль в `mcp-servers/`
2. Реализуйте MCP Protocol согласно спецификации
3. Добавьте конфигурацию в AI Agent
4. Зарегистрируйте инструменты

Подробнее: `mcp-servers/README.md`

## Конфигурация MCP в AI Agent

В приложении AI Agent:
1. Откройте настройки (Settings)
2. Перейдите в раздел "MCP Servers"
3. Добавьте новый сервер:
   - Name: Git MCP
   - URL: http://localhost:8080
   - Type: stdio или http
4. Сохраните и перезапустите агента
