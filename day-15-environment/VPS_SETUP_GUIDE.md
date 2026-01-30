# VPS Setup Guide: Android Build & Emulator Environment

Полное руководство по настройке VPS-сервера на **Ubuntu 24.04 LTS** для автоматической сборки Android-приложений и управления эмулятором через AI-агента.

> **Важно:** Данное руководство специально разработано для Ubuntu 24.04 LTS и использует APT пакетный менеджер, systemd, и Ubuntu-специфичные настройки.

> **⚡ Нужна быстрая настройка?** См. **UBUNTU_QUICK_SETUP.md** - команды copy-paste для настройки за 45-60 минут!

> **💡 Столкнулись с проблемой?** См. **UBUNTU_COMMON_ISSUES.md** - 11 частых проблем с быстрыми решениями!

## 📋 Требования к VPS

### Минимальные характеристики:
- **OS**: Ubuntu 24.04 LTS
- **CPU**: 4 vCPU (желательно с поддержкой KVM)
- **RAM**: 8 GB (минимум 6 GB)
- **Disk**: 50 GB SSD
- **Network**: Публичный IP-адрес
- **Virtualization**: Поддержка nested virtualization (для эмулятора)

### Рекомендуемые провайдеры:
- Hetzner Cloud (CPX31 или выше) - поддержка KVM
- DigitalOcean (CPU-Optimized Droplets)
- Vultr (High Frequency Compute)

### Проверка поддержки KVM (Ubuntu):
```bash
# После подключения к VPS:
egrep -c '(vmx|svm)' /proc/cpuinfo
# Если вывод > 0, KVM поддерживается

# Устанавливаем cpu-checker (Ubuntu package)
sudo apt update
sudo apt install -y cpu-checker

# Проверяем возможность использования KVM
sudo kvm-ok
# Ожидаемый вывод:
# INFO: /dev/kvm exists
# KVM acceleration can be used

# Если KVM недоступен, проверяем модули ядра
lsmod | grep kvm
# Должны быть загружены: kvm, kvm_intel (или kvm_amd)
```

---

## 🚀 Шаг 1: Первоначальная настройка VPS

### 1.1 Подключение и обновление Ubuntu системы
```bash
# Подключаемся по SSH
ssh root@YOUR_VPS_IP

# ВАЖНО: Включаем universe репозитории (на минимальных установках могут быть отключены)
sudo add-apt-repository universe
sudo add-apt-repository multiverse

# Обновляем список пакетов
sudo apt update

# Обновляем установленные пакеты
sudo apt upgrade -y

# Обновляем ядро и системные пакеты (если нужно)
sudo apt dist-upgrade -y

# Удаляем неиспользуемые пакеты
sudo apt autoremove -y

# Устанавливаем базовые инструменты для Ubuntu
sudo apt install -y \
  curl wget git vim nano \
  htop screen tmux \
  build-essential \
  software-properties-common \
  apt-transport-https \
  ca-certificates \
  gnupg \
  lsb-release \
  net-tools

# Проверяем версию Ubuntu
lsb_release -a
# Должно быть: Ubuntu 24.04 LTS

# Проверяем что universe репозитории включены
apt-cache policy | grep universe
```

### 1.2 Настройка Git SSH ключей (рекомендуется)

Если вы планируете работать с Git репозиториями (клонирование, push, pull), настройте SSH ключи сразу.

```bash
# Генерируем SSH ключ
ssh-keygen -t ed25519 -C "your_email@example.com"
# Нажимаете Enter 3 раза (путь по умолчанию, без passphrase)

# Показываем публичный ключ
cat ~/.ssh/id_ed25519.pub

# Копируйте вывод и добавьте на GitHub/GitLab:
# GitHub: https://github.com/settings/keys → "New SSH key"
# GitLab: https://gitlab.com/-/profile/keys → "Add new key"

# Тестируем соединение с GitHub
ssh -T git@github.com
# Должно быть: "Hi USERNAME! You've successfully authenticated..."

# Для GitLab
ssh -T git@gitlab.com

# Настраиваем Git
git config --global user.name "Your Name"
git config --global user.email "your_email@example.com"

# Проверяем конфигурацию
git config --list
```

**Важно:** GitHub и другие платформы больше не поддерживают аутентификацию по паролю. SSH ключи - обязательное требование для работы с Git на VPS.

### 1.3 Создание пользователя (опционально, но рекомендуется для Ubuntu)
```bash
# Создаем пользователя для работы
sudo adduser android-builder
# Следуем интерактивным подсказкам Ubuntu (пароль, имя и т.д.)

# Добавляем пользователя в группу sudo (Ubuntu использует sudo вместо wheel)
sudo usermod -aG sudo android-builder

# Проверяем группы пользователя
groups android-builder

# Опционально: настраиваем sudo без пароля для удобства
sudo visudo
# Добавляем строку: android-builder ALL=(ALL) NOPASSWD:ALL

# Переключаемся на пользователя
su - android-builder

# Проверяем sudo права
sudo whoami
# Должно вернуть: root
```

---

## 🐳 Шаг 2: Установка Docker и Docker Compose

