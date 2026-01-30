"""SSE (Server-Sent Events) transport layer for MCP.

Manages long-lived SSE connections and pairs them with JSON-RPC
message delivery. Each client that connects to /sse receives a unique
session ID and streams responses back through the event channel.

Session lifecycle:
  1. Client GETs /sse  →  server creates session, sends endpoint event
  2. Client POSTs to /message?sessionId=<id>  →  server processes request
  3. Response pushed into session queue  →  streamed back via SSE
  4. Client disconnects  →  session cleaned up
"""

import asyncio
import logging
import uuid
from typing import Dict

from fastapi import FastAPI, Request, Response
from fastapi.responses import StreamingResponse, JSONResponse

logger = logging.getLogger(__name__)


class SseSession:
    """Per-client session holding a message queue for SSE delivery."""

    def __init__(self, session_id: str):
        self.id = session_id
        self.queue: asyncio.Queue[str] = asyncio.Queue()


class SseTransport:
    """Registers SSE and message routes on a FastAPI app."""

    def __init__(self, protocol_handler):
        self.protocol_handler = protocol_handler
        self.sessions: Dict[str, SseSession] = {}

    def setup_routes(self, app: FastAPI):
        @app.get("/sse")
        async def sse_endpoint():
            session_id = str(uuid.uuid4())
            session = SseSession(session_id)
            self.sessions[session_id] = session
            logger.info(f"New SSE session: {session_id}")

            async def event_generator():
                try:
                    # Inform client where to send messages
                    yield f"event: endpoint\ndata: /message?sessionId={session_id}\n\n"

                    while True:
                        try:
                            message = await asyncio.wait_for(session.queue.get(), timeout=30.0)
                            if message == "":
                                break  # shutdown signal
                            yield f"event: message\ndata: {message}\n\n"
                        except asyncio.TimeoutError:
                            yield ": keepalive\n\n"
                except asyncio.CancelledError:
                    logger.debug(f"SSE session cancelled: {session_id}")
                except Exception as e:
                    logger.debug(f"SSE session error ({session_id}): {e}")
                finally:
                    self.sessions.pop(session_id, None)
                    logger.info(f"SSE session closed: {session_id}")

            return StreamingResponse(
                event_generator(),
                media_type="text/event-stream",
                headers={
                    "Cache-Control": "no-cache",
                    "Connection": "keep-alive",
                    "X-Accel-Buffering": "no",
                },
            )

        @app.post("/message")
        async def message_endpoint(request: Request):
            session_id = request.query_params.get("sessionId")
            if not session_id:
                return JSONResponse({"error": "Missing sessionId query parameter"}, status_code=400)

            session = self.sessions.get(session_id)
            if not session:
                return JSONResponse({"error": "Session not found"}, status_code=404)

            body = await request.body()
            request_json = body.decode("utf-8")
            logger.debug(f"Message for {session_id}: {request_json}")

            response_json = await self.protocol_handler.handle_request(request_json)

            # Push response to SSE stream
            await session.queue.put(response_json)

            # Return directly too (for clients that read the POST response)
            return Response(content=response_json, media_type="application/json")

    def get_active_session_count(self) -> int:
        return len(self.sessions)

    async def close_all_sessions(self):
        for session in self.sessions.values():
            await session.queue.put("")  # signal stop
        self.sessions.clear()
