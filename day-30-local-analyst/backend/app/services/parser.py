"""
Парсер входных файлов логов.
Поддерживаемые форматы: CSV, JSON, JSON Lines, plain text (.log/.txt), SQLite (.db/.sqlite)
Стратегия для больших файлов: чтение чанками, не загружать всё в память.
"""
import json
import sqlite3
import shutil
from pathlib import Path
from typing import Optional

import chardet
import pandas as pd


ALLOWED_EXTENSIONS = {".csv", ".json", ".jsonl", ".log", ".txt", ".db", ".sqlite"}
# Порог для больших файлов (байт)
LARGE_FILE_THRESHOLD = 50 * 1024 * 1024  # 50 MB


def detect_encoding(path: Path, sample_bytes: int = 65536) -> str:
    with open(path, "rb") as f:
        raw = f.read(sample_bytes)
    # UTF-8 BOM
    if raw.startswith(b"\xef\xbb\xbf"):
        return "utf-8-sig"
    # Проверяем, является ли файл валидным UTF-8
    try:
        raw.decode("utf-8")
        return "utf-8"
    except UnicodeDecodeError:
        pass
    # Fallback: chardet
    result = chardet.detect(raw)
    return result.get("encoding") or "utf-8"


def get_format(filename: str) -> str:
    ext = Path(filename).suffix.lower()
    if ext == ".csv":
        return "csv"
    if ext in (".json", ".jsonl"):
        return "json"
    if ext in (".log", ".txt"):
        return "text"
    if ext in (".db", ".sqlite"):
        return "sqlite"
    raise ValueError(f"Unsupported file extension: {ext}")


def load_to_sqlite(
    source_path: Path,
    dest_db_path: Path,
    table_name: str = "logs",
) -> int:
    """
    Загружает данные из source_path в SQLite-базу dest_db_path.
    Возвращает количество строк.
    """
    fmt = get_format(source_path.name)

    if fmt == "sqlite":
        return _load_sqlite_to_sqlite(source_path, dest_db_path, table_name)
    elif fmt == "csv":
        return _load_csv_to_sqlite(source_path, dest_db_path, table_name)
    elif fmt == "json":
        return _load_json_to_sqlite(source_path, dest_db_path, table_name)
    elif fmt == "text":
        return _load_text_to_sqlite(source_path, dest_db_path, table_name)
    raise ValueError(f"Unknown format: {fmt}")


def _load_csv_to_sqlite(source: Path, dest: Path, table: str) -> int:
    encoding = detect_encoding(source)
    file_size = source.stat().st_size
    row_count = 0

    con = sqlite3.connect(str(dest))
    try:
        chunksize = 10_000 if file_size > LARGE_FILE_THRESHOLD else None
        reader = pd.read_csv(
            source,
            encoding=encoding,
            encoding_errors="replace",
            chunksize=chunksize,
            low_memory=False,
        )
        if chunksize:
            first = True
            for chunk in reader:
                chunk.columns = [_sanitize_col(c) for c in chunk.columns]
                chunk.to_sql(table, con, if_exists="replace" if first else "append", index=False)
                row_count += len(chunk)
                first = False
        else:
            df = reader
            df.columns = [_sanitize_col(c) for c in df.columns]
            df.to_sql(table, con, if_exists="replace", index=False)
            row_count = len(df)
    finally:
        con.close()
    return row_count


def _load_json_to_sqlite(source: Path, dest: Path, table: str) -> int:
    encoding = detect_encoding(source)
    with open(source, encoding=encoding, errors="replace") as f:
        first_char = f.read(1)

    with open(source, encoding=encoding, errors="replace") as f:
        content = f.read()

    # JSON Lines: каждая строка — объект
    if first_char == "{":
        records = []
        for line in content.splitlines():
            line = line.strip()
            if line:
                try:
                    records.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
    else:
        data = json.loads(content)
        if isinstance(data, list):
            records = data
        elif isinstance(data, dict):
            # Попытка найти первый list-value
            records = next(
                (v for v in data.values() if isinstance(v, list)), [data]
            )
        else:
            records = [{"value": data}]

    df = pd.json_normalize(records)
    df.columns = [_sanitize_col(c) for c in df.columns]
    con = sqlite3.connect(str(dest))
    try:
        df.to_sql(table, con, if_exists="replace", index=False)
    finally:
        con.close()
    return len(df)