> **⚠️ Важно:** Если на вашей Ubuntu минимальная установка, директории `/etc/apt/sources.list.d/` может не существовать. В этом случае команды ниже автоматически создадут необходимые директории.

### 2.1 Установка Docker на Ubuntu 24.04
```bash
# Удаляем старые версии Docker (если есть)
sudo apt remove -y docker docker-engine docker.io containerd runc

# Убедимся что зависимости установлены
sudo apt update
sudo apt install -y apt-transport-https ca-certificates curl gnupg lsb-release

# Проверяем и создаем необходимые директории
sudo mkdir -p /etc/apt/keyrings
sudo mkdir -p /etc/apt/sources.list.d
sudo chmod 755 /etc/apt/keyrings
sudo chmod 755 /etc/apt/sources.list.d

# Добавляем официальный GPG ключ Docker для Ubuntu
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Проверяем кодовое имя Ubuntu версии
. /etc/os-release
echo "Ubuntu codename: $VERSION_CODENAME"
echo "Architecture: $(dpkg --print-architecture)"

# Добавляем репозиторий Docker для Ubuntu 24.04 (noble)
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Проверяем что файл создан
ls -la /etc/apt/sources.list.d/docker.list

# Обновляем список пакетов
sudo apt update

# Устанавливаем Docker Engine, CLI, containerd и Docker Compose plugin
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Проверяем установку
docker --version
docker compose version

# Тестируем Docker
sudo docker run hello-world
```

### 2.2 Настройка прав для текущего пользователя
```bash
# Добавляем пользователя в группу docker
sudo usermod -aG docker $USER

# Применяем изменения (перелогинимся)
newgrp docker

# Проверяем работу без sudo
docker ps
```

### 2.3 Настройка Docker daemon для оптимизации
```bash
# Создаем конфигурационный файл
sudo mkdir -p /etc/docker
sudo nano /etc/docker/daemon.json
```

Вставляем следующую конфигурацию:
```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "default-ulimits": {
    "nofile": {
      "Name": "nofile",
      "Hard": 64000,
      "Soft": 64000
    }
  }
}
```

Перезапускаем Docker:
```bash
sudo systemctl restart docker
sudo systemctl enable docker
```

---

## 📱 Шаг 3: Установка Android SDK и инструментов

### 3.1 Установка Java (OpenJDK) на Ubuntu
```bash
# Ubuntu 24.04 поддерживает несколько версий Java
# Для Android рекомендуется OpenJDK 17 или 21

# Обновляем список пакетов
sudo apt update

# Устанавливаем OpenJDK 17 (LTS, рекомендуется для Android)
# Устанавливаем пакеты по отдельности (во избежание ошибок apt)
sudo apt install openjdk-17-jdk
sudo apt install openjdk-17-jre

# Проверяем установку
java -version
javac -version

# Устанавливаем JAVA_HOME в Ubuntu
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# Проверяем JAVA_HOME
echo $JAVA_HOME

# Опционально: если нужно несколько версий Java, используем update-alternatives
sudo update-alternatives --config java
```

### 3.2 Установка Android SDK Command Line Tools
```bash
# Создаем директорию для SDK
mkdir -p ~/android-sdk
cd ~/android-sdk

# Скачиваем Command Line Tools (актуальная версия на 2026)
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip

# Распаковываем
unzip commandlinetools-linux-*_latest.zip
rm commandlinetools-linux-*_latest.zip

# Создаем правильную структуру директорий
mkdir -p cmdline-tools/latest
mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true
```

### 3.3 Настройка переменных окружения
```bash
# Добавляем в ~/.bashrc
cat >> ~/.bashrc << 'EOF'

# Android SDK
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/emulator
EOF

# Применяем изменения
source ~/.bashrc

# Проверяем
echo $ANDROID_HOME
```

### 3.4 Установка Android SDK компонентов
```bash
# Принимаем лицензии
yes | sdkmanager --licenses

# Устанавливаем необходимые компоненты
sdkmanager "platform-tools" \
           "platforms;android-34" \
           "build-tools;34.0.0" \
           "emulator" \
           "system-images;android-34;google_apis;x86_64"

# Проверяем установленные пакеты
sdkmanager --list_installed
```

### 3.5 Установка ADB и проверка
```bash
# Проверяем ADB
adb version

# Запускаем ADB server
adb start-server
```

---

## 🖥️ Шаг 4: Настройка Android Emulator

### 4.1 Создание AVD (Android Virtual Device)
```bash
# Создаем эмулятор Pixel 6
avdmanager create avd \
  -n pixel6_api34 \
  -k "system-images;android-34;google_apis;x86_64" \
  -d "pixel_6" \
  --force

# Список созданных AVD
avdmanager list avd
```

