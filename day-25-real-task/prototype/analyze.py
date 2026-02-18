#!/usr/bin/env python3
"""
Анализ юридических документов через GigaChat.

Использование:
    python analyze.py <путь_к_файлу.txt>

Пример:
    python analyze.py sample_contract.txt
"""

import argparse
import os
import sys
from pathlib import Path

from dotenv import load_dotenv
from rich.console import Console
from rich.markdown import Markdown
from rich.panel import Panel
from rich.prompt import Prompt

from agent_runner import aggregate, run_all_agents
from client import DocumentAnalyzer
from reader import read_txt

load_dotenv()

console = Console()

EXIT_COMMANDS = {"выход", "exit", "quit", "q", ":q"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Анализ юридических документов через GigaChat"
    )
    parser.add_argument("file", help="Путь к .txt файлу с документом")
    return parser.parse_args()


def load_document(file_arg: str) -> str:
    path = Path(file_arg)

    if not path.exists():
        console.print(f"[red]Файл не найден: {path}[/red]")
        sys.exit(1)

    if path.suffix.lower() != ".txt":
        console.print("[red]Поддерживаются только .txt файлы[/red]")
        sys.exit(1)

    text, truncated = read_txt(path)

    if not text.strip():
        console.print("[red]Файл пустой[/red]")
        sys.exit(1)

    if truncated:
        console.print(
            "[yellow]⚠  Документ большой — загружены первые 50 000 символов[/yellow]\n"
        )

    return text


def load_credentials() -> tuple[str, str]:
    credentials = os.getenv("GIGACHAT_AUTHORIZATION_KEY")
    model = os.getenv("GIGACHAT_MODEL", "GigaChat")

    if not credentials:
        console.print(
            "[red]Не найден GIGACHAT_AUTHORIZATION_KEY.[/red]\n"
            "Скопируй .env.example → .env и заполни ключ."
        )
        sys.exit(1)

    return credentials, model


def run_qa_loop(analyzer: DocumentAnalyzer) -> None:
    console.print(
        "\n[dim]Можно задавать вопросы по документу. "
        "Для выхода введите 'выход'[/dim]\n"
    )

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


def main() -> None:
    args = parse_args()
    document = load_document(args.file)
    credentials, model = load_credentials()

    console.print(
        Panel(
            f"[bold]Документ загружен[/bold] · {len(document):,} символов · модель {model}",
            style="blue",
        )
    )

    with console.status(
        "[dim]Запускаю параллельных агентов: IP/Права, Финансы, Обязательства, Пост-контракт...[/dim]"
    ):
        results = run_all_agents(credentials, model, document)

    with console.status("[dim]Агрегирую результаты и проверяю на галлюцинации...[/dim]"):
        table = aggregate(credentials, model, document, results)

    console.print(Markdown(table))

    analyzer = DocumentAnalyzer(
        credentials=credentials,
        model=model,
        document=document,
    )

    run_qa_loop(analyzer)


if __name__ == "__main__":
    main()
