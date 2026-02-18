from pathlib import Path

MAX_CHARS = 50_000


def read_txt(path: Path) -> tuple[str, bool]:
    """Читает .txt файл. Возвращает (текст, был_ли_обрезан)."""
    text = path.read_text(encoding="utf-8")
    if len(text) > MAX_CHARS:
        return text[:MAX_CHARS], True
    return text, False
