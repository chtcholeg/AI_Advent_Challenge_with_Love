import io
from pathlib import Path

MAX_CHARS = 150_000


def read_file(filename: str, content: bytes) -> tuple[str, bool]:
    """Читает содержимое загруженного файла. Поддерживает .txt и .pdf."""
    ext = Path(filename).suffix.lower()

    if ext == ".txt":
        text = content.decode("utf-8", errors="replace")
    elif ext == ".pdf":
        text = _read_pdf_bytes(content)
    else:
        raise ValueError(f"Неподдерживаемый формат: {ext}. Загрузите .txt или .pdf")

    if len(text) > MAX_CHARS:
        return text[:MAX_CHARS], True
    return text, False


def _read_pdf_bytes(content: bytes) -> str:
    import pdfplumber

    text_parts = []
    with pdfplumber.open(io.BytesIO(content)) as pdf:
        for page in pdf.pages:
            page_text = page.extract_text()
            if page_text:
                text_parts.append(page_text)
    return "\n".join(text_parts)
