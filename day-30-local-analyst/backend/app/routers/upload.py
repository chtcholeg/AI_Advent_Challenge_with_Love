import uuid
from datetime import datetime, timezone
from pathlib import Path

from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.config import settings
from app.db.database import get_db
from app.models.schemas import FileOut, SchemaOut, SchemaUpdate, ColumnInfo
from app.services.parser import (
    ALLOWED_EXTENSIONS,
    get_format,
    load_to_sqlite,
    get_sqlite_tables,
    get_row_count,
)
from app.services.schema_detector import (
    build_schema_from_db,
    enrich_schema_with_llm,
    get_primary_table,
)
from app.services.llm_client import LLMClient

router = APIRouter(prefix="/api", tags=["upload"])


@router.post("/upload", response_model=FileOut)
async def upload_file(
    session_id: str = Form(...),
    file: UploadFile = File(...),
):
    # Проверка расширения
    ext = Path(file.filename or "").suffix.lower()
    if ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(
            status_code=400,
            detail=f"File type not allowed. Supported: {', '.join(ALLOWED_EXTENSIONS)}",
        )

    # Проверка размера
    content = await file.read()
    if len(content) > settings.max_upload_bytes:
        raise HTTPException(
            status_code=413,
            detail=f"File too large. Max: {settings.max_upload_size_mb} MB",
        )

    # Сохраняем оригинальный файл
    file_id = str(uuid.uuid4())
    orig_path = settings.upload_path / f"{file_id}_orig{ext}"
    orig_path.write_bytes(content)

    # Конвертируем в рабочий SQLite
    db_path = settings.upload_path / f"{file_id}.db"
    fmt = get_format(file.filename or "file.txt")

    try:
        row_count = load_to_sqlite(orig_path, db_path)
    except Exception as e:
        orig_path.unlink(missing_ok=True)
        raise HTTPException(status_code=422, detail=f"Failed to parse file: {e}")

    # Сохраняем в БД
    now = datetime.now(timezone.utc).isoformat()
    app_db = await get_db()
    try:
        await app_db.execute(
            """INSERT INTO uploaded_files
               (id, session_id, filename, format, path, row_count, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (file_id, session_id, file.filename, fmt, str(db_path), row_count, now),
        )
        await app_db.commit()
    finally:
        await app_db.close()

    return FileOut(
        id=file_id,
        session_id=session_id,
        filename=file.filename or "",
        format=fmt,
        row_count=row_count,
        created_at=now,
    )


@router.get("/files/{file_id}/schema", response_model=SchemaOut)
async def get_schema(file_id: str, enrich: bool = True):
    app_db = await get_db()
    try:
        cursor = await app_db.execute(
            "SELECT path, filename, schema_json, row_count FROM uploaded_files WHERE id = ?",
            (file_id,),
        )
        row = await cursor.fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="File not found")

        db_path = Path(row["path"])
        table_name = get_primary_table(db_path)
        row_count = row["row_count"] or get_row_count(db_path, table_name)

        # Если схема уже есть в БД — возвращаем её
        if row["schema_json"] and not enrich:
            import json
            cols_data = json.loads(row["schema_json"])
            columns = [ColumnInfo(**c) for c in cols_data]
            return SchemaOut(
                file_id=file_id,
                columns=columns,
                row_count=row_count,
                table_name=table_name,
            )

        columns = build_schema_from_db(db_path, table_name)

        if enrich:
            llm = LLMClient()
            health = await llm.health_check()
            if health["available"]:
                try:
                    columns = await enrich_schema_with_llm(columns, table_name, llm)
                except Exception:
                    pass  # LLM enrichment is best-effort; return plain schema on failure

        return SchemaOut(
            file_id=file_id,
            columns=columns,
            row_count=row_count,
            table_name=table_name,
        )
    finally:
        await app_db.close()


@router.patch("/files/{file_id}/schema")
async def update_schema(file_id: str, body: SchemaUpdate):
    import json
    schema_json = json.dumps([c.model_dump() for c in body.columns], ensure_ascii=False)
    app_db = await get_db()
    try:
        await app_db.execute(
            "UPDATE uploaded_files SET schema_json = ? WHERE id = ?",
            (schema_json, file_id),
        )
        await app_db.commit()
        return {"ok": True}
    finally:
        await app_db.close()


@router.get("/files/{file_id}/tables")
async def get_tables(file_id: str):
    app_db = await get_db()
    try:
        cursor = await app_db.execute(
            "SELECT path FROM uploaded_files WHERE id = ?", (file_id,)
        )
        row = await cursor.fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="File not found")
        tables = get_sqlite_tables(Path(row["path"]))
        return {"tables": tables}
    finally:
        await app_db.close()


@router.get("/sessions/{session_id}/files", response_model=list[FileOut])
async def list_session_files(session_id: str):
    app_db = await get_db()
    try:
        cursor = await app_db.execute(
            "SELECT * FROM uploaded_files WHERE session_id = ? ORDER BY created_at ASC",
            (session_id,),
        )
        rows = await cursor.fetchall()
        return [
            FileOut(
                id=r["id"],
                session_id=r["session_id"],
                filename=r["filename"],
                format=r["format"],
                row_count=r["row_count"],
                created_at=r["created_at"],
            )
            for r in rows
        ]
    finally:
        await app_db.close()
