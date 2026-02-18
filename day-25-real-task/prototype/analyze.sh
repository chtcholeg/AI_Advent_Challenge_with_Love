#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
#  JuriLytics — мультиагентный анализ
#  Использование: ./analyze.sh <файл.txt>
# ─────────────────────────────────────────────────────────────
set -euo pipefail

# ── Цвета ─────────────────────────────────────────────────────
RED='\033[0;31m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
err()  { echo -e "${RED}Ошибка: $*${NC}" >&2; exit 1; }
warn() { echo -e "${YELLOW}⚠  $*${NC}"; }
info() { echo -e "${CYAN}▶  $*${NC}"; }

# ── Аргументы ─────────────────────────────────────────────────
[ $# -eq 0 ] && err "Укажи путь к .txt файлу.\n   Использование: $0 <файл.txt>"
FILE="$1"
[ ! -f "$FILE" ] && err "Файл не найден: $FILE"
[[ "$FILE" != *.txt ]] && err "Поддерживаются только .txt файлы"

# ── Переменные окружения ───────────────────────────────────────
[ -z "${GIGACHAT_AUTHORIZATION_KEY:-}" ] && err "Переменная GIGACHAT_AUTHORIZATION_KEY не задана.\n   Пример: GIGACHAT_AUTHORIZATION_KEY=xxx ./analyze.sh <файл.txt>"

# ── Виртуальное окружение ─────────────────────────────────────
VENV_DIR="$(dirname "$0")/.venv"
if [ ! -d "$VENV_DIR" ]; then
    info "Создаю виртуальное окружение..."
    python3 -m venv "$VENV_DIR"
fi
# shellcheck disable=SC1091
source "$VENV_DIR/bin/activate"

# ── Зависимости ───────────────────────────────────────────────
info "Проверяю зависимости..."
pip install -q gigachat rich

# ── Временный Python-файл ─────────────────────────────────────
TMPFILE=$(mktemp /tmp/doc_analyzer_XXXX.py)
trap 'rm -f "$TMPFILE"' EXIT

cat > "$TMPFILE" << 'PYTHON'
import argparse
import os
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from gigachat import GigaChat
from gigachat.models import Chat, Messages, MessagesRole
from rich.console import Console
from rich.markdown import Markdown
from rich.panel import Panel
from rich.prompt import Prompt

console = Console()
MAX_CHARS = 50_000
EXIT_COMMANDS = {"выход", "exit", "quit", "q", ":q"}

# ── Системный промпт ──────────────────────────────────────────

SYSTEM_PROMPT = """Ты опытный юридический помощник. Помогаешь обычным людям разобраться \
в юридических документах — договорах, офертах, пользовательских соглашениях.

Правила:
- Пиши простым языком, без юридического жаргона
- Честно указывай на невыгодные и опасные условия
- Если что-то неясно из текста — говори об этом прямо
- Отвечай на вопросы только по содержанию предоставленного документа
- Не придумывай условия, которых нет в документе

ТЕКСТ ДОКУМЕНТА:
{document}"""

# ── Промпты агентов (inline) ──────────────────────────────────

AGENT_PROMPTS = {
    "ip_rights": """Ты специалист по интеллектуальной собственности и правам на результаты работ.

Проанализируй ТОЛЬКО следующие аспекты документа:

1. **Тип лицензии**: исключительная или неисключительная? Если неисключительная — исполнитель может продавать тот же продукт другим клиентам.
2. **Срок лицензии**: бессрочная или ограниченная по времени?
3. **Портфолио**: может ли исполнитель показывать результаты в портфолио?
4. **Права на производные работы**: кто владеет улучшениями и доработками?
5. **Права на промежуточные материалы**: исходный код, макеты — кому принадлежат?
6. **Передача прав третьим лицам**: субподряд и передача прав.

Для каждого пункта: ссылка на раздел, в чём риск, критичность 🔴/🟡/🟢.
Если пункт отсутствует — напиши «Пункт X в документе не упомянут».""",

    "financial": """Ты специалист по финансовым условиям договоров.

Проанализируй ТОЛЬКО следующие аспекты документа:

1. **Сумма договора**: общая стоимость, валюта.
2. **График платежей**: предоплата, этапы, финальный платёж.
3. **Пени исполнителя**: штраф за просрочку (% в день от суммы).
4. **Пени заказчика**: штраф за задержку оплаты. Симметричны ли?
5. **Лимит ответственности**: максимальная сумма взыскания vs сумма договора.
6. **Штраф за расторжение по инициативе заказчика**: есть ли отдельный штраф (не путать с пенями), если именно заказчик решает расторгнуть договор досрочно? Какой процент от суммы? Симметричен ли аналогичный штраф для исполнителя?
7. **Возврат при расторжении**: как считаются «фактические расходы»?
8. **НДС и налоги**: кто платит?
9. **Индексация цены**: может ли цена измениться в одностороннем порядке?

Для каждого пункта: ссылка на раздел, конкретные цифры, критичность 🔴/🟡/🟢.
Если пункт отсутствует — напиши «Пункт X в документе не упомянут».""",

    "obligations": """Ты специалист по анализу обязательств и асимметрии прав в договорах.

Проанализируй ТОЛЬКО следующие аспекты документа:

1. **Асимметрия прав**: у одной стороны права без обязанностей, у другой — обязанности без прав?
2. **Одностороннее изменение условий**: может ли одна сторона менять сроки, объём, стоимость?
3. **Третьи лица**: ограничения на субподряд, требования согласования.
4. **Сроки**: кто устанавливает? Кто несёт ответственность за задержку?
5. **Приёмка работ**: порядок, сроки, что если заказчик молчит.
6. **Расторжение**: кто может и в каких случаях? Штрафы за досрочное расторжение?
7. **Форс-мажор**: как определён, кто несёт риски?

Для каждого пункта: ссылка на раздел, в чём асимметрия/риск, критичность 🔴/🟡/🟢.
Если пункт отсутствует — напиши «Пункт X в документе не упомянут».""",

    "post_contract": """Ты специалист по постконтрактным ограничениям.

Проанализируй ТОЛЬКО следующие аспекты документа:

1. **Non-hire**: запрет найма сотрудников другой стороны после окончания договора?
2. **Non-compete**: запрет работы с конкурентами после окончания?
3. **NDA**: срок, что считается конфиденциальным, ответственность.
4. **Отзывы/упоминания**: запрет публично говорить о сотрудничестве?
5. **Юрисдикция**: в каком суде/городе рассматриваются споры? Удобно ли это? ОБЯЗАТЕЛЬНО проверь: кто является Заказчиком — юридическое лицо, ИП или физическое лицо («гражданин»)? Арбитражный суд рассматривает дела только между организациями и ИП. Если Заказчик — физическое лицо (гражданин), указание Арбитражного суда делает судебную защиту для него практически недоступной — это критическая ошибка в подсудности, ставь 🔴 Высокая.
6. **Применимое право**: какое законодательство?
7. **Гарантия**: срок и условия после сдачи работ.
8. **Пролонгация**: автоматическое продление договора?

Для каждого пункта: ссылка на раздел, в чём риск/ограничение, критичность 🔴/🟡/🟢.
Если пункт отсутствует — напиши «Пункт X в документе не упомянут».""",
}

AGENT_LABELS = {
    "ip_rights": "IP/Права",
    "financial": "Финансы",
    "obligations": "Обязательства",
    "post_contract": "Пост-контракт",
}

VERIFIER_TMPL = """Ты верификатор юридического анализа. Проверь анализ по области «{domain}» на галлюцинации.

ВОТ АНАЛИЗ ДЛЯ ПРОВЕРКИ:
{analysis}

ИНСТРУКЦИИ:
1. Для каждого утверждения проверь: есть ли это буквально в тексте документа?
2. Удали всё, чего нет в документе. Сохрани всё подтверждённое текстом.
3. Пункты «в документе не упомянут» — оставь как есть.

Верни исправленный анализ в том же формате. Если всё корректно — верни без изменений."""

AGGREGATOR_TMPL = """На основе результатов четырёх агентов составь итоговую сводную таблицу рисков.

РЕЗУЛЬТАТЫ АГЕНТОВ:
{results}

ИНСТРУКЦИИ:
1. Собери все найденные риски из всех четырёх разделов.
2. Исключи только пункты «не упомянут в документе» и пункты 🟢 Норма.
3. Отсортируй: сначала 🔴 Высокая, затем 🟡 Средняя.

Выведи в формате:

## О документе
2-3 предложения: что это за документ, между кем и кем, о чём. Только то, что есть в тексте.

## Итоговый анализ документа

| Пункт | Область | Проблема | Критичность |
|-------|---------|----------|-------------|

После таблицы добавь:

## Вопросы перед подписанием
Список из 3-5 конкретных вопросов к другой стороне."""

FINAL_VERIFIER_TMPL = """Проверь итоговую таблицу анализа на галлюцинации.

ТАБЛИЦА ДЛЯ ПРОВЕРКИ:
{table}

ИНСТРУКЦИИ:
1. Для каждой строки проверь: подтверждается ли проблема текстом документа?
2. Удали неподтверждённые строки, исправь неточные формулировки.
3. Сохрани структуру таблицы и раздел «Вопросы перед подписанием».

Верни финальную проверенную таблицу в том же markdown-формате."""

# ── Вспомогательные функции ───────────────────────────────────

def _call(credentials, model, system, user):
    giga = GigaChat(credentials=credentials, model=model, verify_ssl_certs=False)
    response = giga.chat(Chat(messages=[
        Messages(role=MessagesRole.SYSTEM, content=system),
        Messages(role=MessagesRole.USER, content=user),
    ]))
    return response.choices[0].message.content


def _run_agent(credentials, model, document, agent_name):
    system = SYSTEM_PROMPT.format(document=document)
    analysis = _call(credentials, model, system, AGENT_PROMPTS[agent_name])
    verified = _call(credentials, model, system,
                     VERIFIER_TMPL.format(domain=AGENT_LABELS[agent_name], analysis=analysis))
    return agent_name, verified


def run_all_agents(credentials, model, document):
    with ThreadPoolExecutor(max_workers=4) as executor:
        futures = {
            executor.submit(_run_agent, credentials, model, document, name): name
            for name in AGENT_PROMPTS
        }
        results = {}
        for future in as_completed(futures):
            name, verified = future.result()
            results[name] = verified
    return results


def aggregate(credentials, model, document, results):
    system = SYSTEM_PROMPT.format(document=document)
    combined = "\n\n".join(
        f"=== {AGENT_LABELS[k]} ===\n{v}" for k, v in results.items()
    )
    table = _call(credentials, model, system,
                  AGGREGATOR_TMPL.format(results=combined))
    final = _call(credentials, model, system,
                  FINAL_VERIFIER_TMPL.format(table=table))
    return final

# ── GigaChat Q&A клиент ───────────────────────────────────────

class DocumentAnalyzer:
    def __init__(self, credentials, model, document):
        self._giga = GigaChat(credentials=credentials, model=model, verify_ssl_certs=False)
        self._history = [
            Messages(role=MessagesRole.SYSTEM, content=SYSTEM_PROMPT.format(document=document))
        ]

    def ask(self, question):
        self._history.append(Messages(role=MessagesRole.USER, content=question))
        response = self._giga.chat(Chat(messages=self._history))
        answer = response.choices[0].message.content
        self._history.append(Messages(role=MessagesRole.ASSISTANT, content=answer))
        return answer

# ── Точка входа ───────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("file")
    args = parser.parse_args()

    path = Path(args.file)
    text = path.read_text(encoding="utf-8")
    truncated = len(text) > MAX_CHARS
    if truncated:
        text = text[:MAX_CHARS]
        console.print("[yellow]⚠  Документ большой — загружены первые 50 000 символов[/yellow]\n")

    credentials = os.getenv("GIGACHAT_AUTHORIZATION_KEY")
    model = os.getenv("GIGACHAT_MODEL", "GigaChat")

    if not credentials:
        console.print("[red]Переменная GIGACHAT_AUTHORIZATION_KEY не задана[/red]")
        sys.exit(1)

    console.print(Panel(
        f"[bold]Документ загружен[/bold] · {len(text):,} символов · модель {model}",
        style="blue",
    ))

    with console.status("[dim]Запускаю параллельных агентов: IP/Права, Финансы, Обязательства, Пост-контракт...[/dim]"):
        results = run_all_agents(credentials, model, text)

    with console.status("[dim]Агрегирую результаты и проверяю на галлюцинации...[/dim]"):
        table = aggregate(credentials, model, text, results)

    console.print(Markdown(table))

    analyzer = DocumentAnalyzer(credentials=credentials, model=model, document=text)

    console.print("\n[dim]Задавайте вопросы по документу. Для выхода — 'выход'[/dim]\n")

    while True:
        try:
            question = Prompt.ask("[bold cyan]Вопрос[/bold cyan]")
        except (KeyboardInterrupt, EOFError):
            console.print("\n[dim]Пока![/dim]")
            break

        if question.strip().lower() in EXIT_COMMANDS:
            console.print("[dim]Пока![/dim]")
            break

        if not question.strip():
            continue

        with console.status("[dim]Думаю...[/dim]"):
            answer = analyzer.ask(question)

        console.print(Markdown(answer))
        console.print()

if __name__ == "__main__":
    main()
PYTHON

# ── Запуск ────────────────────────────────────────────────────
python "$TMPFILE" "$FILE"
