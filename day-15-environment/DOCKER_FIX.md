# Быстрое исправление ошибки установки Docker на Ubuntu

## Проблема
При выполнении команды добавления Docker репозитория получаете ошибку:
```
tee: /etc/apt/sources.list.d/docker.list: No such file or directory
```

## Причина
Директория `/etc/apt/sources.list.d/` не существует на минимальных установках Ubuntu.

## Быстрое решение

Выполните следующие команды по порядку:

```bash
# 1. Создайте необходимые директории
sudo mkdir -p /etc/apt/keyrings
sudo mkdir -p /etc/apt/sources.list.d
sudo chmod 755 /etc/apt/keyrings
sudo chmod 755 /etc/apt/sources.list.d

# 2. Проверьте что директории созданы
ls -la /etc/apt/ | grep sources.list.d

# 3. Добавьте GPG ключ Docker
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 4. Проверьте версию Ubuntu
. /etc/os-release
echo "Ubuntu version: $VERSION_CODENAME"
echo "Architecture: $(dpkg --print-architecture)"

# 5. Добавьте репозиторий Docker
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 6. Проверьте что файл создан
cat /etc/apt/sources.list.d/docker.list

# 7. Продолжите установку Docker
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 8. Проверьте установку
docker --version
docker compose version
```

## Проверка результата

После выполнения команд вы должны увидеть:
```bash
$ docker --version
Docker version 24.0.x, build xxxxx

$ docker compose version
Docker Compose version v2.x.x
```

## Следующие шаги

После успешной установки продолжите с **Шага 2.2** в файле `VPS_SETUP_GUIDE.md`:
- Настройка прав для текущего пользователя
- Настройка Docker daemon

---

**Примечание:** Эта проблема возникает только на минимальных установках Ubuntu Server без предустановленных apt директорий.
