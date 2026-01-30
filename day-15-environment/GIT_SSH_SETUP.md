# Git SSH Setup для Ubuntu VPS

Быстрая настройка SSH ключей для работы с Git на VPS.

---

## ❌ Проблема

```
remote: Invalid username or token.
Password authentication is not supported for Git operations
fatal: Authentication failed
```

**Причина:** GitHub, GitLab и другие Git платформы больше не поддерживают аутентификацию по паролю с августа 2021 года.

**Решение:** Используйте SSH ключи или Personal Access Token.

---

## ✅ Решение 1: SSH ключи (рекомендуется)

### Шаг 1: Генерация SSH ключа на VPS

```bash
# Генерируем ключ (ed25519 - современный и безопасный)
ssh-keygen -t ed25519 -C "your_email@example.com"

# Нажимаете Enter 3 раза:
# - Enter file in which to save the key: [Enter]
# - Enter passphrase (empty for no passphrase): [Enter]
# - Enter same passphrase again: [Enter]

# Ключ создан в ~/.ssh/id_ed25519 (приватный)
# и ~/.ssh/id_ed25519.pub (публичный)
```

### Шаг 2: Копирование публичного ключа

```bash
# Показываем публичный ключ
cat ~/.ssh/id_ed25519.pub

# Скопируйте весь вывод (начинается с ssh-ed25519)
```

### Шаг 3: Добавление ключа на GitHub

1. Открываем браузер и идем на https://github.com/settings/keys
2. Нажимаем **"New SSH key"**
3. Title: `VPS Ubuntu` (любое описательное имя)
4. Key type: `Authentication Key`
5. Key: Вставляем скопированный публичный ключ
6. Нажимаем **"Add SSH key"**

### Шаг 4: Добавление ключа на GitLab (если используете)

1. Открываем https://gitlab.com/-/profile/keys
2. Нажимаем **"Add new key"**
3. Key: Вставляем скопированный публичный ключ
4. Title: `VPS Ubuntu`
5. Нажимаем **"Add key"**

### Шаг 5: Тестирование соединения

```bash
# Тестируем GitHub
ssh -T git@github.com
# Ожидаемый вывод:
# Hi USERNAME! You've successfully authenticated, but GitHub does not provide shell access.

# Тестируем GitLab
ssh -T git@gitlab.com
# Ожидаемый вывод:
# Welcome to GitLab, @USERNAME!
```

### Шаг 6: Настройка Git

```bash
# Настраиваем имя и email
git config --global user.name "Your Name"
git config --global user.email "your_email@example.com"

# Проверяем конфигурацию
git config --list
```

### Шаг 7: Использование SSH URL

```bash
# Для НОВЫХ репозиториев - клонируем через SSH
git clone git@github.com:USERNAME/REPO.git

# Для СУЩЕСТВУЮЩИХ репозиториев - меняем remote URL
cd /path/to/existing/repo
git remote set-url origin git@github.com:USERNAME/REPO.git

# Проверяем URL
git remote -v
# Должно быть:
# origin  git@github.com:USERNAME/REPO.git (fetch)
# origin  git@github.com:USERNAME/REPO.git (push)

# Теперь можно работать без пароля
git pull
git push
```

---

## ✅ Решение 2: Personal Access Token (альтернатива)

Если по какой-то причине SSH ключи не подходят, используйте Personal Access Token.

### Шаг 1: Создание токена на GitHub

1. Открываем https://github.com/settings/tokens
2. Нажимаем **"Generate new token"** → **"Generate new token (classic)"**
3. Note: `VPS Ubuntu` (описание)
4. Expiration: `90 days` или `No expiration` (для VPS можно без срока)
5. Select scopes: Отмечаем **`repo`** (полный доступ к репозиториям)
6. Нажимаем **"Generate token"**
7. **ВАЖНО:** Копируем токен - он больше не покажется!

### Шаг 2: Настройка credential helper

```bash
# Настраиваем Git для сохранения токена
git config --global credential.helper store

# При следующем git push вводим:
# Username: ваш_github_username
# Password: ваш_токен (НЕ пароль от аккаунта!)

# Токен сохранится в ~/.git-credentials
```

### Шаг 3: Использование токена

```bash
# Клонирование с токеном
git clone https://USERNAME:TOKEN@github.com/USERNAME/REPO.git

# Или для существующего репозитория
git remote set-url origin https://USERNAME:TOKEN@github.com/USERNAME/REPO.git

# Push
git push
# При первом push введите username и токен (если не использовали URL с токеном)
```

---

## 🔄 Сравнение SSH vs PAT