### 4.2 Настройка KVM для аппаратного ускорения на Ubuntu
```bash
# Убеждаемся что universe репозитории включены (нужны для libvirt пакетов)
sudo add-apt-repository universe -y
sudo apt update

# Устанавливаем KVM и необходимые пакеты для Ubuntu
sudo apt install -y qemu-kvm libvirt-daemon-system libvirt-clients bridge-utils

# Проверяем, что KVM доступен
ls -la /dev/kvm

# Если файл не существует, загружаем модули ядра
sudo modprobe kvm
sudo modprobe kvm_intel  # Для Intel CPU
# sudo modprobe kvm_amd  # Для AMD CPU

# Делаем автозагрузку модулей при старте Ubuntu
echo "kvm" | sudo tee -a /etc/modules
echo "kvm_intel" | sudo tee -a /etc/modules  # или kvm_amd для AMD

# Добавляем пользователя в группы kvm и libvirt (Ubuntu)
sudo usermod -aG kvm $USER
sudo usermod -aG libvirt $USER
newgrp kvm

# Проверяем права
ls -la /dev/kvm
# Должно быть: crw-rw---- 1 root kvm

# Проверяем группы текущего пользователя
groups
# Должны быть: kvm, libvirt

# Запускаем libvirt сервис (для Ubuntu)
sudo systemctl enable libvirtd
sudo systemctl start libvirtd
sudo systemctl status libvirtd
```

### 4.3 Тестовый запуск эмулятора (headless)
```bash
# Запускаем в headless режиме (без GUI)
emulator -avd pixel6_api34 -no-window -no-audio -gpu swiftshader_indirect &

# Ждем загрузки (~30 секунд)
sleep 30

# Проверяем подключенные устройства
adb devices

# Должно быть:
# List of devices attached
# emulator-5554   device

# Останавливаем эмулятор
adb -s emulator-5554 emu kill
```

---

## 🐍 Шаг 5: Установка Python и зависимостей

### 5.1 Установка Python на Ubuntu 24.04
```bash
# Ubuntu 24.04 поставляется с Python 3.12 по умолчанию
python3 --version
# Должно быть: Python 3.12.x

# Устанавливаем pip и venv для Ubuntu
sudo apt update
sudo apt install -y python3-pip python3-venv python3-dev

# Ubuntu 24.04 использует externally-managed-environment
# Поэтому всегда используем venv для проектов

# Проверяем установку
pip3 --version
python3 -m venv --help

# Создаем символические ссылки для удобства (опционально)
# sudo ln -s /usr/bin/python3 /usr/bin/python
# sudo ln -s /usr/bin/pip3 /usr/bin/pip

# Обновляем pip в системе (опционально)
# python3 -m pip install --upgrade pip --break-system-packages
# Но лучше обновлять pip внутри venv
```

### 5.2 Создание рабочей директории для проекта
```bash
# Создаем директорию проекта
mkdir -p ~/ai-agent-project
cd ~/ai-agent-project

# Клонируем репозиторий (или копируем файлы)
# git clone YOUR_REPO_URL .
```

### 5.3 Установка Python зависимостей для MCP серверов
```bash
# Создаем виртуальное окружение
cd ~/ai-agent-project/mcp-servers
python3 -m venv venv
source venv/bin/activate

# Устанавливаем зависимости
pip install --upgrade pip
pip install sse-starlette starlette uvicorn httpx python-dotenv

# Для Docker MCP Server
pip install docker

# Проверяем установку
pip list
```

---

## 🔧 Шаг 6: Настройка Docker Compose для Android Build

### 6.1 Создание Dockerfile для Android сборки
Файл уже создан в `mcp-servers/docker/android-builder/Dockerfile`

### 6.2 Создание docker-compose.yml
Файл уже создан в `mcp-servers/docker/docker-compose.yml`

### 6.3 Тестовая сборка Docker образа
```bash
cd ~/ai-agent-project/mcp-servers/docker

# Собираем образ (первый раз займет ~10-15 минут)
docker compose build android-builder

# Проверяем созданный образ
docker images | grep android-builder
```

---

## 🚀 Шаг 7: Запуск MCP серверов

### 7.1 Настройка переменных окружения
```bash
cd ~/ai-agent-project/mcp-servers

# Создаем .env файл
cat > .env << 'EOF'
# API Key для MCP серверов (замените на свой)
MCP_API_KEY=your_secret_key_here_12345

# Docker настройки
DOCKER_HOST=unix:///var/run/docker.sock

# Android SDK
ANDROID_HOME=/home/android-builder/android-sdk
ANDROID_SDK_ROOT=/home/android-builder/android-sdk
EOF

# Делаем файл приватным
chmod 600 .env
```

### 7.2 Запуск Docker MCP Server
```bash
cd ~/ai-agent-project/mcp-servers
source venv/bin/activate

# Запускаем Docker MCP Server
python launcher.py docker --no-auth

# В новом терминале проверяем доступность
curl http://localhost:8006/health
# Должно вернуть: {"status":"healthy"}
```

### 7.3 Запуск ADB MCP Server
```bash
# В новом терминале/screen сессии
cd ~/ai-agent-project/mcp-servers
source venv/bin/activate

# Запускаем ADB MCP Server
python launcher.py adb --no-auth

# Проверяем доступность
curl http://localhost:8007/health
```

### 7.4 Использование screen для фоновой работы
```bash
# Устанавливаем screen (если не установлен)
sudo apt install -y screen

# Создаем сессию для MCP серверов
screen -S mcp-servers

# Внутри screen:
cd ~/ai-agent-project/mcp-servers
source venv/bin/activate
python launcher.py docker adb --no-auth

# Отключаемся от screen: Ctrl+A, затем D
# Переподключаемся: screen -r mcp-servers
```

