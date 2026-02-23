# Local Log Analyst

Локальное веб-приложение для анализа логов с помощью LLM (Ollama).
Загружаешь CSV, JSON, SQLite или текстовый лог — задаёшь вопросы на естественном языке.

## Требования

- Python 3.11+
- Node.js 18+
- [Ollama](https://ollama.ai) запущен локально (`ollama serve`)

## Быстрый старт

### 1. Запустить Ollama и скачать модель

```bash
ollama serve
ollama pull llama3.1:8b   # или qwen2.5:7b, deepseek-r1:8b и т.д.
```

### 2. Backend

```bash
cd backend
python -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
pip install -r requirements.txt

cp ../.env.example .env         # настрой при необходимости

python run.py
# Backend запустится на http://localhost:8000
```

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
# UI доступен на http://localhost:5173
```

### 4. Открыть браузер

[http://localhost:5173](http://localhost:5173)

## Поддерживаемые форматы

| Формат | Расширения |
|--------|-----------|
| CSV | `.csv` |
| JSON / JSON Lines | `.json`, `.jsonl` |
| Plain text / log | `.txt`, `.log` |
| SQLite | `.db`, `.sqlite` |

## Настройки

- Нажать **⚙ Settings** в сайдбаре
- Указать название модели Ollama (напр. `qwen2.5:7b`)
- Опционально изменить URL Ollama

## Структура проекта

```
day-30-local-analyst/
├── backend/          # FastAPI
│   ├── app/
│   │   ├── main.py
│   │   ├── config.py
│   │   ├── db/          # SQLite persistence
│   │   ├── models/      # Pydantic schemas
│   │   ├── routers/     # API endpoints
│   │   └── services/    # Business logic
│   └── run.py
├── frontend/         # React + TypeScript
│   └── src/
│       ├── api/         # API client
│       ├── components/  # UI компоненты
│       ├── pages/       # Страницы
│       └── types/       # TypeScript типы
├── fetching-reviews/ # CLI-скрейпер отзывов (Google Play + RuStore)
│   ├── main.py       # fetch / stats команды
│   ├── db.py         # SQLite: сохранение отзывов
│   └── scrapers/     # google_play, rustore (API), rustore_web (Playwright)
├── SPEC.md           # Спецификация
├── PLAN.md           # План разработки
└── .env.example
```

## Утилита для сбора отзывов

`fetching-reviews/` — отдельный CLI-инструмент для скачивания реальных отзывов из магазинов приложений в SQLite. Готовый `.db`-файл можно сразу загрузить в Local Analyst и задавать вопросы.

```bash
cd fetching-reviews
pip install -r requirements.txt

# Google Play — без авторизации
python main.py fetch --store google_play --app com.example.app --count 500

# RuStore через веб-скрейпинг (Playwright, любое приложение)
python main.py fetch --store rustore_web --app ru.example.app --count 200

# Статистика собранного
python main.py stats
```

## Переменные окружения (backend/.env)

| Переменная | По умолчанию | Описание |
|-----------|-------------|---------|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | URL Ollama |
| `OLLAMA_MODEL` | `llama3.1:8b` | Модель по умолчанию |
| `MAX_UPLOAD_SIZE_MB` | `1000` | Лимит загрузки (MB) |
| `UPLOAD_DIR` | `./uploads` | Папка для файлов |
| `APP_DB_PATH` | `./data/app.db` | SQLite база приложения |


## Видео

- https://disk.yandex.ru/i/XZs4oONS_V2vKg