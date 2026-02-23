"""
Основной роутер анализа: чат, SSE-стриминг, SQL-запуск вручную.
"""
import json
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import AsyncIterator

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse

from app.config import settings
from app.db.database import get_db
from app.models.schemas import ChatRequest, SqlRunRequest
from app.services.llm_client import (
    LLMClient,
    OllamaUnavailableError,
    SYSTEM_ANALYST,
    build_sql_prompt,
    build_explanation_prompt,
    build_retry_sql_prompt,
    should_suggest_chart,
)
from app.services.sql_engine import (
    execute_query,
    validate_sql,
    SqlSandboxError,
    sql_hash,
    serialize_result,
    deserialize_result,
)
from app.services.schema_detector import get_primary_table
from app.services.chunker import summarize_text_file
from app.services import semantic_analysis as sem

router = APIRouter(prefix="/api/sessions", tags=["analyze"])

MAX_RETRIES = 2

# ─── Result post-processing: fix NULLs and merge duplicate product names ─────

_PRODUCT_EXACT_NAMES = frozenset({
    "app_name", "product_name", "product", "title", "name", "app", "application",
    "appname", "app_title", "product_title",
})
_PRODUCT_SUBSTRINGS = ("app_name", "product", "appname", "приложени", "название")

_SUM_SUBSTRINGS = ("count", "cnt", "total", "сумма", "итого")
_MEAN_SUBSTRINGS = ("avg", "mean", "average")


def _detect_product_col_in_result(col_names: list[str]) -> str | None:
    for name in col_names:
        if name.lower() in _PRODUCT_EXACT_NAMES:
            return name
    for name in col_names:
        if any(sub in name.lower() for sub in _PRODUCT_SUBSTRINGS):
            return name
    return None


def _is_sum_col(col_name: str) -> bool:
    return any(sub in col_name.lower() for sub in _SUM_SUBSTRINGS)


def _is_mean_col(col_name: str) -> bool:
    return any(sub in col_name.lower() for sub in _MEAN_SUBSTRINGS)


def _build_product_name_mapping(names: list[str]) -> dict[str, str]:
    """
    Substring-based canonicalization: if name A is a substring of name B (min 4 chars),
    map A → B (shorter variant → longer canonical).
    Example: 'Zoom' → 'Zoom Workplace'.
    """
    mapping: dict[str, str] = {}
    for a in names:
        for b in names:
            if a != b and len(a.strip()) >= 4 and a.lower() in b.lower():
                if a not in mapping or len(mapping[a]) < len(b):
                    mapping[a] = b
    return mapping


