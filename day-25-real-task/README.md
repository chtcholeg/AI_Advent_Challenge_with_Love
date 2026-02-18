# Day 25 — Real Task: JuriLytics + VPS Manager

День 25 — переход от учебных задач к реальным проектам. За день реализованы два независимых приложения: **JuriLytics** (анализ юридических документов с помощью AI) и **VPS Manager** (веб-мастер настройки сервера).

---

## JuriLytics — анализатор юридических документов

Загружаете договор, оферту или ToS — получаете таблицу рисков на человеческом языке и можете задавать вопросы по документу в чате.

### Архитектура: мульти-агентный пайплайн

```
Загрузка файла (.txt / .pdf)
        ↓
  Классификатор
  (тип документа, стороны, список нужных агентов)
        ↓
┌──────────────────────────────────────────┐
│  Параллельные специализированные агенты  │
│  ├── IP/Права        → Верификатор       │
│  ├── Финансы         → Верификатор       │
│  ├── Обязательства   → Верификатор       │
│  ├── Пост-контракт   → Верификатор       │
│  └── Права потребит. → Верификатор       │
└──────────────────────────────────────────┘
        ↓
    Агрегатор
    (сводная таблица рисков)
        ↓
  Финальный верификатор
  (исключает галлюцинации)
        ↓
    Gap-checker
    (проверяет полноту анализа)
        ↓
  Markdown-таблица рисков
  + Q&A-сессия по документу
```

**Умная классификация:** классификатор автоматически определяет тип документа и выбирает только нужных агентов. Например, для договора с физлицом активируется агент по правам потребителя; для договора на дизайн — агент по IP-правам.

### Фаза 1: CLI-прототип (`prototype/`)

```bash
cd prototype
pip install -r requirements.txt
# Создать .env с GIGACHAT_AUTHORIZATION_KEY=...
python analyze.py sample_contract.txt
```

**Возможности:**
- Поддержка `.txt`-файлов
- Параллельный запуск агентов (IP/Права, Финансы, Обязательства, Пост-контракт)
- Верификация + агрегация результатов
- Q&A режим: вопросы по документу после анализа
- Красивый вывод через `rich`

### Фаза 2: Полное веб-приложение (`web-app/`)

```bash
cd web-app/backend
pip install -r requirements.txt
# Создать .env:
# GIGACHAT_AUTHORIZATION_KEY=...
# ADMIN_USERNAME=admin
# ADMIN_PASSWORD=yourpassword
uvicorn main:app --port 8001
# Открыть http://localhost:8001
```

**Возможности веб-приложения:**

- Загрузка `.txt` и `.pdf` файлов
- Прогресс анализа в реальном времени через SSE (Server-Sent Events)
- Выбор модели GigaChat (GigaChat / Plus / Pro / Max)
- Q&A чат по загруженному документу
- История анализов с возможностью повторного анализа
- Аутентификация с httpOnly-сессиями и ролями (user / admin)
- Rate limiting (5 попыток входа в минуту)
- Панель администратора: управление пользователями
- Автоочистка Q&A-сессий (TTL 2 часа)

**API-эндпоинты:**

| Метод | URL | Описание |
|-------|-----|----------|
| `POST` | `/api/login` | Вход (httpOnly cookie) |
| `POST` | `/api/logout` | Выход |
| `GET` | `/api/me` | Текущий пользователь |
| `POST` | `/api/analyze` | Анализ документа (SSE-стрим) |
| `POST` | `/api/ask` | Вопрос по документу |
| `GET` | `/api/history` | Список анализов |
| `GET` | `/api/history/{id}` | Детали анализа + Q&A сессия |
| `POST` | `/api/history/{id}/reanalyze` | Повторный анализ |
| `DELETE` | `/api/history/{id}` | Удалить запись |
| `GET` | `/api/admin/users` | Список пользователей (admin) |
| `POST` | `/api/admin/users` | Создать пользователя (admin) |
| `DELETE` | `/api/admin/users/{u}` | Удалить пользователя (admin) |

**Стек веб-приложения:**
- **Backend:** Python, FastAPI, GigaChat SDK, pdfplumber, slowapi
- **Frontend:** Vanilla JS, HTML/CSS (без фреймворков)
- **База данных:** SQLite (история + сессии авторизации)

---

## VPS Manager — мастер настройки сервера (`vps-manager/`)

Веб-интерфейс для пошаговой настройки VPS «с нуля». Подключаетесь по SSH прямо из браузера — мастер выполняет команды на сервере и показывает результат в реальном времени.

```bash
cd vps-manager
./start.sh
# Открыть http://localhost:8000
```

### Сценарии

**OpenVPN-сервер** — полная установка VPN:
1. Обновление пакетов системы
2. Создание sudo-пользователя
3. Настройка файрволла UFW
4. Установка Fail2ban
5. Установка OpenVPN + Easy-RSA
6. Инициализация PKI и создание CA
7. Генерация сертификатов сервера (DH + TLS)
8. Конфигурация OpenVPN
9. IP Forwarding + персистентный NAT через UFW
10. Открытие порта 1194/UDP + запуск сервиса
11. Генерация клиентского сертификата
12. Сборка `client1.ovpn` с встроенными сертификатами
13. **Скачивание `.ovpn`-файла** через SFTP прямо из браузера

**Python веб-приложение** — подготовка VPS:
1. Базовая настройка безопасности (UFW, Fail2ban, sudo-пользователь)
2. Установка Python 3 + venv
3. Nginx reverse proxy с настройкой
4. SSL-сертификат Let's Encrypt через Certbot
5. Systemd-сервис для автозапуска приложения
6. Инструкции по загрузке кода и управлению сервисом

