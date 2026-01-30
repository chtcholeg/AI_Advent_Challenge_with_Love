"""ADB MCP Server tool definitions."""

from shared.models import Tool, ToolParameter

# =============================================================================
# Tool Definitions
# =============================================================================

TOOLS = [
    Tool(
        name="restart_adb_server",
        description="""Перезапустить ADB сервер для исправления проблем подключения.

Используй когда:
- ADB не видит устройства
- Ошибки "no devices/emulators found"
- Проблемы с подключением после перезапуска эмулятора

НЕ используй для запуска эмулятора - для этого есть start_emulator.""",
        parameters=[],
        fewShotExamples=[
            {
                "request": "ADB не видит устройства",
                "params": {}
            },
            {
                "request": "Перезапусти ADB сервер",
                "params": {}
            },
            {
                "request": "Ошибка no devices found",
                "params": {}
            }
        ]
    ),
    Tool(
        name="list_devices",
        description="""Показать ТЕКСТОВЫЙ СПИСОК подключённых устройств и эмуляторов.

Возвращает JSON со списком: device_id (например emulator-5554), статус, модель.

ВАЖНО: Этот инструмент НЕ показывает изображение экрана!
Для скриншота/снимка экрана используй инструмент 'screenshot'.

НЕ показывает доступные для запуска AVD - для этого используй list_avds.""",
        parameters=[],
        fewShotExamples=[
            {
                "request": "Покажи подключенные устройства",
                "params": {}
            },
            {
                "request": "Какие Android девайсы сейчас работают?",
                "params": {}
            },
            {
                "request": "Есть ли запущенные эмуляторы?",
                "params": {}
            },
            {
                "request": "Список устройств",
                "params": {}
            }
        ]
    ),
    Tool(
        name="list_avds",
        description="""Показать список ДОСТУПНЫХ Android Virtual Devices (AVD), которые можно запустить.

Используй ПЕРЕД start_emulator чтобы узнать точные имена AVD.
Возвращает имена вида: pixel6_api34, Pixel_6_API_34, Nexus_5X_API_30 и т.д.

После получения имени AVD используй start_emulator для запуска.""",
        parameters=[],
        fewShotExamples=[
            {
                "request": "Покажи доступные AVD",
                "params": {}
            },
            {
                "request": "Какие эмуляторы можно запустить?",
                "params": {}
            },
            {
                "request": "Список виртуальных устройств",
                "params": {}
            }
        ]
    ),
    Tool(
        name="start_emulator",
        description="""ЗАПУСТИТЬ Android эмулятор по имени AVD.

ЭТО ЕДИНСТВЕННЫЙ СПОСОБ запустить эмулятор!
НЕ используй execute_adb для запуска эмулятора - это не сработает.

Параметры:
- avd_name: имя AVD из list_avds (например "pixel6_api34")
- no_window: true = без GUI (по умолчанию), false = с окном
- timeout: время ожидания загрузки в секундах

Эмулятор запускается и ждёт полной загрузки системы.""",
        parameters=[
            ToolParameter(
                name="avd_name",
                type="string",
                description="Имя AVD для запуска (получи из list_avds). Примеры: pixel6_api34, Pixel_6_API_34",
                required=True
            ),
            ToolParameter(
                name="no_window",
                type="boolean",
                description="Без окна/GUI (true по умолчанию). Установи false чтобы видеть экран эмулятора",
                required=False
            ),
            ToolParameter(
                name="no_audio",
                type="boolean",
                description="Отключить звук (true по умолчанию)",
                required=False
            ),
            ToolParameter(
                name="timeout",
                type="integer",
                description="Таймаут загрузки в секундах (180 по умолчанию)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "request": "Запусти эмулятор pixel6_api34",
                "params": {"avd_name": "pixel6_api34"}
            },
            {
                "request": "Стартани эмулятор Pixel_6_API_34 с окном",
                "params": {"avd_name": "Pixel_6_API_34", "no_window": False}
            },
            {
                "request": "Запусти Android эмулятор",
                "params": {"avd_name": "pixel6_api34"}
            },
            {
                "request": "emulator -avd pixel6_api34",
                "params": {"avd_name": "pixel6_api34"}
            },
            {
                "request": "Нужен эмулятор для тестирования",
                "params": {"avd_name": "pixel6_api34"}
            }
        ]
    ),
    Tool(
        name="stop_emulator",
        description="""ОСТАНОВИТЬ/ВЫКЛЮЧИТЬ работающий Android эмулятор.

⚠️ ВНИМАНИЕ: НЕ используй этот инструмент для:
- Установки APK (используй install_apk)
- Запуска приложения (используй launch_app)
- Сборки проекта (используй build_apk)

Используй ТОЛЬКО когда пользователь явно просит:
- "останови эмулятор", "выключи эмулятор", "закрой эмулятор"
- "shut down emulator", "stop emulator"

Если device_id не указан - остановит первый найденный эмулятор.""",
        parameters=[
            ToolParameter(
                name="device_id",
                type="string",
                description="ID устройства для остановки (например emulator-5554). Если не указан - первый эмулятор",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "request": "Останови эмулятор",
                "params": {}
            },
            {
                "request": "Выключи устройство emulator-5554",
                "params": {"device_id": "emulator-5554"}
            },
            {
                "request": "Закрой эмулятор",
                "params": {}
            }
        ],
        negativeFewShotExamples=[
            {
                "request": "Собери APK",
                "reason": "НЕ используй stop_emulator! Для сборки используй build_apk"
            },
            {
                "request": "Build APK",
                "reason": "НЕ используй stop_emulator! Для сборки используй build_apk"
            },
            {
                "request": "Установи приложение",
                "reason": "НЕ используй stop_emulator! Для установки используй install_apk"
            },
            {
                "request": "Запусти приложение",
                "reason": "НЕ используй stop_emulator! Для запуска приложения используй launch_app"
            }
        ]
    ),
    Tool(
        name="install_apk",
        description="""Установить APK файл на Android устройство или эмулятор.

Требования:
- Устройство/эмулятор должен быть запущен и подключён
- APK файл должен существовать по указанному пути

ВАЖНО: Возвращает package name в поле "package" - используй его для launch_app!
Не угадывай package name по названию файла или проекта.

Используй после build_apk для установки собранного приложения.""",
        parameters=[
            ToolParameter(
                name="apk_path",
                type="string",
                description="Полный путь к APK файлу. Пример: /Users/user/project/app/build/outputs/apk/debug/app-debug.apk",
                required=True
            ),
            ToolParameter(
                name="device_id",
                type="string",
                description="ID устройства (опционально). Если не указан - первое подключённое устройство",
                required=False
            ),
            ToolParameter(
                name="replace",
                type="boolean",
                description="Заменить существующее приложение (true по умолчанию)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "request": "Установи APK из /path/to/app.apk",
                "params": {"apk_path": "/path/to/app.apk"}
            },
            {
                "request": "Поставь приложение на эмулятор",
                "params": {"apk_path": "/path/to/app-debug.apk"}
            },
            {
                "request": "Установи собранный APK",
                "params": {"apk_path": "/project/composeApp/build/outputs/apk/debug/composeApp-debug.apk"}
            }
        ]
    ),
    Tool(
        name="screenshot",
        description="""СДЕЛАТЬ СКРИНШОТ - захватить изображение экрана Android устройства.

ИСПОЛЬЗУЙ ЭТОТ ИНСТРУМЕНТ когда пользователь просит:
- "скриншот", "screenshot", "снимок экрана"
- "покажи экран", "что на экране", "что показывает"
- "сфоткай экран", "захвати экран"
- проверить UI, посмотреть интерфейс

НЕ используй list_devices для просмотра экрана!
list_devices показывает СПИСОК устройств, а НЕ изображение экрана.

Возвращает:
- path: путь к сохранённому файлу PNG
- base64: изображение в формате base64 для просмотра
- format: "png"

Полезно для проверки UI и отладки.""",
        parameters=[
            ToolParameter(
                name="device_id",
                type="string",
                description="ID устройства (опционально, по умолчанию первое устройство)",
                required=False
            ),
            ToolParameter(
                name="output_path",
                type="string",
                description="Путь для сохранения скриншота (опционально, по умолчанию временный файл)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "request": "Сделай скриншот экрана",
                "params": {}
            },
            {
                "request": "Покажи что на экране эмулятора",
                "params": {}
            },
            {
                "request": "Сохрани скриншот в screenshot.png",
                "params": {"output_path": "screenshot.png"}
            },
            {
                "request": "Скриншот",
                "params": {}
            },
            {
                "request": "Что сейчас показывает экран?",
                "params": {}
            },
            {
                "request": "Снимок экрана устройства",
                "params": {}
            },
            {
                "request": "Покажи UI приложения",
                "params": {}
            }
        ]
    ),
    Tool(
        name="execute_adb",
        description="""Выполнить произвольную ADB команду на подключённом устройстве.

ВАЖНО: Это ТОЛЬКО для ADB команд! Команда выполняется как "adb <command>".

Допустимые команды:
- shell <команда> - выполнить shell команду на устройстве
- logcat -t N - показать N строк логов
- push/pull - копирование файлов
- forward/reverse - проброс портов

НЕЛЬЗЯ использовать для:
- Запуска эмулятора (используй start_emulator)
- Команд emulator, avdmanager
- Системных команд компьютера""",
        parameters=[
            ToolParameter(
                name="command",
                type="string",
                description="ADB команда БЕЗ префикса 'adb'. Примеры: 'shell ls /sdcard', 'logcat -t 50', 'shell input tap 100 200'",
                required=True
            ),
            ToolParameter(
                name="device_id",
                type="string",
                description="ID устройства (опционально, по умолчанию первое устройство)",
                required=False
            ),
            ToolParameter(
                name="timeout",
                type="integer",
                description="Таймаут команды в секундах (по умолчанию 30)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "request": "Покажи файлы в /sdcard",
                "params": {"command": "shell ls /sdcard"}
            },
            {
                "request": "Покажи последние 50 строк логов",
                "params": {"command": "logcat -t 50"}
            },
            {
                "request": "Нажми на экран в точке 500,800",
                "params": {"command": "shell input tap 500 800"}
            },
            {
                "request": "Нажми кнопку Home",
                "params": {"command": "shell input keyevent KEYCODE_HOME"}
            }
        ]
    ),
    Tool(
        name="get_device_info",
        description="""Получить подробную информацию об Android устройстве.

Возвращает: производитель, модель, версия Android, SDK, разрешение экрана, уровень батареи.""",
        parameters=[
            ToolParameter(
                name="device_id",
                type="string",
                description="ID устройства (опционально, по умолчанию первое устройство)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "request": "Покажи информацию об устройстве",
                "params": {}
            },
            {
                "request": "Какая модель и версия Android?",
                "params": {}
            },
            {
                "request": "Характеристики эмулятора",
                "params": {}
            }
        ]
    ),
    Tool(
        name="build_apk",
        description="""Собрать APK файл с помощью Gradle.

Выполняет ./gradlew assembleDebug или assembleRelease в директории проекта.
Автоматически определяет модуль (ищет: composeApp, app, androidApp).

Требования:
- project_path должен быть ПОЛНЫМ путём (не ~/ или относительным)
- В директории должен быть файл gradlew

После сборки используй install_apk для установки.""",
        parameters=[
            ToolParameter(
                name="project_path",
                type="string",
                description="ПОЛНЫЙ путь к корню Android проекта (где лежит gradlew). Пример: /Users/user/AndroidProjects/MyApp",
                required=True
            ),
            ToolParameter(
                name="build_type",
                type="string",
                description="Тип сборки: 'debug' или 'release' (по умолчанию debug)",
                required=False
            ),
            ToolParameter(
                name="module",
                type="string",
                description="Имя модуля - оставь пустым для автоопределения. Автопоиск: composeApp, app, androidApp",
                required=False
            ),
            ToolParameter(
                name="clean",
                type="boolean",
                description="Выполнить clean перед сборкой (по умолчанию false)",
                required=False
            ),
            ToolParameter(
                name="timeout",
                type="integer",
                description="Таймаут сборки в секундах (по умолчанию 600 = 10 минут)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "request": "Собери APK проекта",
                "params": {"project_path": "/Users/user/AndroidStudioProjects/MyApp"}
            },
            {
                "request": "Собери APK для модуля hello-world из ~/current-work-dir",
                "params": {"project_path": "/Users/user/current-work-dir", "module": "hello-world"}
            },
            {
                "request": "Build APK",
                "params": {"project_path": "/path/to/project"}
            },
            {
                "request": "Собери release APK",
                "params": {"project_path": "/path/to/project", "build_type": "release"}
            },
            {
                "request": "Сделай чистую сборку",
                "params": {"project_path": "/path/to/project", "clean": True}
            },
            {
                "request": "Скомпилируй приложение",
                "params": {"project_path": "/path/to/project"}
            },
            {
                "request": "assembleDebug",
                "params": {"project_path": "/path/to/project", "build_type": "debug"}
            }
        ]
    ),
    Tool(
        name="launch_app",
        description="""Запустить установленное приложение на устройстве.

ВАЖНО: Используй package из результата install_apk!
НЕ угадывай package по названию файла/проекта - это приведёт к ошибке.

Правильный workflow:
1. install_apk → получаешь {"package": "com.example.app", ...}
2. launch_app(package="com.example.app") → используешь package из шага 1

Автоматически определяет главную Activity если не указана.
Приложение должно быть уже установлено (используй install_apk).""",
        parameters=[
            ToolParameter(
                name="package",
                type="string",
                description="Имя пакета приложения. Примеры: ru.chtcholeg.app, com.example.myapp",
                required=True
            ),
            ToolParameter(
                name="activity",
                type="string",
                description="Activity для запуска (опционально, автоопределение если не указано)",
                required=False
            ),
            ToolParameter(
                name="device_id",
                type="string",
                description="ID устройства (опционально, по умолчанию первое устройство)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "request": "Запусти приложение ru.chtcholeg.app",
                "params": {"package": "ru.chtcholeg.app"}
            },
            {
                "request": "Открой установленное приложение",
                "params": {"package": "com.example.app"}
            },
            {
                "request": "Запусти MainActivity",
                "params": {"package": "ru.chtcholeg.app", "activity": ".MainActivity"}
            }
        ],
        negativeFewShotExamples=[
            {
                "request": "Установи и запусти hello-world-debug.apk",
                "reason": "НЕ угадывай package как 'hello-world'! Сначала вызови install_apk, получи package из результата, затем используй его в launch_app"
            },
            {
                "request": "Запусти приложение из проекта my-app",
                "reason": "Package name НЕ равен названию проекта. Используй package из результата install_apk"
            }
        ]
    ),
    Tool(
        name="dismiss_dialogs",
        description="""Закрыть системные диалоги (ANR, краши) и отключить будущие ANR попапы.

Используй когда:
- Эмулятор показывает "System UI isn't responding"
- Появляются диалоги "Приложение не отвечает"
- Нужно автоматически закрыть всплывающие окна""",
        parameters=[
            ToolParameter(
                name="device_id",
                type="string",
                description="ID устройства (опционально, по умолчанию первое устройство)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "request": "System UI isn't responding",
                "params": {}
            },
            {
                "request": "Закрой диалог ANR",
                "params": {}
            },
            {
                "request": "Приложение не отвечает - закрой",
                "params": {}
            },
            {
                "request": "Убери всплывающие окна на эмуляторе",
                "params": {}
            }
        ]
    ),
    Tool(
        name="get_app_logs",
        description="""Получить логи приложения (logcat) отфильтрованные по имени пакета.

Полезно для:
- Отладки крашей и ошибок
- Просмотра вывода приложения
- Поиска причин проблем

Возвращает также crash_logs если приложение упало.""",
        parameters=[
            ToolParameter(
                name="package",
                type="string",
                description="Имя пакета для фильтрации логов. Пример: ru.chtcholeg.app",
                required=True
            ),
            ToolParameter(
                name="lines",
                type="integer",
                description="Количество строк логов (по умолчанию 100)",
                required=False
            ),
            ToolParameter(
                name="level",
                type="string",
                description="Минимальный уровень логов: V(erbose), D(ebug), I(nfo), W(arning), E(rror), F(atal). По умолчанию D",
                required=False
            ),
            ToolParameter(
                name="device_id",
                type="string",
                description="ID устройства (опционально, по умолчанию первое устройство)",
                required=False
            )
        ],
        fewShotExamples=[
            {
                "request": "Покажи логи приложения ru.chtcholeg.app",
                "params": {"package": "ru.chtcholeg.app"}
            },
            {
                "request": "Почему приложение крашится?",
                "params": {"package": "ru.chtcholeg.app", "level": "E"}
            },
            {
                "request": "Покажи ошибки приложения",
                "params": {"package": "ru.chtcholeg.app", "level": "E", "lines": 200}
            },
            {
                "request": "Последние 50 строк логов",
                "params": {"package": "com.example.app", "lines": 50}
            }
        ]
    ),
]


def get_tools() -> list[Tool]:
    """Return list of all ADB tools."""
    return TOOLS


def get_tool_by_name(name: str) -> Tool | None:
    """Get tool by name."""
    for tool in TOOLS:
        if tool.name == name:
            return tool
    return None