---

## 🔒 Шаг 8: Настройка файрвола и безопасности

### 8.1 Установка UFW (Uncomplicated Firewall)
```bash
# Устанавливаем UFW
sudo apt install -y ufw

# Разрешаем SSH (важно!)
sudo ufw allow 22/tcp

# Разрешаем порты MCP серверов (только с определенных IP)
sudo ufw allow from YOUR_CLIENT_IP to any port 8000:8010 proto tcp

# Включаем файрвол
sudo ufw enable

# Проверяем статус
sudo ufw status verbose
```

### 8.2 Настройка API ключа для MCP серверов
```bash
# Генерируем случайный API ключ
openssl rand -hex 32

# Обновляем .env файл с новым ключом
nano ~/ai-agent-project/mcp-servers/.env
```

### 8.3 Запуск серверов с аутентификацией
```bash
cd ~/ai-agent-project/mcp-servers
source venv/bin/activate

# Запускаем С API ключом (без --no-auth)
export MCP_API_KEY="ваш_сгенерированный_ключ"
python launcher.py docker adb
```

---

## ✅ Шаг 9: Проверка работоспособности

### 9.1 Чеклист проверки всех компонентов

```bash
# 1. Docker работает
docker ps
docker compose version

# 2. Android SDK установлен
which adb
adb version
which avdmanager

# 3. Эмулятор создан
avdmanager list avd

# 4. Python окружение активно
which python
python --version

# 5. MCP серверы доступны
curl http://localhost:8006/health  # Docker
curl http://localhost:8007/health  # ADB

# 6. Docker MCP Server работает
curl -X POST http://localhost:8006/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# 7. ADB MCP Server работает
curl -X POST http://localhost:8007/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

### 9.2 Тестовый запуск эмулятора через MCP
```bash
# Запускаем эмулятор через ADB MCP Server
curl -X POST http://localhost:8007/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "start_emulator",
      "arguments": {
        "avd_name": "pixel6_api34"
      }
    }
  }'

# Ждем 30 секунд, затем проверяем устройства
sleep 30
adb devices
```

---

## 🎯 Шаг 10: Подключение из мобильного приложения

### 10.1 Получение публичного IP VPS
```bash
curl ifconfig.me
# Запоминаем IP: например, 123.45.67.89
```

### 10.2 Настройка MCP серверов в приложении

Откройте приложение AI Agent на Android/Desktop и добавьте серверы:

**Docker MCP Server:**
- Name: `VPS Docker`
- URL: `http://123.45.67.89:8006`
- Transport: `SSE`
- API Key: (ваш ключ из .env)
- Enabled: ✓

**ADB MCP Server:**
- Name: `VPS Android Emulator`
- URL: `http://123.45.67.89:8007`
- Transport: `SSE`
- API Key: (ваш ключ из .env)
- Enabled: ✓

### 10.3 Тестовые команды в чате

Откройте чат и попробуйте:

```
Покажи список Docker контейнеров
```

```
Запусти Android эмулятор pixel6_api34
```

```
Собери APK из /path/to/project
```

```
Установи APK на эмулятор и сделай скриншот
```

---

## 🐛 Устранение проблем (Ubuntu)

### Ошибка "E: Unable to locate package" (libvirt, qemu и др.)
```bash
# Если получаете ошибку при установке пакетов:
# E: Unable to locate package libvirt-daemon-system
# E: Package 'qemu-kvm' has no installation candidate

# Причина: universe и multiverse репозитории отключены

# Решение: Включите репозитории
sudo add-apt-repository universe -y
sudo add-apt-repository multiverse -y
sudo apt update

# Попробуйте установить снова
sudo apt install -y qemu-kvm libvirt-daemon-system libvirt-clients

# Проверьте что репозитории включены
apt-cache policy | grep universe
```

**Важно:** Всегда включайте universe и multiverse репозитории СРАЗУ после первого входа на VPS!

### Ошибка "E: Invalid operation install"
```bash
# Если при установке пакетов получаете ошибку:
# E: Invalid operation install

# Причина: иногда apt не может обработать установку нескольких пакетов одновременно

# Решение 1: Установите пакеты по отдельности
sudo apt update
sudo apt install openjdk-17-jdk
sudo apt install openjdk-17-jre

# Решение 2: Используйте apt-get вместо apt
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk openjdk-17-jre

# Решение 3: Проверьте синтаксис команды
# Убедитесь что нет лишних символов, переносов строк
# Команда должна быть в одной строке или использовать \

# Проверьте установку
java -version
javac -version
```

### Ошибка при установке Docker: "No such file or directory"
```bash
# Если при добавлении репозитория Docker получаете ошибку:
# tee: /etc/apt/sources.list.d/docker.list: No such file or directory

# Создайте необходимые директории вручную
sudo mkdir -p /etc/apt/keyrings
sudo mkdir -p /etc/apt/sources.list.d
sudo chmod 755 /etc/apt/keyrings
sudo chmod 755 /etc/apt/sources.list.d

# Проверьте права доступа
ls -la /etc/apt/ | grep sources.list.d
# Должно быть: drwxr-xr-x

# Затем повторите команду добавления репозитория
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Проверьте результат
cat /etc/apt/sources.list.d/docker.list
```

