# Руководство по развёртыванию MCP Server на VPS

Пошаговая инструкция по установке и настройке MCP-сервера на Ubuntu VPS с Caddy, systemd и Let's Encrypt.

## Оглавление

1. [Подготовка локальной машины](#1-подготовка-локальной-машины)
2. [Подготовка VPS](#2-подготовка-vps)
3. [Установка JDK](#3-установка-jdk)
4. [Установка и настройка Caddy](#4-установка-и-настройка-caddy)
5. [Настройка DNS](#5-настройка-dns)
6. [Создание пользователя и директорий](#6-создание-пользователя-и-директорий)
7. [Деплой приложения](#7-деплой-приложения)
8. [Настройка systemd](#8-настройка-systemd)
9. [Настройка firewall](#9-настройка-firewall)
10. [Проверка работоспособности](#10-проверка-работоспособности)
11. [Настройка UptimeRobot](#11-настройка-uptimerobot)
12. [Обновление приложения](#12-обновление-приложения)
13. [Устранение проблем](#13-устранение-проблем)

---

## 1. Подготовка локальной машины

### 1.1. Сборка приложения

```bash
cd mcp-server
./gradlew shadowJar
```

Проверьте, что JAR создан:
```bash
ls -la build/libs/mcp-server-all.jar
```

### 1.2. Генерация API-ключа

Сгенерируйте случайный API-ключ:
```bash
openssl rand -hex 32
```

**Сохраните этот ключ!** Он понадобится на шаге 7.

Пример вывода:
```
a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef12345678
```

---

## 2. Подготовка VPS

### 2.1. Подключение к серверу

```bash
ssh root@YOUR_VPS_IP
```

### 2.2. Обновление системы

```bash
apt update && apt upgrade -y
```

### 2.3. Установка базовых утилит

```bash
apt install -y curl wget htop nano unzip
```

---

## 3. Установка JDK

### 3.1. Установка OpenJDK 17

```bash
apt install -y openjdk-17-jre-headless
```

### 3.2. Проверка установки

```bash
java -version
```

Ожидаемый вывод:
```
openjdk version "17.0.x" ...
```

---

## 4. Установка и настройка Caddy

### 4.1. Установка Caddy

```bash
apt install -y debian-keyring debian-archive-keyring apt-transport-https curl

curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg

curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list

apt update
apt install -y caddy
```

### 4.2. Проверка установки

```bash
caddy version
systemctl status caddy
```

### 4.3. Создание конфигурации Caddy

Откройте файл конфигурации:
```bash
nano /etc/caddy/Caddyfile
```

Замените содержимое на (замените `YOUR_DOMAIN.COM` на ваш домен):
```
YOUR_DOMAIN.COM {
    reverse_proxy localhost:8081

    # Логирование
    log {
        output file /var/log/caddy/access.log
        format json
    }

    # Заголовки безопасности
    header {
        X-Content-Type-Options nosniff
        X-Frame-Options DENY
        Referrer-Policy strict-origin-when-cross-origin
    }
}
```

### 4.4. Создание директории для логов

```bash
mkdir -p /var/log/caddy
chown caddy:caddy /var/log/caddy
```

### 4.5. Проверка конфигурации

```bash
caddy validate --config /etc/caddy/Caddyfile
```

**Не перезапускайте Caddy пока!** Сначала настройте DNS.

---

## 5. Настройка DNS

### 5.1. Добавление A-записи

В панели управления вашего домена (регистратор или Cloudflare):

1. Создайте **A-запись**:
   - **Имя (Host):** `@` или `mcp` (для субдомена `mcp.yourdomain.com`)
   - **Значение (Value):** IP-адрес вашего VPS
   - **TTL:** 300 (5 минут) или Auto

2. Дождитесь распространения DNS (5-30 минут)

### 5.2. Проверка DNS

С локальной машины:
```bash
dig YOUR_DOMAIN.COM +short
# или
nslookup YOUR_DOMAIN.COM
```

Должен вернуться IP-адрес вашего VPS.

---

## 6. Создание пользователя и директорий

### 6.1. Создание системного пользователя

```bash
useradd -r -s /bin/false -d /opt/mcp-server mcp
```

### 6.2. Создание директорий

```bash
mkdir -p /opt/mcp-server/plugins
mkdir -p /var/log/mcp-server
```

### 6.3. Установка прав

```bash
chown -R mcp:mcp /opt/mcp-server
chown -R mcp:mcp /var/log/mcp-server
chmod 750 /opt/mcp-server
```

---

## 7. Деплой приложения

### 7.1. Копирование JAR на сервер

**С локальной машины:**
```bash
scp build/libs/mcp-server-all.jar root@YOUR_VPS_IP:/opt/mcp-server/mcp-server.jar
```

### 7.2. Установка прав на JAR

**На сервере:**
```bash
chown mcp:mcp /opt/mcp-server/mcp-server.jar
chmod 640 /opt/mcp-server/mcp-server.jar
```

### 7.3. Создание файла окружения

```bash
nano /opt/mcp-server/.env
```

Содержимое (замените `YOUR_API_KEY` на ключ из шага 1.2):
```bash
# MCP Server Environment
MCP_API_KEY=YOUR_API_KEY_HERE
SERVER_HOST=127.0.0.1
SERVER_PORT=8081
PLUGINS_DIR=/opt/mcp-server/plugins
RATE_LIMIT_RPM=100
```

### 7.4. Защита файла окружения

```bash
chown mcp:mcp /opt/mcp-server/.env
chmod 600 /opt/mcp-server/.env
```

---

## 8. Настройка systemd

### 8.1. Создание unit-файла

```bash
nano /etc/systemd/system/mcp-server.service
```

Содержимое:
```ini
[Unit]
Description=MCP Server
Documentation=https://github.com/your-repo/mcp-server
After=network.target

[Service]
Type=simple
User=mcp
Group=mcp
WorkingDirectory=/opt/mcp-server

# Java с ограничением памяти для минимального VPS
ExecStart=/usr/bin/java \
    -Xms128m \
    -Xmx256m \
    -XX:+UseSerialGC \
    -XX:MaxMetaspaceSize=64m \
    -Djava.security.egd=file:/dev/./urandom \
    -jar mcp-server.jar

# Загрузка переменных окружения
EnvironmentFile=/opt/mcp-server/.env

# Автоматический перезапуск при сбое
Restart=always
RestartSec=10

# Ограничения безопасности
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/mcp-server /var/log/mcp-server

# Логирование в journald
StandardOutput=journal
StandardError=journal
SyslogIdentifier=mcp-server

[Install]
WantedBy=multi-user.target
```

### 8.2. Активация сервиса

```bash
systemctl daemon-reload
systemctl enable mcp-server
systemctl start mcp-server
```

### 8.3. Проверка статуса

```bash
systemctl status mcp-server
```

### 8.4. Просмотр логов

```bash
journalctl -u mcp-server -f
```

---

## 9. Настройка firewall

### 9.1. Установка ufw (если не установлен)

```bash
apt install -y ufw
```

### 9.2. Базовая настройка

```bash
# Разрешить SSH (важно! иначе потеряете доступ)
ufw allow ssh

# Разрешить HTTP (для Let's Encrypt проверки)
ufw allow 80/tcp

# Разрешить HTTPS
ufw allow 443/tcp

# Включить firewall
ufw enable
```

### 9.3. Проверка правил

```bash
ufw status verbose
```

### 9.4. Перезапуск Caddy

Теперь, когда DNS настроен и порты открыты:
```bash
systemctl restart caddy
systemctl status caddy
```

Caddy автоматически получит SSL-сертификат от Let's Encrypt.

---

## 10. Проверка работоспособности

### 10.1. Проверка локально на сервере

```bash
curl http://localhost:8081/health
```

Ожидаемый ответ:
```json
{"status":"ok","uptime":12345,"tools_count":1,"active_sessions":0}
```

### 10.2. Проверка через HTTPS

```bash
curl https://YOUR_DOMAIN.COM/health
```

### 10.3. Проверка аутентификации

```bash
curl -H "Authorization: Bearer YOUR_API_KEY" https://YOUR_DOMAIN.COM/tools
```

### 10.4. Полный тест MCP

**Терминал 1 (SSE-соединение):**
```bash
curl -N -H "Authorization: Bearer YOUR_API_KEY" https://YOUR_DOMAIN.COM/sse
```

Запишите `sessionId` из первого события.

**Терминал 2 (отправка запроса):**
```bash
curl -X POST \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  "https://YOUR_DOMAIN.COM/message?sessionId=SESSION_ID"
```

---

## 11. Настройка UptimeRobot

### 11.1. Регистрация

1. Зайдите на https://uptimerobot.com/
2. Создайте бесплатный аккаунт

### 11.2. Добавление монитора

1. Нажмите **"Add New Monitor"**
2. Настройки:
   - **Monitor Type:** HTTP(s)
   - **Friendly Name:** MCP Server
   - **URL:** `https://YOUR_DOMAIN.COM/health`
   - **Monitoring Interval:** 5 minutes

3. Настройте уведомления (email, Telegram, Slack)

4. Сохраните

---

## 12. Обновление приложения

### 12.1. Скрипт деплоя (опционально)

Создайте `deploy.sh` на локальной машине:
```bash
#!/bin/bash
set -e

VPS_HOST="root@YOUR_VPS_IP"
JAR_PATH="build/libs/mcp-server-all.jar"
REMOTE_PATH="/opt/mcp-server/mcp-server.jar"

echo "Building..."
./gradlew shadowJar

echo "Uploading..."
scp $JAR_PATH $VPS_HOST:$REMOTE_PATH

echo "Setting permissions..."
ssh $VPS_HOST "chown mcp:mcp $REMOTE_PATH && chmod 640 $REMOTE_PATH"

echo "Restarting service..."
ssh $VPS_HOST "systemctl restart mcp-server"

echo "Checking status..."
ssh $VPS_HOST "systemctl status mcp-server --no-pager"

echo "Done!"
```

```bash
chmod +x deploy.sh
./deploy.sh
```

### 12.2. Ручное обновление

```bash
# Локально
./gradlew shadowJar
scp build/libs/mcp-server-all.jar root@YOUR_VPS_IP:/opt/mcp-server/mcp-server.jar

# На сервере
ssh root@YOUR_VPS_IP
chown mcp:mcp /opt/mcp-server/mcp-server.jar
systemctl restart mcp-server
journalctl -u mcp-server -f
```

---

## 13. Устранение проблем

### Проблема: Сервис не запускается

```bash
# Проверьте логи
journalctl -u mcp-server -n 50 --no-pager

# Проверьте права
ls -la /opt/mcp-server/
cat /opt/mcp-server/.env

# Попробуйте запустить вручную
sudo -u mcp java -jar /opt/mcp-server/mcp-server.jar
```

### Проблема: Caddy не получает сертификат

```bash
# Проверьте логи Caddy
journalctl -u caddy -f

# Проверьте DNS
dig YOUR_DOMAIN.COM

# Проверьте порты
ss -tlnp | grep -E '(80|443)'

# Проверьте firewall
ufw status
```

### Проблема: Не хватает памяти

```bash
# Проверьте использование памяти
free -h
htop

# Уменьшите heap JVM в systemd unit
# -Xmx128m вместо -Xmx256m
```

### Проблема: Connection refused

```bash
# Проверьте, что сервис запущен
systemctl status mcp-server

# Проверьте, что слушает порт
ss -tlnp | grep 8081

# Проверьте firewall
ufw status
```

### Полезные команды

```bash
# Логи MCP-сервера
journalctl -u mcp-server -f

# Логи Caddy
journalctl -u caddy -f

# Перезапуск сервисов
systemctl restart mcp-server
systemctl restart caddy

# Использование ресурсов
htop
free -h
df -h

# Проверка сертификата
curl -vI https://YOUR_DOMAIN.COM 2>&1 | grep -E "(SSL|certificate|expire)"
```

---

## Чеклист после установки

- [ ] JDK 17 установлен
- [ ] Caddy установлен и настроен
- [ ] DNS A-запись указывает на VPS
- [ ] SSL-сертификат получен автоматически
- [ ] Пользователь `mcp` создан
- [ ] JAR загружен в `/opt/mcp-server/`
- [ ] `.env` файл создан с API-ключом
- [ ] systemd unit создан и активирован
- [ ] Firewall настроен (22, 80, 443)
- [ ] `/health` отвечает через HTTPS
- [ ] UptimeRobot настроен

---

*Документ создан для Ubuntu 22.04/24.04 LTS. Для других дистрибутивов команды могут отличаться.*