| Критерий | SSH ключи | Personal Access Token |
|----------|-----------|----------------------|
| Безопасность | ✅ Очень высокая | ⚠️ Средняя (токен может утечь) |
| Удобство | ✅ Не требует ввода | ⚠️ Нужно вводить/хранить |
| Срок действия | ✅ Бессрочно | ⚠️ Может истечь |
| Настройка | 🟡 5 минут | 🟡 3 минуты |
| Для VPS | ✅ Идеально | 🟢 Подходит |

**Рекомендация:** Используйте SSH ключи - это безопаснее и удобнее для серверов.

---

## 🐛 Частые проблемы

### Проблема 1: Permission denied (publickey)

```bash
# Ошибка:
# git@github.com: Permission denied (publickey)

# Решение 1: Проверьте что ключ добавлен на GitHub
ssh -T git@github.com

# Решение 2: Проверьте права на ключ
ls -la ~/.ssh/
chmod 600 ~/.ssh/id_ed25519
chmod 644 ~/.ssh/id_ed25519.pub

# Решение 3: Запустите ssh-agent
eval "$(ssh-agent -s)"
ssh-add ~/.ssh/id_ed25519
```

### Проблема 2: Host key verification failed

```bash
# Ошибка:
# Host key verification failed.

# Решение: Добавьте GitHub в known_hosts
ssh-keyscan github.com >> ~/.ssh/known_hosts

# Или удалите старую запись
ssh-keygen -R github.com
ssh -T git@github.com  # и нажмите yes
```

### Проблема 3: Уже использую HTTPS, как переключиться на SSH?

```bash
# Проверяем текущий URL
cd /path/to/repo
git remote -v

# Если видим https://github.com/... меняем на SSH
git remote set-url origin git@github.com:USERNAME/REPO.git

# Проверяем
git remote -v

# Теперь git push будет использовать SSH
```

---

## 📋 Шпаргалка команд

```bash
# Генерация SSH ключа
ssh-keygen -t ed25519 -C "email@example.com"

# Показать публичный ключ
cat ~/.ssh/id_ed25519.pub

# Тест соединения с GitHub
ssh -T git@github.com

# Настройка Git
git config --global user.name "Name"
git config --global user.email "email@example.com"

# Клонирование через SSH
git clone git@github.com:USER/REPO.git

# Смена URL на SSH
git remote set-url origin git@github.com:USER/REPO.git

# Проверка URL
git remote -v

# Сохранение токена (если используете HTTPS)
git config --global credential.helper store
```

---

## 🔒 Безопасность

### Защита приватного ключа

```bash
# Правильные права на ключи
chmod 700 ~/.ssh
chmod 600 ~/.ssh/id_ed25519
chmod 644 ~/.ssh/id_ed25519.pub
chmod 644 ~/.ssh/known_hosts

# Проверка
ls -la ~/.ssh/
```

### Использование passphrase (опционально)

Если вы хотите дополнительную защиту:

```bash
# Создаем ключ с passphrase
ssh-keygen -t ed25519 -C "email@example.com"
# При запросе passphrase вводим надежный пароль

# Добавляем в ssh-agent чтобы не вводить каждый раз
eval "$(ssh-agent -s)"
ssh-add ~/.ssh/id_ed25519
```

### Несколько SSH ключей

Если у вас несколько GitHub аккаунтов:

```bash
# Создаем файл конфигурации
nano ~/.ssh/config

# Добавляем:
# GitHub personal account
Host github.com
    HostName github.com
    User git
    IdentityFile ~/.ssh/id_ed25519

# GitHub work account
Host github-work
    HostName github.com
    User git
    IdentityFile ~/.ssh/id_ed25519_work

# Теперь можно клонировать
git clone git@github.com:personal/repo.git
git clone git@github-work:company/repo.git
```

---

## ✅ Проверка настройки

После настройки выполните:

```bash
# 1. Тест соединения
ssh -T git@github.com

# 2. Проверка Git конфигурации
git config --list | grep user

# 3. Проверка прав на ключи
ls -la ~/.ssh/

# 4. Тест клонирования (пример публичного репо)
git clone git@github.com:octocat/Hello-World.git /tmp/test-repo
rm -rf /tmp/test-repo  # удаляем тестовый репозиторий

# Если все прошло успешно - настройка завершена! ✅
```

---

## 📚 Дополнительные ресурсы

- [GitHub SSH Docs](https://docs.github.com/en/authentication/connecting-to-github-with-ssh)
- [GitLab SSH Docs](https://docs.gitlab.com/ee/user/ssh.html)
- [Generating a new SSH key](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/generating-a-new-ssh-key-and-adding-it-to-the-ssh-agent)

---

**Создано:** 2026-02-02
**Для:** Ubuntu 24.04 LTS VPS
**Время настройки:** 5-10 минут