def _load_text_to_sqlite(source: Path, dest: Path, table: str) -> int:
    encoding = detect_encoding(source)
    lines = []
    with open(source, encoding=encoding, errors="replace") as f:
        for i, line in enumerate(f):
            stripped = line.rstrip("\n\r")
            if stripped:
                lines.append({"line_number": i + 1, "raw": stripped})

    df = pd.DataFrame(lines)
    con = sqlite3.connect(str(dest))
    try:
        df.to_sql(table, con, if_exists="replace", index=False)
    finally:
        con.close()
    return len(df)


def _load_sqlite_to_sqlite(source: Path, dest: Path, table: str) -> int:
    """
    Копирует первую таблицу из source SQLite в dest.
    """
    src_con = sqlite3.connect(f"file:{source}?mode=ro", uri=True)
    cursor = src_con.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
    tables = [row[0] for row in cursor.fetchall()]
    src_con.close()

    if not tables:
        raise ValueError("SQLite file contains no tables")

    # Копируем все таблицы как есть
    shutil.copy2(source, dest)
    # Подсчитываем строки первой таблицы
    con = sqlite3.connect(str(dest))
    try:
        row = con.execute(f'SELECT COUNT(*) FROM "{tables[0]}"').fetchone()
        return row[0] if row else 0
    finally:
        con.close()


def get_sqlite_tables(db_path: Path) -> list[str]:
    con = sqlite3.connect(str(db_path))
    try:
        cursor = con.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
        return [row[0] for row in cursor.fetchall()]
    finally:
        con.close()


def sample_rows(db_path: Path, table: str, n: int = 5) -> list[dict]:
    con = sqlite3.connect(str(db_path))
    con.row_factory = sqlite3.Row
    try:
        cursor = con.execute(f'SELECT * FROM "{table}" LIMIT {n}')
        return [dict(row) for row in cursor.fetchall()]
    finally:
        con.close()


def _sanitize_col(name: str) -> str:
    return (
        str(name)
        .strip()
        .replace(" ", "_")
        .replace(".", "_")
        .replace("-", "_")
        .replace("/", "_")
        .replace("\\", "_")
    )


def get_table_info(db_path: Path, table: str) -> list[dict]:
    """Возвращает PRAGMA table_info как список dict."""
    con = sqlite3.connect(str(db_path))
    try:
        cursor = con.execute(f'PRAGMA table_info("{table}")')
        cols = [{"cid": r[0], "name": r[1], "type": r[2]} for r in cursor.fetchall()]
        return cols
    finally:
        con.close()


def get_row_count(db_path: Path, table: str) -> int:
    con = sqlite3.connect(str(db_path))
    try:
        row = con.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()
        return row[0] if row else 0
    finally:
        con.close()


def read_text_chunks(source: Path, chunk_lines: int = 500) -> list[str]:
    """Читает текстовый файл чанками строк. Используется для chunking-стратегии LLM."""
    encoding = detect_encoding(source)
    chunks = []
    buffer = []
    with open(source, encoding=encoding, errors="replace") as f:
        for line in f:
            buffer.append(line)
            if len(buffer) >= chunk_lines:
                chunks.append("".join(buffer))
                buffer = []
    if buffer:
        chunks.append("".join(buffer))
    return chunks


def get_optional_preview(db_path: Path, table: str, max_chars: int = 3000) -> str:
    """
    Возвращает строковый превью таблицы (первые строки), ограниченный max_chars.
    Используется в промптах LLM.
    """
    rows = sample_rows(db_path, table, n=20)
    if not rows:
        return ""
    lines = []
    for row in rows:
        lines.append(str(row))
        if sum(len(l) for l in lines) > max_chars:
            break
    return "\n".join(lines)