### Эмулятор не запускается
```bash
# Проверяем KVM на Ubuntu
sudo kvm-ok

# Проверяем права на /dev/kvm
ls -la /dev/kvm

# Проверяем модули ядра
lsmod | grep kvm

# Загружаем модули вручную
sudo modprobe kvm
sudo modprobe kvm_intel  # или kvm_amd

# Добавляем пользователя в группы kvm и libvirt
sudo usermod -aG kvm $USER
sudo usermod -aG libvirt $USER
newgrp kvm

# Проверяем статус libvirt (Ubuntu)
sudo systemctl status libvirtd

# Если libvirt не запущен
sudo systemctl start libvirtd
sudo systemctl enable libvirtd
```

### Docker контейнеры не запускаются на Ubuntu
```bash
# Проверяем статус Docker service
sudo systemctl status docker

# Проверяем логи Docker через journalctl (Ubuntu systemd)
sudo journalctl -u docker -n 100 --no-pager

# Перезапускаем Docker
sudo systemctl restart docker

# Проверяем права пользователя
groups $USER | grep docker

# Если нет в группе docker
sudo usermod -aG docker $USER
newgrp docker

# Проверяем Docker daemon конфигурацию
sudo docker info

# Проверяем место на диске
df -h /var/lib/docker
```

### MCP серверы недоступны на Ubuntu
```bash
# Проверяем systemd сервис (если настроен)
sudo systemctl status mcp-servers.service

# Логи через journalctl
sudo journalctl -u mcp-servers.service -n 50

# Проверяем процессы Python
ps aux | grep python
ps aux | grep launcher.py

# Проверяем открытые порты через ss (современная замена netstat в Ubuntu)
sudo ss -tulpn | grep python
sudo ss -tulpn | grep 800[0-9]

# Проверяем UFW firewall
sudo ufw status verbose

# Если порты заблокированы, разрешаем
sudo ufw allow 8006/tcp
sudo ufw allow 8007/tcp

# Проверяем Python venv
which python
python --version
```

### Недостаточно памяти для эмулятора
```bash
# Проверяем использование RAM
free -h

# Проверяем swap
sudo swapon --show

# Останавливаем ненужные контейнеры
docker stop $(docker ps -q)

# Создаем swap файл 4GB (Ubuntu)
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# Делаем постоянным
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Проверяем память после добавления swap
free -h
```

### Проблемы с Python venv на Ubuntu 24.04
```bash
# Ubuntu 24.04 использует PEP 668 (externally-managed-environment)
# Всегда используйте venv для проектов

# Если возникает ошибка при установке пакетов
python3 -m venv venv
source venv/bin/activate
pip install --upgrade pip

# Проверяем что используем venv
which python
# Должно быть: /path/to/venv/bin/python
```

### Android SDK проблемы
```bash
# Проверяем переменные окружения
echo $ANDROID_HOME
echo $ANDROID_SDK_ROOT

# Если не установлены, добавляем в ~/.bashrc
nano ~/.bashrc
# Добавляем:
# export ANDROID_HOME=$HOME/android-sdk
# export ANDROID_SDK_ROOT=$ANDROID_HOME
# export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

source ~/.bashrc

# Проверяем права на SDK директорию
ls -la ~/android-sdk

# Проверяем sdkmanager
sdkmanager --list
```

### SSH подключение прерывается
```bash
# Настраиваем SSH keepalive на Ubuntu сервере
sudo nano /etc/ssh/sshd_config

# Добавляем/изменяем:
# ClientAliveInterval 60
# ClientAliveCountMax 3

# Перезапускаем SSH service
sudo systemctl restart sshd

# На клиенте также настраиваем keepalive
nano ~/.ssh/config
# Добавляем:
# Host *
#   ServerAliveInterval 60
#   ServerAliveCountMax 3
```

### Git аутентификация не работает
```bash
# Ошибка: "Password authentication is not supported"

# Решение 1: SSH ключи (рекомендуется)
ssh-keygen -t ed25519 -C "your_email@example.com"
cat ~/.ssh/id_ed25519.pub
# Добавьте ключ на https://github.com/settings/keys

# Тестируем
ssh -T git@github.com

# Меняем remote на SSH
git remote set-url origin git@github.com:USERNAME/REPO.git

# Решение 2: Personal Access Token
# Создайте токен на https://github.com/settings/tokens
# Используйте токен вместо пароля при git push
git config --global credential.helper store
```

---

## 📊 Мониторинг и логирование (Ubuntu)

### Мониторинг ресурсов с помощью Ubuntu инструментов
```bash
# Интерактивный мониторинг CPU, RAM, процессов
htop

# Стандартный top
top

# Docker ресурсы в реальном времени
docker stats

# Использование диска
df -h
du -sh /var/lib/docker

# Использование inode (важно для SSD)
df -i

# Сетевая статистика
sudo ss -s
sudo nethogs  # нужно установить: sudo apt install nethogs

# Температура CPU (если доступно)
sensors  # нужно установить: sudo apt install lm-sensors
```

