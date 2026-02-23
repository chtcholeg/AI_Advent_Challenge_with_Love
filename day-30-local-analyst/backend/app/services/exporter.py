"""
Экспорт сессий в Markdown, JSON, CSV, PDF.
"""
import csv
import io
import json
from typing import Any

import markdown as md_lib


def session_to_markdown(session_name: str, messages: list[dict]) -> str:
    lines = [f"# Log Analysis Report: {session_name}\n"]
    for msg in messages:
        if msg["role"] == "user":
            lines.append(f"## Question\n\n{msg['content']}\n")
        else:
            lines.append(f"## Answer\n\n{msg['content']}\n")
            if msg.get("sql_query"):
                lines.append(f"**SQL Query:**\n```sql\n{msg['sql_query']}\n```\n")
            if msg.get("result_json"):
                try:
                    rows = json.loads(msg["result_json"])
                    if rows and isinstance(rows, list):
                        lines.append(_rows_to_md_table(rows))
                except Exception:
                    pass
    return "\n".join(lines)


def _rows_to_md_table(rows: list[dict]) -> str:
    if not rows:
        return ""
    headers = list(rows[0].keys())
    header_line = "| " + " | ".join(str(h) for h in headers) + " |"
    sep_line = "| " + " | ".join("---" for _ in headers) + " |"
    data_lines = [
        "| " + " | ".join(str(row.get(h, "")) for h in headers) + " |"
        for row in rows[:50]
    ]
    return "\n".join([header_line, sep_line] + data_lines) + "\n"


def session_to_json(messages: list[dict]) -> str:
    export = []
    for msg in messages:
        item: dict[str, Any] = {
            "role": msg["role"],
            "content": msg["content"],
            "created_at": msg.get("created_at", ""),
        }
        if msg.get("sql_query"):
            item["sql_query"] = msg["sql_query"]
        if msg.get("result_json"):
            try:
                item["result"] = json.loads(msg["result_json"])
            except Exception:
                item["result"] = msg["result_json"]
        export.append(item)
    return json.dumps(export, ensure_ascii=False, indent=2)


def last_result_to_csv(messages: list[dict]) -> str:
    """Экспортирует результат последнего SQL-запроса как CSV."""
    # Находим последнее сообщение ассистента с result_json
    result_rows = None
    for msg in reversed(messages):
        if msg["role"] == "assistant" and msg.get("result_json"):
            try:
                result_rows = json.loads(msg["result_json"])
                break
            except Exception:
                continue

    if not result_rows or not isinstance(result_rows, list) or not result_rows:
        return "No tabular data available"

    output = io.StringIO()
    writer = csv.DictWriter(output, fieldnames=list(result_rows[0].keys()))
    writer.writeheader()
    writer.writerows(result_rows)
    return output.getvalue()


def session_to_pdf_bytes(session_name: str, messages: list[dict]) -> bytes:
    """Конвертирует сессию в PDF через weasyprint."""
    try:
        from weasyprint import HTML

        md_text = session_to_markdown(session_name, messages)
        html_body = md_lib.markdown(md_text, extensions=["tables", "fenced_code"])
        html = f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  body {{ font-family: Arial, sans-serif; margin: 40px; font-size: 14px; line-height: 1.6; }}
  h1 {{ color: #1a1a2e; border-bottom: 2px solid #1a1a2e; }}
  h2 {{ color: #16213e; margin-top: 24px; }}
  pre {{ background: #f4f4f4; padding: 12px; border-radius: 4px; overflow-x: auto; }}
  code {{ font-family: monospace; font-size: 12px; }}
  table {{ border-collapse: collapse; width: 100%; margin: 16px 0; }}
  th, td {{ border: 1px solid #ddd; padding: 8px; text-align: left; }}
  th {{ background: #e8eaf6; }}
  tr:nth-child(even) {{ background: #f9f9f9; }}
</style>
</head>
<body>
{html_body}
</body>
</html>"""
        return HTML(string=html).write_pdf()
    except ImportError:
        # Fallback: возвращаем HTML как bytes если weasyprint не установлен
        return html.encode("utf-8")
