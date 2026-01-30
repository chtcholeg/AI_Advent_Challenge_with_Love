# Ubuntu 24.04 VPS Quick Setup

Быстрая настройка Ubuntu 24.04 VPS с учетом всех известных проблем.

---

## 🚀 Step 1: Первоначальная настройка (5 минут)

```bash
# Подключаемся к VPS
ssh root@YOUR_VPS_IP

# КРИТИЧНО: Включаем universe и multiverse репозитории СРАЗУ
sudo add-apt-repository universe -y
sudo add-apt-repository multiverse -y

# Обновляем систему
sudo apt update
sudo apt upgrade -y
sudo apt dist-upgrade -y
sudo apt autoremove -y

# Устанавливаем базовые инструменты
sudo apt install -y curl wget git vim nano htop screen tmux \
  build-essential software-properties-common \
  apt-transport-https ca-certificates gnupg lsb-release net-tools

# Проверяем версию
lsb_release -a
```

---

## 🔑 Step 2: Git SSH ключи (3 минуты)

```bash
# Генерируем SSH ключ для Git
ssh-keygen -t ed25519 -C "your_email@example.com"
# Нажмите Enter 3 раза

# Показываем публичный ключ
cat ~/.ssh/id_ed25519.pub

# ДЕЙСТВИЕ: Скопируйте вывод и добавьте на:
# - GitHub: https://github.com/settings/keys
# - GitLab: https://gitlab.com/-/profile/keys

# Тестируем (после добавления на GitHub)
ssh -T git@github.com

# Настраиваем Git
git config --global user.name "Your Name"
git config --global user.email "your_email@example.com"
```

**Важно:** Без SSH ключей вы не сможете работать с Git репозиториями (GitHub больше не поддерживает пароли).

---

## 🐳 Step 3: Docker (10 минут)

```bash
# Создаем необходимые директории
sudo mkdir -p /etc/apt/keyrings /etc/apt/sources.list.d
sudo chmod 755 /etc/apt/keyrings /etc/apt/sources.list.d

# Добавляем GPG ключ Docker
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Добавляем репозиторий Docker
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Устанавливаем Docker
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin

# Настраиваем права
sudo usermod -aG docker $USER
newgrp docker

# Проверяем
docker --version
docker compose version
docker run hello-world
```

---

## ☕ Step 4: Java (3 минуты)

```bash
# Обновляем список пакетов
sudo apt update

# Устанавливаем OpenJDK 17 ПО ОТДЕЛЬНОСТИ
sudo apt install openjdk-17-jdk
sudo apt install openjdk-17-jre

# Настраиваем JAVA_HOME
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# Проверяем
java -version
javac -version
echo $JAVA_HOME
```

---

## 📱 Step 5: Android SDK (15 минут)

```bash
# Создаем директорию
mkdir -p ~/android-sdk
cd ~/android-sdk

# Скачиваем Command Line Tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*_latest.zip
rm commandlinetools-linux-*_latest.zip

# Создаем правильную структуру
mkdir -p cmdline-tools/latest
mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true

# Настраиваем переменные окружения
cat >> ~/.bashrc << 'EOF'

# Android SDK
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/emulator
EOF

source ~/.bashrc

# Принимаем лицензии
yes | sdkmanager --licenses

# Устанавливаем компоненты
sdkmanager "platform-tools" \
  "platforms;android-34" \
  "build-tools;34.0.0" \
  "emulator" \
  "system-images;android-34;google_apis;x86_64"

# Проверяем
adb version
```

---

## 🖥️ Step 6: Android Emulator + KVM (10 минут)

