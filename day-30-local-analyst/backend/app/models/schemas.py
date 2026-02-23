from pydantic import BaseModel
from typing import Any, Optional


class SessionCreate(BaseModel):
    name: str = "New Session"


class SessionOut(BaseModel):
    id: str
    name: str
    created_at: str


class ColumnInfo(BaseModel):
    name: str
    dtype: str
    description: str = ""
    examples: list[Any] = []


class SchemaOut(BaseModel):
    file_id: str
    columns: list[ColumnInfo]
    row_count: int
    table_name: str


class SchemaUpdate(BaseModel):
    columns: list[ColumnInfo]


class FileOut(BaseModel):
    id: str
    session_id: str
    filename: str
    format: str
    row_count: Optional[int]
    created_at: str


class ChatRequest(BaseModel):
    question: str
    file_ids: list[str] = []


class SqlRunRequest(BaseModel):
    sql: str
    file_id: str


class MessageOut(BaseModel):
    id: str
    session_id: str
    role: str
    content: str
    sql_query: Optional[str] = None
    result_json: Optional[str] = None
    chart_json: Optional[str] = None
    created_at: str


class OllamaStatus(BaseModel):
    available: bool
    models: list[str] = []
    error: Optional[str] = None


class AppSettingsIn(BaseModel):
    ollama_model: Optional[str] = None
    ollama_base_url: Optional[str] = None
