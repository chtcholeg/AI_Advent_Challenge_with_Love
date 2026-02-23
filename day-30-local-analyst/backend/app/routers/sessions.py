import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, HTTPException
from app.db.database import get_db
from app.models.schemas import SessionCreate, SessionOut, MessageOut

router = APIRouter(prefix="/api/sessions", tags=["sessions"])


@router.post("", response_model=SessionOut)
async def create_session(body: SessionCreate):
    sid = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()
    db = await get_db()
    try:
        await db.execute(
            "INSERT INTO sessions (id, name, created_at) VALUES (?, ?, ?)",
            (sid, body.name, now),
        )
        await db.commit()
        return SessionOut(id=sid, name=body.name, created_at=now)
    finally:
        await db.close()


@router.get("", response_model=list[SessionOut])
async def list_sessions():
    db = await get_db()
    try:
        cursor = await db.execute(
            "SELECT id, name, created_at FROM sessions ORDER BY created_at DESC"
        )
        rows = await cursor.fetchall()
        return [SessionOut(id=r["id"], name=r["name"], created_at=r["created_at"]) for r in rows]
    finally:
        await db.close()


@router.get("/{session_id}", response_model=SessionOut)
async def get_session(session_id: str):
    db = await get_db()
    try:
        cursor = await db.execute(
            "SELECT id, name, created_at FROM sessions WHERE id = ?", (session_id,)
        )
        row = await cursor.fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Session not found")
        return SessionOut(id=row["id"], name=row["name"], created_at=row["created_at"])
    finally:
        await db.close()


@router.delete("/{session_id}")
async def delete_session(session_id: str):
    db = await get_db()
    try:
        await db.execute("DELETE FROM sessions WHERE id = ?", (session_id,))
        await db.commit()
        return {"ok": True}
    finally:
        await db.close()


@router.get("/{session_id}/messages", response_model=list[MessageOut])
async def get_messages(session_id: str):
    db = await get_db()
    try:
        cursor = await db.execute(
            "SELECT * FROM chat_messages WHERE session_id = ? ORDER BY created_at ASC",
            (session_id,),
        )
        rows = await cursor.fetchall()
        return [
            MessageOut(
                id=r["id"],
                session_id=r["session_id"],
                role=r["role"],
                content=r["content"],
                sql_query=r["sql_query"],
                result_json=r["result_json"],
                chart_json=r["chart_json"],
                created_at=r["created_at"],
            )
            for r in rows
        ]
    finally:
        await db.close()
