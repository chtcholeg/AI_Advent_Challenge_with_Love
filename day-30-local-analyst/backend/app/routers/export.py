from fastapi import APIRouter, HTTPException
from fastapi.responses import Response

from app.db.database import get_db
from app.services.exporter import (
    session_to_markdown,
    session_to_json,
    last_result_to_csv,
    session_to_pdf_bytes,
)

router = APIRouter(prefix="/api/sessions", tags=["export"])


async def _fetch_session_data(session_id: str):
    db = await get_db()
    try:
        cursor = await db.execute(
            "SELECT name FROM sessions WHERE id = ?", (session_id,)
        )
        session = await cursor.fetchone()
        if not session:
            raise HTTPException(status_code=404, detail="Session not found")

        cursor = await db.execute(
            "SELECT * FROM chat_messages WHERE session_id = ? ORDER BY created_at ASC",
            (session_id,),
        )
        messages = [dict(r) for r in await cursor.fetchall()]
        return session["name"], messages
    finally:
        await db.close()


@router.get("/{session_id}/export/markdown")
async def export_markdown(session_id: str):
    name, messages = await _fetch_session_data(session_id)
    content = session_to_markdown(name, messages)
    return Response(
        content=content.encode("utf-8"),
        media_type="text/markdown",
        headers={"Content-Disposition": f'attachment; filename="report_{session_id[:8]}.md"'},
    )


@router.get("/{session_id}/export/json")
async def export_json(session_id: str):
    _, messages = await _fetch_session_data(session_id)
    content = session_to_json(messages)
    return Response(
        content=content.encode("utf-8"),
        media_type="application/json",
        headers={"Content-Disposition": f'attachment; filename="report_{session_id[:8]}.json"'},
    )


@router.get("/{session_id}/export/csv")
async def export_csv(session_id: str):
    _, messages = await _fetch_session_data(session_id)
    content = last_result_to_csv(messages)
    return Response(
        content=content.encode("utf-8"),
        media_type="text/csv",
        headers={"Content-Disposition": f'attachment; filename="data_{session_id[:8]}.csv"'},
    )


@router.get("/{session_id}/export/pdf")
async def export_pdf(session_id: str):
    name, messages = await _fetch_session_data(session_id)
    content = session_to_pdf_bytes(name, messages)
    return Response(
        content=content,
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="report_{session_id[:8]}.pdf"'},
    )