def _postprocess_result_rows(rows: list[dict]) -> list[dict]:
    """
    1. Replaces None in the product column with 'Неизвестный продукт'.
    2. Normalizes similar product names (e.g. 'Zoom' → 'Zoom Workplace').
    3. Re-aggregates rows that now share the same key after normalization,
       summing numeric aggregate columns (count, total, etc.).
    """
    if not rows:
        return rows

    from collections import OrderedDict

    col_names = list(rows[0].keys())
    product_col = _detect_product_col_in_result(col_names)

    # Step 1: replace None in product column
    filled: list[dict] = []
    for row in rows:
        new_row = dict(row)
        if product_col and new_row.get(product_col) is None:
            new_row[product_col] = "Неизвестный продукт"
        filled.append(new_row)

    if not product_col:
        return filled

    # Step 2: build normalization mapping from distinct product values
    product_values = list(dict.fromkeys(str(r[product_col]) for r in filled))
    mapping = _build_product_name_mapping(product_values)
    if not mapping:
        return filled

    # Apply mapping
    normalized: list[dict] = []
    for row in filled:
        new_row = dict(row)
        pv = str(new_row[product_col])
        new_row[product_col] = mapping.get(pv, pv)
        normalized.append(new_row)

    # Step 3: re-aggregate rows sharing the same key after normalization.
    # Columns named count/cnt/total → SUM; avg/mean/average → MEAN; others → GROUP BY key.
    first = normalized[0]
    sum_cols = {c for c in col_names if _is_sum_col(c) and isinstance(first.get(c), (int, float))}
    mean_cols = {c for c in col_names if _is_mean_col(c) and isinstance(first.get(c), (int, float))}
    agg_cols = sum_cols | mean_cols
    key_cols = [c for c in col_names if c not in agg_cols]

    groups: OrderedDict = OrderedDict()
    group_counts: dict = {}  # number of rows merged per key (for mean calculation)
    for row in normalized:
        key = tuple(row.get(c) for c in key_cols)
        if key not in groups:
            groups[key] = dict(row)
            group_counts[key] = 1
        else:
            group_counts[key] += 1
            for c in sum_cols:
                v_new = row.get(c)
                v_old = groups[key].get(c)
                if isinstance(v_new, (int, float)) and isinstance(v_old, (int, float)):
                    groups[key][c] = v_old + v_new
            for c in mean_cols:
                v_new = row.get(c)
                v_old = groups[key].get(c)
                if isinstance(v_new, (int, float)) and isinstance(v_old, (int, float)):
                    # Running sum; divide at the end
                    groups[key][c] = v_old + v_new

    # Finalise mean columns
    for key, row in groups.items():
        n = group_counts[key]
        if n > 1:
            for c in mean_cols:
                if isinstance(row.get(c), (int, float)):
                    row[c] = round(row[c] / n, 2)

    return list(groups.values())


# Ключевые слова, сигнализирующие о необходимости семантического анализа
_SEMANTIC_KEYWORDS = (
    "проблем", "жалоб", "ошибк", "недовольн", "баг", "глюк", "критик",
    "что не нравится", "что плохо", "что не так", "минус",
    "кластер", "сгруппир", "категори", "тематик",
    "error", "problem", "complaint", "issue", "bug", "cluster", "top error",
)


def _needs_semantic_analysis(question: str) -> bool:
    q = question.lower()
    return any(kw in q for kw in _SEMANTIC_KEYWORDS)


async def _get_file_context(file_id: str, db) -> dict:
    """Возвращает метаданные файла для построения промпта."""
    cursor = await db.execute(
        "SELECT path, filename, format, schema_json, row_count FROM uploaded_files WHERE id = ?",
        (file_id,),
    )
    row = await cursor.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail=f"File {file_id} not found")
    return dict(row)


async def _get_or_build_schema_context(file_info: dict, file_id: str) -> list[dict]:
    from app.services.schema_detector import build_schema_from_db
    import sqlite3

    db_path = Path(file_info["path"])
    table_name = get_primary_table(db_path)

    if file_info["schema_json"]:
        cols = json.loads(file_info["schema_json"])
        # Refresh examples for TEXT columns from live DB so the LLM sees all distinct values
        # (cached examples may only contain 3 samples from 5 rows, missing variants like "Zoom Workplace")
        try:
            con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
            try:
                for col in cols:
                    dtype = (col.get("dtype") or "").upper()
                    is_numeric = any(t in dtype for t in ("INT", "FLOAT", "REAL", "NUM", "DOUBLE"))
                    if not is_numeric:
                        name = col["name"]
                        # Only refresh if column cardinality is low (skip free-text review columns)
                        card_row = con.execute(
                            f'SELECT COUNT(DISTINCT "{name}") as cnt FROM "{table_name}"'
                        ).fetchone()
                        if card_row and card_row[0] <= 100:
                            cursor = con.execute(
                                f'SELECT "{name}", COUNT(*) as cnt FROM "{table_name}" '
                                f'WHERE "{name}" IS NOT NULL AND TRIM(CAST("{name}" AS TEXT)) != "" '
                                f'GROUP BY "{name}" ORDER BY cnt DESC LIMIT 10'
                            )
                            fresh = [str(row[0]) for row in cursor.fetchall()]
                            if fresh:
                                col["examples"] = fresh
            finally:
                con.close()
        except Exception:
            pass  # Keep cached examples on error
    else:
        cols_obj = build_schema_from_db(db_path, table_name)
        cols = [c.model_dump() for c in cols_obj]

    return [{
        "name": table_name,
        "columns": cols,
        "row_count": file_info["row_count"] or 0,
    }]