### Логи через journalctl (Ubuntu systemd)
```bash
# Логи MCP серверов через systemd
sudo journalctl -u mcp-servers.service -f

# Логи за последний час
sudo journalctl -u mcp-servers.service --since "1 hour ago"

# Логи Docker
sudo journalctl -u docker.service -n 100

# Логи Nginx
sudo journalctl -u nginx.service -f

# Системные логи Ubuntu
sudo journalctl -xe

# Логи ядра
dmesg | tail -50
```

### Логи MCP серверов (альтернативные методы)
```bash
# Если используется screen
screen -r mcp-servers

# Если сервис работает через systemd, логи уже в journalctl
sudo journalctl -u mcp-servers.service -f

# Ручное перенаправление логов в файл (если не используется systemd)
cd ~/ai-agent-project/mcp-servers
mkdir -p logs
source venv/bin/activate
python launcher.py docker adb > logs/mcp.log 2>&1 &

# Просмотр логов
tail -f logs/mcp.log
```

### Настройка logrotate для логов (Ubuntu)
```bash
# Создаем конфигурацию logrotate
sudo nano /etc/logrotate.d/mcp-servers
```

Вставляем:
```
/home/android-builder/ai-agent-project/mcp-servers/logs/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 0644 android-builder android-builder
}
```

Тестируем:
```bash
sudo logrotate -d /etc/logrotate.d/mcp-servers
```

---

## 💾 Шаг 14: Настройка автоматических бэкапов (Ubuntu)

### 14.1 Бэкап критических файлов

```bash
# Создаем директорию для бэкапов
mkdir -p ~/backups

# Создаем скрипт бэкапа
nano ~/backups/backup.sh
```

Вставляем:
```bash
#!/bin/bash
# Бэкап скрипт для Ubuntu VPS

BACKUP_DIR="$HOME/backups"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_NAME="vps-backup-$DATE.tar.gz"

echo "Starting backup at $(date)"

# Бэкапим MCP серверы конфигурацию
tar -czf "$BACKUP_DIR/$BACKUP_NAME" \
    ~/ai-agent-project/mcp-servers/ \
    ~/.bashrc \
    ~/android-sdk/avd/ \
    2>/dev/null

# Удаляем бэкапы старше 7 дней
find "$BACKUP_DIR" -name "vps-backup-*.tar.gz" -mtime +7 -delete

echo "Backup completed: $BACKUP_NAME"
echo "Backup size: $(du -h $BACKUP_DIR/$BACKUP_NAME | cut -f1)"
```

Делаем исполняемым:
```bash
chmod +x ~/backups/backup.sh

# Тестируем
~/backups/backup.sh
```

### 14.2 Настройка cron для автоматических бэкапов (Ubuntu)

```bash
# Открываем crontab
crontab -e
```

Добавляем (ежедневный бэкап в 3:00 AM):
```
0 3 * * * /home/android-builder/backups/backup.sh >> /home/android-builder/backups/backup.log 2>&1
```

Проверяем:
```bash
# Список задач cron
crontab -l

# Статус cron сервиса (Ubuntu systemd)
sudo systemctl status cron
```

### 14.3 Бэкап Docker volumes

```bash
# Создаем скрипт бэкапа Docker volumes
nano ~/backups/backup-docker.sh
```

Вставляем:
```bash
#!/bin/bash
BACKUP_DIR="$HOME/backups/docker"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p "$BACKUP_DIR"

# Бэкапим все Docker volumes
for volume in $(docker volume ls -q); do
    echo "Backing up volume: $volume"
    docker run --rm \
        -v "$volume":/data \
        -v "$BACKUP_DIR":/backup \
        ubuntu:24.04 \
        tar czf "/backup/${volume}-${DATE}.tar.gz" /data
done
```

---

## 🎉 Готово! VPS на Ubuntu 24.04 настроен

Ваш VPS-сервер на **Ubuntu 24.04 LTS** полностью настроен и готов к работе!

### ✅ Что настроено:

**Базовая система:**
- ✅ Ubuntu 24.04 LTS обновлен до последней версии
- ✅ Базовые инструменты установлены
- ✅ Пользователь с sudo правами создан
- ✅ UFW firewall настроен

**Docker окружение:**
- ✅ Docker Engine установлен с official репозитория
- ✅ Docker Compose v2 (plugin) установлен
- ✅ Docker daemon оптимизирован для production

**Android разработка:**
- ✅ OpenJDK 17 установлен
- ✅ Android SDK Command Line Tools настроены
- ✅ ADB и platform-tools доступны
- ✅ Android эмулятор с KVM ускорением
- ✅ AVD (Pixel 6 API 34) создан

**MCP серверы:**
- ✅ Python 3.12 с venv
- ✅ Docker MCP Server на порту 8006
- ✅ ADB MCP Server на порту 8007
- ✅ Systemd сервисы для автозапуска
- ✅ Nginx обратный прокси (опционально)

