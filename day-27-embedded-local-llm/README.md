# Day 27 — Embedded Local LLM (Android)

Нативное Android-приложение, которое запускает языковую модель **Gemma 2 2B** прямо на устройстве — без интернета и без облачных серверов. Часть серии AI Advent Challenge.

## Что делает приложение

- Чат с ИИ, работающий полностью офлайн
- Токен-за-токеном стриминг ответов модели в реальном времени
- История переписки в сессии
- Состояния загрузки, ошибок и отсутствия модели

## Технологии

| Категория | Технология |
|---|---|
| Язык | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material Design 3 |
| LLM Runtime | MediaPipe Tasks GenAI 0.10.14 |
| Модель | Google Gemma 2 2B IT (INT4, CPU) |
| Архитектура | MVVM + StateFlow + Coroutines |
| Min SDK | 26 (Android 8.0) |

## Структура проекта

```
app/src/main/java/com/example/localllmchat/
├── MainActivity.kt          — точка входа
├── data/
│   └── Message.kt           — модель сообщения
└── ui/
    ├── ChatViewModel.kt     — логика LLM и состояние чата
    ├── ChatScreen.kt        — весь UI чата
    └── theme/               — цвета, типографика, тема
```

## Параметры модели

```
Max tokens:  1024
Top-K:       40
Temperature: 0.8
Seed:        101
```

Формат промпта — стандартный Gemma 2 instruction format с маркерами `<start_of_turn>`.

## Добавление модели на телефон

Модель не входит в APK — её нужно загрузить и передать на устройство вручную.

### 1. Скачать модель

Открыть страницу на Kaggle:

```
https://www.kaggle.com/models/google/gemma/tfLite
```

Выбрать вариант: **gemma-2b-it-cpu-int4**

Скачать архив и разархивировать при необходимости.

### 2. Переименовать файл

```bash
mv <скачанный_файл> model.bin
```

### 3. Передать на устройство через ADB

```bash
adb push model.bin /sdcard/Android/data/com.example.localllmchat/files/model.bin
```

> Убедитесь, что приложение установлено на устройстве до выполнения `adb push` — иначе директория не существует.

### 4. Запустить приложение

При первом запуске модель инициализируется 10–30 секунд в зависимости от устройства.

## Сборка

```bash
./gradlew assembleDebug
```

Или открыть проект в Android Studio и запустить через `Run`.

## Пакет

```
com.example.localllmchat
```

## Видео

- https://disk.yandex.ru/i/fGS3OPQkEl3nvg