```bash
# Проверяем поддержку KVM
egrep -c '(vmx|svm)' /proc/cpuinfo
# Если > 0, KVM поддерживается

# Устанавливаем KVM пакеты
sudo apt update
sudo apt install -y qemu-kvm libvirt-daemon-system libvirt-clients bridge-utils

# Загружаем модули
sudo modprobe kvm
sudo modprobe kvm_intel  # или kvm_amd для AMD

# Добавляем в автозагрузку
echo "kvm" | sudo tee -a /etc/modules
echo "kvm_intel" | sudo tee -a /etc/modules

# Настраиваем права
sudo usermod -aG kvm $USER
sudo usermod -aG libvirt $USER
newgrp kvm

# Запускаем libvirt
sudo systemctl enable libvirtd
sudo systemctl start libvirtd

# Проверяем
sudo kvm-ok
ls -la /dev/kvm

# Создаем AVD
avdmanager create avd \
  -n pixel6_api34 \
  -k "system-images;android-34;google_apis;x86_64" \
  -d "pixel_6" \
  --force

# Тестируем эмулятор (headless)
emulator -avd pixel6_api34 -no-window -no-audio -gpu swiftshader_indirect &
sleep 30
adb devices
adb -s emulator-5554 emu kill
```

---

## 🐍 Step 7: Python (5 минут)

```bash
# Проверяем версию (Ubuntu 24.04 имеет Python 3.12)
python3 --version

# Устанавливаем pip и venv
sudo apt update
sudo apt install -y python3-pip python3-venv python3-dev

# Создаем рабочую директорию
mkdir -p ~/ai-agent-project/mcp-servers
cd ~/ai-agent-project/mcp-servers

# Создаем виртуальное окружение
python3 -m venv venv
source venv/bin/activate

# Устанавливаем зависимости для MCP серверов
pip install --upgrade pip
pip install sse-starlette starlette uvicorn httpx python-dotenv docker

# Проверяем
which python
pip list
```

---

## 🔧 Step 8: MCP Серверы (зависит от вашего проекта)

```bash
cd ~/ai-agent-project/mcp-servers

# Создаем .env файл
cat > .env << 'EOF'
MCP_API_KEY=your_secret_key_here
DOCKER_HOST=unix:///var/run/docker.sock
ANDROID_HOME=/root/android-sdk
ANDROID_SDK_ROOT=/root/android-sdk
EOF

chmod 600 .env

# Запускаем серверы
source venv/bin/activate
python launcher.py docker adb --no-auth

# Или через screen для фоновой работы
screen -S mcp-servers
# Внутри screen:
cd ~/ai-agent-project/mcp-servers
source venv/bin/activate
python launcher.py docker adb --no-auth
# Ctrl+A, затем D для отключения
```

---

## 🔒 Step 9: Безопасность (5 минут)

```bash
# Настраиваем UFW
sudo apt install -y ufw
sudo ufw allow 22/tcp
sudo ufw allow from YOUR_CLIENT_IP to any port 8000:8010 proto tcp
sudo ufw enable
sudo ufw status verbose

# Генерируем API ключ
openssl rand -hex 32

# Обновляем .env с настоящим API ключом
nano ~/ai-agent-project/mcp-servers/.env

# Настраиваем swap (если нет)
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Настраиваем автообновления безопасности
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades
```

---

## ✅ Финальная проверка

```bash
# Docker работает
docker ps
docker compose version

# Android SDK установлен
which adb
adb version

# Эмулятор создан
avdmanager list avd

# Python окружение
cd ~/ai-agent-project/mcp-servers
source venv/bin/activate
which python
python --version

# KVM доступен
sudo kvm-ok
ls -la /dev/kvm

# Публичный IP
curl ifconfig.me
```

---

## 🎉 Готово!

**Время установки:** ~45-60 минут

**Что установлено:**
- ✅ Ubuntu 24.04 с universe/multiverse репозиториями
- ✅ Docker + Docker Compose
- ✅ Java (OpenJDK 17)
- ✅ Android SDK + ADB
- ✅ Android Emulator + KVM
- ✅ Python 3.12 + venv
- ✅ MCP серверы
- ✅ UFW firewall
- ✅ Swap файл

**Следующие шаги:**
1. Настройте systemd автозапуск для MCP серверов (см. VPS_SETUP_GUIDE.md, раздел 11)
2. Настройте Nginx + SSL (см. VPS_SETUP_GUIDE.md, раздел 12)
3. Подключите мобильное приложение к VPS

**Если возникли проблемы:**
- См. **UBUNTU_COMMON_ISSUES.md** (12 частых проблем с решениями)
- См. **VPS_SETUP_GUIDE.md** (полное руководство)

---

**Создано:** 2026-02-02
**Для:** Ubuntu 24.04 LTS
**Время выполнения:** 45-60 минут