**Мониторинг и безопасность:**
- ✅ Journalctl логирование через systemd
- ✅ Logrotate для ротации логов
- ✅ Автоматические бэкапы через cron
- ✅ Unattended upgrades для безопасности

### 🚀 Возможности AI-агента:

Теперь AI-агент может удаленно:
- 🐳 Управлять Docker контейнерами
- 📦 Собирать Android APK в изолированном окружении
- 📱 Запускать Android эмулятор с KVM ускорением
- 🔧 Устанавливать приложения на эмулятор
- 📸 Делать скриншоты и выполнять UI-тесты
- ⚡ Выполнять ADB команды удаленно
- 🔄 Автоматически восстанавливаться при сбоях (systemd)

### 📚 Дополнительные шаги (опционально):

**Безопасность:**
- Настройте HTTPS через nginx + Let's Encrypt (см. раздел 12.3)
- Настройте VPN для доступа к MCP серверам
- Добавьте fail2ban для защиты от брутфорса
- Настройте регулярное обновление SSL сертификатов

**Мониторинг:**
- Установите Prometheus + Grafana для метрик
- Настройте Netdata для real-time мониторинга
- Добавьте алерты в Telegram/Email при проблемах

**Оптимизация:**
- Настройте Docker registry cache
- Добавьте NFS/S3 для хранения бэкапов
- Оптимизируйте параметры ядра Ubuntu для production

### 🆘 Полезные команды для управления:

```bash
# Перезагрузка VPS
sudo reboot

# Проверка всех systemd сервисов
systemctl list-units --type=service --state=running

# Проверка использования ресурсов
htop
docker stats
df -h

# Обновление системы Ubuntu
sudo apt update && sudo apt upgrade -y

# Просмотр логов в реальном времени
sudo journalctl -f
```

### 📞 Поддержка:

Если возникли проблемы:
1. **Быстрые решения:** См. файл **UBUNTU_COMMON_ISSUES.md** (12 частых проблем с решениями)
2. **Проблемы с Docker:** См. файл **DOCKER_FIX.md**
3. **Проблемы с Git:** См. файл **GIT_SSH_SETUP.md** (SSH ключи, аутентификация)
4. **Детальная диагностика:** Раздел "🐛 Устранение проблем (Ubuntu)" в этом файле
5. **Логи:** `sudo journalctl -xe` или `sudo journalctl -u mcp-servers -f`
6. **Статус сервисов:** `sudo systemctl status mcp-servers`

### 📄 Дополнительные документы в этой папке:

- **VPS_SETUP_GUIDE.md** (этот файл) - Полное руководство по настройке VPS
- **UBUNTU_QUICK_SETUP.md** ⚡ - Быстрая настройка (45-60 мин) - команды copy-paste
- **UBUNTU_COMMON_ISSUES.md** ⭐ - Частые проблемы и их решения (12 проблем)
- **GIT_SSH_SETUP.md** 🔑 - Настройка Git SSH ключей для VPS (GitHub/GitLab)
- **DOCKER_FIX.md** - Быстрое исправление проблем с установкой Docker
- **DOCKER_QUICKSTART.md** - Быстрый старт с Docker
- **README.md** - Общее описание проекта

---

**Документация создана:** 2026-02-02
**Операционная система:** Ubuntu 24.04 LTS
**Версия:** 1.1 (Ubuntu Edition)

---

## 🔄 Шаг 11: Автозапуск MCP серверов через systemd (Ubuntu)

### 11.1 Создание systemd service файла

Ubuntu использует systemd для управления сервисами. Создадим service файл для автозапуска MCP серверов.

```bash
# Создаем systemd service файл
sudo nano /etc/systemd/system/mcp-servers.service
```

Вставляем следующее содержимое:

```ini
[Unit]
Description=MCP Servers (Docker + ADB)
After=network.target docker.service
Wants=docker.service
Documentation=https://github.com/your-repo

[Service]
Type=simple
User=android-builder
Group=android-builder
WorkingDirectory=/home/android-builder/ai-agent-project/mcp-servers

# Переменные окружения
Environment="PATH=/home/android-builder/ai-agent-project/mcp-servers/venv/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
Environment="ANDROID_HOME=/home/android-builder/android-sdk"
Environment="ANDROID_SDK_ROOT=/home/android-builder/android-sdk"
EnvironmentFile=/home/android-builder/ai-agent-project/mcp-servers/.env

# Команда запуска
ExecStart=/home/android-builder/ai-agent-project/mcp-servers/venv/bin/python launcher.py docker adb

# Настройки перезапуска
Restart=always
RestartSec=10

# Логирование
StandardOutput=journal
StandardError=journal
SyslogIdentifier=mcp-servers

[Install]
WantedBy=multi-user.target
```

### 11.2 Настройка и запуск сервиса

```bash
# Перезагружаем конфигурацию systemd
sudo systemctl daemon-reload

# Включаем автозапуск при старте Ubuntu
sudo systemctl enable mcp-servers.service

# Запускаем сервис
sudo systemctl start mcp-servers.service

# Проверяем статус
sudo systemctl status mcp-servers.service

# Просмотр логов
sudo journalctl -u mcp-servers.service -f

# Просмотр последних 50 строк логов
sudo journalctl -u mcp-servers.service -n 50
```