async def _build_analysis_context(
    file_infos: dict,
    file_ids: list[str],
    all_tables: list[dict],
) -> str:
    """
    Собирает контекст для free-form анализа когда SQL не применим.
    Запускает автоматические агрегирующие запросы и случайную выборку.
    """
    from app.services.schema_detector import get_primary_table

    parts: list[str] = []

    for fid in file_ids:
        fi = file_infos[fid]
        db_path = Path(fi["path"])
        table_name = get_primary_table(db_path)
        schema_info = next((t for t in all_tables if t["name"] == table_name), None)
        if not schema_info:
            continue

        # Схема
        cols_str = "\n".join(
            f"  - {c['name']} ({c['dtype']})" + (f": {c.get('description', '')}" if c.get("description") else "")
            for c in schema_info["columns"]
        )
        parts.append(f"Table '{table_name}' ({schema_info['row_count']:,} rows):\n{cols_str}")

        # Агрегаты по колонкам
        agg_parts: list[str] = []
        for col in schema_info["columns"]:
            dtype = col["dtype"].upper()
            name = col["name"]
            try:
                if any(t in dtype for t in ("INT", "FLOAT", "REAL", "NUM", "DOUBLE")):
                    rows = execute_query(
                        db_path,
                        f'SELECT "{name}", COUNT(*) as cnt FROM "{table_name}" '
                        f'GROUP BY "{name}" ORDER BY cnt DESC LIMIT 15',
                    )
                    if rows:
                        agg_parts.append(f"{name} distribution:\n" + json.dumps(rows, default=str))
                else:
                    # Текстовые колонки: группируем только если кардинальность небольшая
                    card = execute_query(
                        db_path,
                        f'SELECT COUNT(DISTINCT "{name}") as cnt FROM "{table_name}"',
                    )
                    if card and card[0].get("cnt", 9999) <= 30:
                        rows = execute_query(
                            db_path,
                            f'SELECT "{name}", COUNT(*) as cnt FROM "{table_name}" '
                            f'GROUP BY "{name}" ORDER BY cnt DESC LIMIT 15',
                        )
                        if rows:
                            agg_parts.append(f"{name} distribution:\n" + json.dumps(rows, default=str))
            except Exception:
                pass

        if agg_parts:
            parts.append("Aggregate statistics:\n" + "\n\n".join(agg_parts))

        # Случайная выборка строк
        try:
            sample = execute_query(
                db_path,
                f'SELECT * FROM "{table_name}" ORDER BY RANDOM() LIMIT 30',
            )
            if sample:
                parts.append(
                    "Random sample (30 rows):\n"
                    + json.dumps(sample, default=str, ensure_ascii=False, indent=2)
                )
        except Exception:
            pass

    return "\n\n---\n\n".join(parts)


async def _check_query_cache(file_id: str, sql: str, db) -> list[dict] | None:
    key = sql_hash(file_id, sql)
    cursor = await db.execute(
        "SELECT result_json FROM query_cache WHERE file_id = ? AND sql_hash = ?",
        (file_id, key),
    )
    row = await cursor.fetchone()
    if row:
        return deserialize_result(row["result_json"])
    return None


async def _write_query_cache(file_id: str, sql: str, rows: list[dict], db) -> None:
    key = sql_hash(file_id, sql)
    cache_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()
    try:
        await db.execute(
            """INSERT OR REPLACE INTO query_cache (id, file_id, sql_hash, result_json, created_at)
               VALUES (?, ?, ?, ?, ?)""",
            (cache_id, file_id, key, serialize_result(rows), now),
        )
        await db.commit()
    except Exception:
        pass  # Кэш не критичен