### Особенности VPS Manager

- **WebSocket**: выполнение шагов и получение вывода команд в реальном времени
- **Подсказки при ошибках**: при сбое шага отображаются объяснения и кнопка «Применить исправление» (например, разблокировка dpkg, ожидание DNS)
- **Пропуск шагов**: некритичные шаги (Fail2ban, порты) можно пропустить
- **Запоминание подключения**: IP, логин и порт сохраняются между сессиями
- **Информационные блоки**: объяснения для новичков (что такое VPS, где купить, минимальные требования)
- **SFTP-скачивание**: файлы с сервера (`.ovpn`) загружаются через браузер

**API VPS Manager:**

| Метод | URL | Описание |
|-------|-----|----------|
| `GET` | `/api/last-connection` | Последние SSH-данные |
| `GET` | `/api/scenarios` | Доступные сценарии |
| `POST` | `/api/connect` | SSH-подключение |
| `WS` | `/ws/{session_id}` | Деплой-операции |
| `WS` | `/ws/exec/{session_id}` | Выполнение шагов мастера |
| `GET` | `/api/download/{session_id}` | Скачать файл с VPS (SFTP) |

**Стек:**
- **Backend:** Python, FastAPI, Paramiko (SSH/SFTP), WebSockets
- **Frontend:** Vanilla JS, HTML/CSS

---

## Структура проекта

```
day-25-real-task/
├── prototype/               # JuriLytics: CLI-прототип
│   ├── analyze.py           # Точка входа
│   ├── agent_runner.py      # Мульти-агентный пайплайн
│   ├── client.py            # GigaChat DocumentAnalyzer (Q&A)
│   ├── reader.py            # Чтение txt/pdf
│   ├── prompts.py           # Системный промпт
│   ├── agents/prompts/      # Промпты агентов
│   │   ├── classifier.txt
│   │   ├── ip_rights.txt
│   │   ├── financial.txt
│   │   ├── obligations.txt
│   │   ├── post_contract.txt
│   │   ├── consumer_rights.txt
│   │   ├── verifier.txt
│   │   ├── aggregator.txt
│   │   ├── final_verifier.txt
│   │   └── gap_checker.txt
│   ├── sample_contract.txt  # Тестовый договор
│   └── requirements.txt
├── web-app/                 # JuriLytics: полное веб-приложение
│   ├── backend/
│   │   ├── main.py          # FastAPI: все эндпоинты
│   │   ├── agent_runner.py  # Мульти-агентный пайплайн (расширенный)
│   │   ├── client.py        # GigaChat DocumentAnalyzer
│   │   ├── reader.py        # Чтение txt/pdf с лимитом
│   │   ├── auth.py          # Аутентификация, сессии, роли
│   │   ├── history.py       # SQLite: история анализов
│   │   ├── prompts.py       # Системный промпт
│   │   ├── agents/prompts/  # Промпты агентов (аналогично прототипу)
│   │   └── requirements.txt
│   └── frontend/
│       ├── index.html       # Главная страница
│       ├── login.html       # Страница входа
│       ├── app.js           # Логика: SSE, Q&A, история
│       └── style.css
├── vps-manager/             # VPS Manager
│   ├── backend/
│   │   ├── main.py          # FastAPI + WebSocket
│   │   ├── scenarios.py     # Сценарии: OpenVPN, Python webapp
│   │   └── requirements.txt
│   ├── frontend/
│   │   ├── index.html
│   │   ├── app.js
│   │   └── style.css
│   └── start.sh
├── IDEAS.md                 # Оценка идей (25 вариантов)
├── PLAN.md                  # План разработки JuriLytics
└── README.md
```

---

## Как запустить

### JuriLytics (веб)

```bash
cd web-app/backend
pip install -r requirements.txt

# Создать .env файл:
cat > .env << 'EOF'
GIGACHAT_AUTHORIZATION_KEY=ваш_ключ_из_кабинета_GigaChat
GIGACHAT_MODEL=GigaChat
ADMIN_USERNAME=admin
ADMIN_PASSWORD=секретный_пароль
EOF

uvicorn main:app --host 0.0.0.0 --port 8001
```

Открыть: **http://localhost:8001**

### JuriLytics (CLI-прототип)

```bash
cd prototype
pip install -r requirements.txt
echo "GIGACHAT_AUTHORIZATION_KEY=ваш_ключ" > .env
python analyze.py sample_contract.txt
```

### VPS Manager

```bash
cd vps-manager
./start.sh
```

Открыть: **http://localhost:8000**

---

## Что нового в Day 25

- **Реальный продукт**: не учебный пример, а приложение с практической ценностью
- **Мульти-агентная архитектура**: классификатор + специализированные агенты + верификаторы + агрегатор + gap-checker
- **Умная маршрутизация**: классификатор выбирает только нужных агентов под тип документа
- **SSE-стриминг**: прогресс анализа в реальном времени без опроса (polling)
- **Аутентификация**: httpOnly-сессии, роли, rate limiting, панель администратора
- **VPS Manager**: WebSocket-мастер с SSH-подключением прямо из браузера
- **SFTP-скачивание**: `.ovpn`-файл генерируется на сервере и загружается в браузер
- **Подсказки по ошибкам**: при сбое шага показывается объяснение и готовое исправление

## Videos

- https://disk.yandex.ru/i/coG-yqkfbNhdag