### 11.3 Управление сервисом

```bash
# Остановить сервис
sudo systemctl stop mcp-servers.service

# Перезапустить сервис
sudo systemctl restart mcp-servers.service

# Отключить автозапуск
sudo systemctl disable mcp-servers.service

# Проверить статус
sudo systemctl is-active mcp-servers.service
sudo systemctl is-enabled mcp-servers.service
```

### 11.4 Отладка проблем с systemd

```bash
# Проверяем синтаксис service файла
sudo systemd-analyze verify /etc/systemd/system/mcp-servers.service

# Полные логи с момента загрузки
sudo journalctl -u mcp-servers.service --since today

# Логи с определенного времени
sudo journalctl -u mcp-servers.service --since "2026-02-01 10:00:00"

# Логи в реальном времени (как tail -f)
sudo journalctl -u mcp-servers.service -f

# Проверяем зависимости сервиса
systemctl list-dependencies mcp-servers.service
```

---

## 🌐 Шаг 12: Настройка Nginx для безопасного доступа (Ubuntu)

### 12.1 Установка Nginx на Ubuntu

```bash
# Устанавливаем Nginx
sudo apt update
sudo apt install -y nginx

# Запускаем и включаем автозапуск
sudo systemctl start nginx
sudo systemctl enable nginx

# Проверяем статус
sudo systemctl status nginx

# Проверяем конфигурацию
sudo nginx -t
```

### 12.2 Настройка обратного прокси для MCP серверов

```bash
# Создаем конфигурацию для MCP серверов
sudo nano /etc/nginx/sites-available/mcp-servers
```

Вставляем конфигурацию:

```nginx
upstream mcp_docker {
    server 127.0.0.1:8006;
}

upstream mcp_adb {
    server 127.0.0.1:8007;
}

server {
    listen 80;
    server_name your-domain.com;  # Замените на ваш домен или IP

    # Ограничение доступа по IP (опционально)
    # allow YOUR_CLIENT_IP;
    # deny all;

    location /docker {
        proxy_pass http://mcp_docker;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;

        # SSE support
        proxy_buffering off;
        proxy_read_timeout 300s;
        proxy_connect_timeout 75s;
    }

    location /adb {
        proxy_pass http://mcp_adb;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;

        # SSE support
        proxy_buffering off;
        proxy_read_timeout 300s;
        proxy_connect_timeout 75s;
    }
}
```

Активируем конфигурацию:

```bash
# Создаем символическую ссылку
sudo ln -s /etc/nginx/sites-available/mcp-servers /etc/nginx/sites-enabled/

# Проверяем конфигурацию
sudo nginx -t

# Перезагружаем Nginx
sudo systemctl reload nginx
```

### 12.3 Установка Let's Encrypt SSL (опционально)

```bash
# Устанавливаем Certbot для Ubuntu
sudo apt install -y certbot python3-certbot-nginx

# Получаем SSL сертификат
sudo certbot --nginx -d your-domain.com

# Автоматическое обновление сертификата
sudo systemctl status certbot.timer
```

---

## 📊 Шаг 13: Оптимизация Ubuntu для production

### 13.1 Настройка swap файла

```bash
# Проверяем текущий swap
free -h
sudo swapon --show

# Создаем swap файл 4GB (если нет)
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# Делаем постоянным в Ubuntu
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Настраиваем swappiness (для SSD)
sudo sysctl vm.swappiness=10
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
```

### 13.2 Настройка лимитов файлов (Ubuntu)

```bash
# Увеличиваем лимиты для Ubuntu
sudo nano /etc/security/limits.conf
```

Добавляем:
```
* soft nofile 65536
* hard nofile 65536
* soft nproc 65536
* hard nproc 65536
```

Применяем:
```bash
# Перелогиниваемся или перезагружаемся
ulimit -n
# Должно быть: 65536
```

### 13.3 Настройка автоматических обновлений безопасности (Ubuntu)

```bash
# Устанавливаем unattended-upgrades
sudo apt install -y unattended-upgrades

# Настраиваем автообновления
sudo dpkg-reconfigure -plow unattended-upgrades

# Проверяем конфигурацию
cat /etc/apt/apt.conf.d/50unattended-upgrades
```

---

## 🎓 Полезные команды Ubuntu для администрирования VPS

### Управление сервисами (systemd)
```bash
# Список всех сервисов
systemctl list-units --type=service

# Автозапуск сервиса
sudo systemctl enable SERVICE_NAME

# Проверка логов
journalctl -xe
journalctl -u SERVICE_NAME -f
```

### Мониторинг ресурсов
```bash
# Использование CPU/RAM/Disk
htop
top

# Место на диске
df -h
du -sh /path/to/directory

# Сетевые подключения
sudo netstat -tulpn
sudo ss -tulpn
```

### Управление пакетами
```bash
# Поиск пакета
apt search PACKAGE_NAME

# Информация о пакете
apt show PACKAGE_NAME

# Установленные пакеты
apt list --installed

# Очистка кэша пакетов
sudo apt clean
sudo apt autoclean
```