async def _save_messages(
    session_id: str,
    question: str,
    answer: str,
    sql_query: str | None,
    result_rows: list[dict] | None,
    chart_json: str | None,
    db,
) -> None:
    now = datetime.now(timezone.utc).isoformat()
    user_id = str(uuid.uuid4())
    asst_id = str(uuid.uuid4())

    await db.execute(
        "INSERT INTO chat_messages (id, session_id, role, content, created_at) VALUES (?, ?, ?, ?, ?)",
        (user_id, session_id, "user", question, now),
    )
    await db.execute(
        """INSERT INTO chat_messages
           (id, session_id, role, content, sql_query, result_json, chart_json, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
        (
            asst_id,
            session_id,
            "assistant",
            answer,
            sql_query,
            serialize_result(result_rows) if result_rows else None,
            chart_json,
            now,
        ),
    )
    await db.commit()


def _extract_chart_json(text: str) -> tuple[str, str | None]:
    """Извлекает JSON графика из текста ответа LLM."""
    import re
    pattern = r"```json\s*(\{.*?\"chart\".*?\})\s*```"
    match = re.search(pattern, text, re.DOTALL)
    if match:
        chart_raw = match.group(1)
        clean_text = text[: match.start()].rstrip()
        try:
            json.loads(chart_raw)  # validate
            return clean_text, chart_raw
        except json.JSONDecodeError:
            pass
    return text, None


@router.post("/{session_id}/chat")
async def chat(session_id: str, body: ChatRequest):
    """
    Основной эндпоинт чата. Возвращает SSE-поток.
    Стадии: schema → sql_gen → sql_exec → llm_explain → done
    """

    async def event_stream() -> AsyncIterator[str]:
        db = await get_db()

        cursor = await db.execute("SELECT value FROM app_settings WHERE key = 'ollama_base_url'")
        row = await cursor.fetchone()
        base_url = row["value"] if row else None

        cursor = await db.execute("SELECT value FROM app_settings WHERE key = 'ollama_model'")
        row = await cursor.fetchone()
        model = row["value"] if row else None

        llm = LLMClient(base_url=base_url, model=model)
        answer_parts: list[str] = []
        final_sql: str | None = None
        final_rows: list[dict] | None = None
        final_chart: str | None = None

        def sse(event: str, data: dict) -> str:
            return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"

        try:
            # Проверка Ollama
            health = await llm.health_check()
            if not health["available"]:
                yield sse("error", {
                    "message": f"Ollama is not available: {health.get('error', '')}. "
                               "Please start Ollama with: `ollama serve`"
                })
                return

            # Получаем файлы сессии
            file_ids = body.file_ids
            if not file_ids:
                cursor = await db.execute(
                    "SELECT id FROM uploaded_files WHERE session_id = ? ORDER BY created_at",
                    (session_id,),
                )
                rows = await cursor.fetchall()
                file_ids = [r["id"] for r in rows]

            if not file_ids:
                yield sse("error", {"message": "No files uploaded in this session."})
                return

            # Стадия 1: Загружаем схемы
            yield sse("stage", {"stage": "schema", "label": "Читаю схему..."})

            all_tables: list[dict] = []
            file_infos: dict[str, dict] = {}

            for fid in file_ids:
                fi = await _get_file_context(fid, db)
                file_infos[fid] = fi
                tables = await _get_or_build_schema_context(fi, fid)
                all_tables.extend(tables)

            # Для текстовых файлов используем chunking-стратегию
            text_file_ids = [
                fid for fid, fi in file_infos.items() if fi["format"] == "text"
            ]

            if text_file_ids:
                # Chunking для текста
                yield sse("stage", {"stage": "chunking", "label": "Анализирую текстовый лог..."})
                source_path = Path(file_infos[text_file_ids[0]]["path"].replace(".db", "_orig.txt")
                                   .replace(".db", ""))
                # Ищем оригинальный текстовый файл
                orig_candidates = list(
                    settings.upload_path.glob(f"{text_file_ids[0]}_orig*")
                )
                if orig_candidates:
                    answer = await summarize_text_file(
                        orig_candidates[0], body.question, llm
                    )
                    yield sse("token", {"token": answer})
                    yield sse("done", {"sql": None, "chart": None})
                    await _save_messages(session_id, body.question, answer, None, None, None, db)
                    return

            # ── Семантический анализ (кластеризация проблем) ──────────────────
            if _needs_semantic_analysis(body.question):
                db_path = Path(file_infos[file_ids[0]]["path"])
                table_name = get_primary_table(db_path)
                schema_info = all_tables[0]["columns"] if all_tables else []

                full_report = f"## Анализ проблем по продуктам\n"
                yield sse("stage", {"stage": "semantic", "label": "Определяю колонки схемы..."})

                async for event in sem.analyze_complaints(db_path, table_name, schema_info, llm):
                    if event["type"] == "error":
                        yield sse("error", {"message": event["message"]})
                        return
                    elif event["type"] == "stage":
                        yield sse("stage", {"stage": "semantic", "label": event["message"]})
                    elif event["type"] == "start":
                        n = event["total_products"]
                        yield sse("stage", {"stage": "semantic", "label": f"Найдено {n} продуктов..."})
                    elif event["type"] == "product_start":
                        yield sse("stage", {
                            "stage": "semantic",
                            "label": f"Кластеризую: {event['product']} ({event['total']} жалоб)...",
                        })
                    elif event["type"] == "product_done":
                        text = event["text"]
                        full_report += text
                        yield sse("token", {"token": text})

                yield sse("done", {"sql": None, "chart": None})
                await _save_messages(session_id, body.question, full_report, None, None, None, db)
                return
            # ──────────────────────────────────────────────────────────────────

            # Стадия 2: Генерация SQL
            yield sse("stage", {"stage": "sql_gen", "label": "Генерирую SQL-запрос..."})

            sql_prompt = build_sql_prompt(body.question, all_tables)
            generated_sql: str | None = None
            result_rows: list[dict] | None = None
            last_error = ""

            for attempt in range(MAX_RETRIES + 1):
                try:
                    if attempt == 0:
                        raw_sql = await llm.chat_sync(sql_prompt, system=SYSTEM_ANALYST)
                    else:
                        yield sse("stage", {
                            "stage": "sql_retry",
                            "label": f"Исправляю SQL (попытка {attempt + 1})..."
                        })
                        retry_prompt = build_retry_sql_prompt(sql_prompt, generated_sql or "", last_error)
                        raw_sql = await llm.chat_sync(retry_prompt, system=SYSTEM_ANALYST)

                    raw_sql = raw_sql.strip().strip("```sql").strip("```").strip()

                    if raw_sql.upper().startswith("CANNOT_ANSWER"):
                        # SQL не подходит — собираем данные сами и анализируем
                        yield sse("stage", {"stage": "llm", "label": "Собираю данные для анализа..."})
                        data_context = await _build_analysis_context(file_infos, file_ids, all_tables)
                        analysis_prompt = (
                            f"Вот реальные данные из базы:\n\n"
                            f"{data_context}\n\n"
                            f"Вопрос пользователя: {body.question}\n\n"
                            f"Проанализируй данные выше и дай конкретный ответ. "
                            f"Не предлагай запускать запросы и не рекомендуй дополнительный анализ — "
                            f"отвечай по тому, что видишь в данных прямо сейчас. "
                            f"Отвечай на русском языке."
                        )
                        yield sse("stage", {"stage": "llm", "label": "Анализирую данные..."})
                        full_answer = ""
                        async for token in llm.chat_stream(analysis_prompt, system=SYSTEM_ANALYST):
                            yield sse("token", {"token": token})
                            full_answer += token
                        yield sse("done", {"sql": None, "chart": None})
                        await _save_messages(session_id, body.question, full_answer, None, None, None, db)
                        return

                    validate_sql(raw_sql)
                    generated_sql = raw_sql

                    # Стадия 3: Выполнение SQL
                    yield sse("stage", {"stage": "sql_exec", "label": "Выполняю запрос..."})

                    # Проверяем кэш
                    for fid in file_ids:
                        cached = await _check_query_cache(fid, generated_sql, db)
                        if cached is not None:
                            result_rows = cached
                            yield sse("cache_hit", {"message": "Result from cache"})
                            break

                    if result_rows is None:
                        db_path = Path(file_infos[file_ids[0]]["path"])
                        result_rows = execute_query(db_path, generated_sql)
                        # Кэшируем
                        await _write_query_cache(file_ids[0], generated_sql, result_rows, db)

                    # Нормализуем продукты: убираем NULL и объединяем варианты (Zoom / Zoom Workplace).
                    # Применяется всегда — и для кэшированных, и для свежих результатов.
                    result_rows = _postprocess_result_rows(result_rows)

                    break  # Успех

                except (SqlSandboxError, Exception) as e:
                    last_error = str(e)
                    if attempt == MAX_RETRIES:
                        # Исчерпали попытки
                        yield sse("error", {
                            "message": f"Could not generate a valid SQL query after {MAX_RETRIES + 1} attempts. "
                                       f"Please try rephrasing your question.\n\nLast error: {last_error}"
                        })
                        return

            if result_rows is None:
                result_rows = []

            # Стадия 4: Объяснение результата
            yield sse("stage", {"stage": "llm", "label": "Формирую ответ..."})
            yield sse("sql", {"sql": generated_sql})

            with_chart = should_suggest_chart(result_rows)
            explain_prompt = build_explanation_prompt(
                body.question,
                generated_sql or "",
                result_rows,
                chart_hint="bar" if with_chart else None,
            )

            full_answer = ""
            async for token in llm.chat_stream(explain_prompt, system=SYSTEM_ANALYST):
                yield sse("token", {"token": token})
                full_answer += token

            # Извлекаем график из ответа
            clean_answer, chart_json = _extract_chart_json(full_answer)

            if result_rows:
                yield sse("result", {"rows": result_rows[:100]})

            if chart_json:
                yield sse("chart", {"chart": json.loads(chart_json)})

            yield sse("done", {"sql": generated_sql, "chart": chart_json})

            await _save_messages(
                session_id,
                body.question,
                clean_answer,
                generated_sql,
                result_rows,
                chart_json,
                db,
            )

        except OllamaUnavailableError as e:
            yield sse("error", {"message": str(e)})
        except Exception as e:
            yield sse("error", {"message": f"Internal error: {e}"})
        finally:
            await db.close()

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/{session_id}/chat/sql")
async def run_sql_manually(session_id: str, body: SqlRunRequest):
    """Запускает SQL вручную (из редактора в UI)."""
    try:
        validate_sql(body.sql)
    except SqlSandboxError as e:
        raise HTTPException(status_code=400, detail=str(e))

    app_db = await get_db()
    try:
        cursor = await app_db.execute(
            "SELECT path FROM uploaded_files WHERE id = ?", (body.file_id,)
        )
        row = await cursor.fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="File not found")

        db_path = Path(row["path"])
        try:
            result_rows = execute_query(db_path, body.sql)
        except Exception as e:
            raise HTTPException(status_code=422, detail=f"SQL execution failed: {e}")

        await _write_query_cache(body.file_id, body.sql, result_rows, app_db)

        return {"rows": result_rows, "count": len(result_rows)}
    finally:
        await app_db.close()
