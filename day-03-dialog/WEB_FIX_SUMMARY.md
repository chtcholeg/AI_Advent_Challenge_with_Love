# Исправление проблемы с веб-версией

## Проблема
При запуске веб-версии открывалась страница с содержимым папки (directory listing) вместо самого приложения.

## Что было исправлено

### 1. Создан index.html
Создан файл `composeApp/src/wasmJsMain/resources/index.html` с базовой HTML структурой для приложения.

### 2. Обновлен main.kt
Изменен `composeApp/src/wasmJsMain/kotlin/ru/chtcholeg/app/main.kt` для использования `div` с `id="root"` вместо `document.body`.

### 3. Добавлены Gradle задачи
В `composeApp/build.gradle.kts` добавлены задачи для автоматического копирования `index.html` в выходную директорию:
- `copyWasmIndexHtml` - для development сборки
- `copyWasmIndexHtmlProduction` - для production сборки

## Как запустить веб-версию

### Development сервер (рекомендуется)
```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

После запуска сервер будет доступен по адресу:
- **http://localhost:8082**

Откройте этот адрес в браузере вручную.

### Что вы увидите в консоли
```
> Task :composeApp:wasmJsBrowserDevelopmentRun
<i> [webpack-dev-server] Project is running at:
<i> [webpack-dev-server] Loopback: http://localhost:8082/
webpack 5.94.0 compiled with 1 warning in 576 ms
```

## Проверка
1. Запустите команду выше
2. Откройте браузер и перейдите на http://localhost:8082
3. Должно открыться приложение GigaChat, а не список файлов

## Production сборка
```bash
# Создать production сборку
./gradlew :composeApp:wasmJsBrowserProductionWebpack

# Запустить локальный сервер для тестирования
cd composeApp/build/kotlin-webpack/wasmJs/productionExecutable
python3 -m http.server 8080
```

Затем откройте http://localhost:8080 в браузере.

## Важные файлы
- `composeApp/src/wasmJsMain/resources/index.html` - HTML точка входа
- `composeApp/src/wasmJsMain/kotlin/ru/chtcholeg/app/main.kt` - Kotlin точка входа
- `composeApp/build.gradle.kts` - Конфигурация сборки с задачами копирования

## Готово!
Теперь веб-версия должна открываться правильно и показывать приложение GigaChat вместо списка файлов.
