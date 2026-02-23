"""
Семантический анализ: кластеризация жалоб/проблем по смыслу через LLM.
Группирует негативные отзывы по продуктам и определяет основные категории проблем.
"""
import json
import random
import sqlite3
from pathlib import Path
from typing import AsyncIterator, TYPE_CHECKING

if TYPE_CHECKING:
    from app.services.llm_client import LLMClient


# ─── Определение колонок ──────────────────────────────────────────────────────

_PRODUCT_EXACT  = {"app_name", "product_name", "product", "title", "name", "app", "application"}
_TEXT_EXACT     = {"text", "review_text", "review", "comment", "body", "content", "message"}
_RATING_EXACT   = {"rating", "rate", "score", "stars", "grade", "mark", "rank"}
_APP_ID_EXACT   = {"app_id", "package_name", "package", "bundle_id", "bundle", "app_package",
                   "applicationid", "app_identifier"}

_PRODUCT_PARTIAL = ("app_name", "_name", "product", "приложени", "название")
_TEXT_PARTIAL    = ("text", "review", "comment", "content", "текст", "комментарий")
_RATING_PARTIAL  = ("rating", "rate", "score", "оценк", "рейтинг", "балл", "звезд")
_APP_ID_PARTIAL  = ("app_id", "package", "bundle", "appid")

_TEXT_DESC_PHRASES    = ("текст отзыва", "текст комментария", "содержимое отзыва",
                         "review text", "comment text", "review content", "text of the review")
_PRODUCT_DESC_PHRASES = ("название приложения", "название продукта", "имя приложения",
                         "app name", "product name", "application name")
_APP_ID_DESC_PHRASES  = ("идентификатор приложения", "package name", "bundle id",
                         "app package", "android package", "ios bundle")


def detect_columns(
    schema_info: list[dict],
) -> tuple[str | None, str | None, str | None, str | None]:
    """
    Трёхфазная детекция колонок: точное имя → частичное → фразы в описании.
    Возвращает (product_col, text_col, rating_col, app_id_col).
    """
    product_col = text_col = rating_col = app_id_col = None

    def is_numeric(dtype: str) -> bool:
        dt = dtype.upper()
        return any(t in dt for t in ("INT", "FLOAT", "REAL", "NUM", "DOUBLE"))

    # Фаза 1: точное совпадение имени колонки
    for col in schema_info:
        name_lower = col["name"].lower()
        dtype = col["dtype"].upper()

        if not product_col and name_lower in _PRODUCT_EXACT:
            product_col = col["name"]
        if not text_col and name_lower in _TEXT_EXACT:
            text_col = col["name"]
        if not rating_col and name_lower in _RATING_EXACT and is_numeric(dtype):
            rating_col = col["name"]
        if not app_id_col and name_lower in _APP_ID_EXACT:
            app_id_col = col["name"]

    # Фаза 2: частичное совпадение имени колонки
    for col in schema_info:
        name_lower = col["name"].lower()
        dtype = col["dtype"].upper()

        if not product_col and any(k in name_lower for k in _PRODUCT_PARTIAL):
            product_col = col["name"]
        if not text_col and any(k in name_lower for k in _TEXT_PARTIAL):
            text_col = col["name"]
        if not rating_col and any(k in name_lower for k in _RATING_PARTIAL) and is_numeric(dtype):
            rating_col = col["name"]
        if not app_id_col and any(k in name_lower for k in _APP_ID_PARTIAL):
            app_id_col = col["name"]

    # Фаза 3: точные фразы в описании (только если ещё не найдено)
    for col in schema_info:
        desc = (col.get("description") or "").lower()

        if not product_col and any(p in desc for p in _PRODUCT_DESC_PHRASES):
            product_col = col["name"]
        if not text_col and any(p in desc for p in _TEXT_DESC_PHRASES):
            text_col = col["name"]
        if not app_id_col and any(p in desc for p in _APP_ID_DESC_PHRASES):
            app_id_col = col["name"]

    return product_col, text_col, rating_col, app_id_col


# ─── Выборка жалоб из БД ──────────────────────────────────────────────────────

def fetch_complaints(
    db_path: Path,
    table_name: str,
    product_col: str,
    text_col: str,
    rating_col: str,
    app_id_col: str | None = None,
    rating_threshold: int = 3,
    min_per_product: int = 3,
    max_per_product: int = 120,
) -> list[tuple[str, int, list[str], list[str]]]:
    """
    Возвращает [(product_name, total_complaints, [text, ...], [app_id, ...]), ...]
    app_ids — список наиболее частых package-имён для данной группы (пусто если колонки нет).
    Отсортировано по количеству жалоб (по убыванию).
    """
    con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    try:
        cursor = con.execute(
            f'SELECT COALESCE("{product_col}", "Неизвестный продукт"), COUNT(*) as cnt '
            f'FROM "{table_name}" '
            f'WHERE CAST("{rating_col}" AS REAL) <= {rating_threshold} '
            f'  AND "{text_col}" IS NOT NULL '
            f'  AND TRIM("{text_col}") != "" '
            f'GROUP BY COALESCE("{product_col}", "Неизвестный продукт") '
            f'HAVING cnt >= {min_per_product} '
            f'ORDER BY cnt DESC'
        )
        products = cursor.fetchall()

        result = []
        for product_name, total in products:
            # Тексты жалоб
            cursor = con.execute(
                f'SELECT "{text_col}" FROM "{table_name}" '
                f'WHERE COALESCE("{product_col}", "Неизвестный продукт") = ? '
                f'  AND CAST("{rating_col}" AS REAL) <= {rating_threshold} '
                f'  AND "{text_col}" IS NOT NULL '
                f'  AND TRIM("{text_col}") != "" '
                f'ORDER BY RANDOM() LIMIT {max_per_product}',
                (product_name,),
            )
            texts = [row[0].strip() for row in cursor.fetchall() if row[0] and row[0].strip()]

            # Наиболее частые app_id для этой группы (до 5 уникальных)
            app_ids: list[str] = []
            if app_id_col:
                cursor = con.execute(
                    f'SELECT "{app_id_col}", COUNT(*) as cnt FROM "{table_name}" '
                    f'WHERE COALESCE("{product_col}", "Неизвестный продукт") = ? '
                    f'  AND "{app_id_col}" IS NOT NULL '
                    f'  AND TRIM("{app_id_col}") != "" '
                    f'GROUP BY "{app_id_col}" ORDER BY cnt DESC LIMIT 5',
                    (product_name,),
                )
                app_ids = [row[0].strip() for row in cursor.fetchall() if row[0]]

            if texts:
                result.append((str(product_name), total, texts, app_ids))

        return result
    finally:
        con.close()


# ─── Определение продукта по app_id и тексту (для NULL-значений) ──────────────

async def detect_product_from_texts(
    texts: list[str],
    app_ids: list[str],
    llm: "LLMClient",
) -> str:
    """
    Определяет читаемое название продукта.
    Использует app_id (package name) как основную подсказку, тексты — как подтверждение.
    Например: com.google.android.apps.meetings → Google Meet
    """
    sample_texts = texts[:15]

    # Строим подсказки для промпта
    hints: list[str] = []
    if app_ids:
        ids_str = ", ".join(f"`{a}`" for a in app_ids)
        hints.append(f"Package / Bundle ID: {ids_str}")
    if sample_texts:
        reviews_block = "\n".join(f"• {t}" for t in sample_texts)
        hints.append(f"Примеры отзывов:\n{reviews_block}")

    context = "\n\n".join(hints)

    prompt = (
        "По следующим данным определи, как называется это мобильное приложение или продукт.\n"
        "Используй package name как основной источник — он точно указывает на конкретное приложение.\n"
        "Ответь ТОЛЬКО одним коротким читаемым названием (без пояснений, без кавычек).\n"
        "Примеры:\n"
        "  com.google.android.apps.meetings → Google Meet\n"
        "  com.zhiliaoapp.musically → TikTok\n"
        "  ru.yandex.telemost → Яндекс Телемост\n\n"
        f"{context}"
    )

    raw = await llm.chat_sync(
        prompt,
        system="Ты эксперт по мобильным приложениям. Определяешь название продукта по package name и отзывам.",
    )
    name = raw.strip().strip("\"'").strip()
    return name if name else "Неизвестный продукт"


# ─── Нормализация и объединение похожих продуктов ─────────────────────────────

async def merge_similar_products(
    products_data: list[tuple[str, int, list[str], list[str]]],
    llm: "LLMClient",
) -> list[tuple[str, int, list[str], list[str]]]:
    """
    Объединяет записи об одном и том же продукте под единым названием.
    Например, 'Zoom' и 'Zoom Workplace' → 'Zoom Workplace'.
    """
    if len(products_data) <= 1:
        return products_data

    names = [p[0] for p in products_data]
    names_block = "\n".join(f"- {n}" for n in names)
    prompt = (
        f"Вот список названий продуктов из базы данных отзывов:\n{names_block}\n\n"
        "Определи группы продуктов, которые являются одним и тем же продуктом "
        "(разные версии, синонимы, варианты написания одного продукта).\n"
        "Ответь ТОЛЬКО валидным JSON-объектом (без пояснений, без markdown).\n"
        "Ключ — каноническое (основное) название, значение — массив всех вариантов "
        "(включая само каноническое название).\n"
        "Пример: {\"Zoom Workplace\": [\"Zoom\", \"Zoom Workplace\"], "
        "\"Яндекс Телемост\": [\"Яндекс Телемост\"]}"
    )

    raw = await llm.chat_sync(
        prompt,
        system="Ты нормализуешь названия продуктов. Отвечай строго в JSON.",
    )

    try:
        start = raw.index("{")
        end = raw.rindex("}") + 1
        groups: dict[str, list[str]] = json.loads(raw[start:end])
    except (ValueError, json.JSONDecodeError):
        return products_data

    variant_to_canonical: dict[str, str] = {}
    for canonical, variants in groups.items():
        for v in variants:
            variant_to_canonical[v] = canonical

    merged: dict[str, tuple[int, list[str], list[str]]] = {}
    for name, total, texts, app_ids in products_data:
        canonical = variant_to_canonical.get(name, name)
        if canonical in merged:
            prev_total, prev_texts, prev_ids = merged[canonical]
            merged_ids = list(dict.fromkeys(prev_ids + app_ids))  # уникальные, порядок сохранён
            merged[canonical] = (prev_total + total, (prev_texts + texts)[:200], merged_ids[:5])
        else:
            merged[canonical] = (total, texts, app_ids)

    result = [(name, total, texts, app_ids) for name, (total, texts, app_ids) in merged.items()]
    result.sort(key=lambda x: x[1], reverse=True)
    return result


# ─── Нормализация названий категорий (пост-фактум) ───────────────────────────

async def normalize_category_names(
    all_clusters: dict[str, list[dict]],
    llm: "LLMClient",
) -> dict[str, str]:
    """
    Принимает {product_name: [cluster]} со всех продуктов.
    Возвращает маппинг {вариант_названия: каноническое_название}.
    Объединяет похожие категории разных продуктов под одним именем.
    Если продукт один или категорий мало — возвращает пустой dict (нормализация не нужна).
    """
    all_cats: set[str] = set()
    for clusters in all_clusters.values():
        for cl in clusters:
            all_cats.add(cl["category"])

    if len(all_cats) <= 1:
        return {}

    cats_block = "\n".join(f"- {c}" for c in sorted(all_cats))
    prompt = (
        f"Вот категории проблем, самостоятельно выявленные для разных продуктов:\n{cats_block}\n\n"
        "Твоя задача: создать единый словарь, где похожие категории разных продуктов "
        "получают одинаковое каноническое название.\n"
        "Требования:\n"
        "- Объединяй только действительно схожие по смыслу категории.\n"
        "- Не объединяй разные проблемы ради единообразия.\n"
        "- Каноническое название — чёткое и короткое (3–5 слов).\n"
        "Ответь ТОЛЬКО валидным JSON-объектом (без пояснений, без markdown).\n"
        "Ключ — оригинальное название, значение — каноническое:\n"
        '{"Проблема аудио": "Проблемы со звуком и видео", '
        '"Звук и видео": "Проблемы со звуком и видео", '
        '"Нестабильная работа": "Нестабильная работа"}'
    )

    raw = await llm.chat_sync(
        prompt,
        system="Ты нормализуешь названия категорий. Отвечай строго в JSON на русском языке.",
    )

    try:
        start = raw.index("{")
        end = raw.rindex("}") + 1
        return json.loads(raw[start:end])
    except (ValueError, json.JSONDecodeError):
        return {}


def _apply_category_mapping(
    clusters: list[dict],
    mapping: dict[str, str],
) -> list[dict]:
    """
    Применяет маппинг к кластерам: переименовывает категории.
    Если два кластера получают одинаковое каноническое имя — объединяет их
    (суммирует count, берёт пример от того, у кого count выше).
    """
    merged: dict[str, dict] = {}
    for cl in clusters:
        canonical = mapping.get(cl["category"], cl["category"])
        if canonical in merged:
            existing = merged[canonical]
            if cl.get("count", 0) > existing.get("count", 0):
                example = cl.get("example", "")
            else:
                example = existing.get("example", "")
            merged[canonical] = {
                "category": canonical,
                "count": existing.get("count", 0) + cl.get("count", 0),
                "description": existing.get("description", "") or cl.get("description", ""),
                "example": example,
            }
        else:
            merged[canonical] = {**cl, "category": canonical}

    result = list(merged.values())
    result.sort(key=lambda x: x.get("count", 0), reverse=True)
    return result


# ─── Форматирование отчёта ────────────────────────────────────────────────────

def format_product_report(product_name: str, total: int, clusters: list[dict]) -> str:
    lines = [f"\n---\n\n### {product_name}  ({total} жалоб)\n"]
    if not clusters:
        lines.append("_Не удалось кластеризовать проблемы._\n")
        return "\n".join(lines)

    for i, cl in enumerate(clusters, 1):
        category = cl.get("category", "Без названия")
        count = cl.get("count", 0)
        description = cl.get("description", "")
        example = cl.get("example", "")

        pct = round(count / total * 100, 1) if total > 0 else 0

        lines.append(f"**{i}. {category}** — ~{count} упоминаний ({pct}%)")
        if description:
            lines.append(f"   {description}")
        if example:
            lines.append(f"\n   > {example}\n")
        else:
            lines.append("")

    return "\n".join(lines)


# ─── Главный async generator ──────────────────────────────────────────────────

async def analyze_complaints(
    db_path: Path,
    table_name: str,
    schema_info: list[dict],
    llm: "LLMClient",
) -> AsyncIterator[dict]:
    """
    Async generator. Yields events:
      {"type": "start",        "total_products": int}
      {"type": "product_start","product": str, "total": int}
      {"type": "product_done", "product": str, "total": int, "clusters": list, "text": str}
      {"type": "stage",        "message": str}
      {"type": "error",        "message": str}
    """
    product_col, text_col, rating_col, app_id_col = detect_columns(schema_info)

    if not (product_col and text_col and rating_col):
        yield {
            "type": "error",
            "message": (
                f"Не удалось автоматически определить нужные колонки. "
                f"Найдено: продукт={product_col}, текст={text_col}, рейтинг={rating_col}. "
                f"Укажите вопрос точнее или проверьте описания колонок в схеме."
            ),
        }
        return

    products_data = fetch_complaints(
        db_path, table_name, product_col, text_col, rating_col, app_id_col
    )

    if not products_data:
        yield {"type": "error", "message": "Отзывов с низкой оценкой (1–3 звезды) не найдено."}
        return

    # Шаг 1: определяем название для "Неизвестных продуктов"
    resolved_data: list[tuple[str, int, list[str], list[str]]] = []
    for name, total, texts, app_ids in products_data:
        if name == "Неизвестный продукт":
            hint = f" (найдены package names: {', '.join(app_ids)})" if app_ids else ""
            yield {"type": "stage", "message": f"Определяю название неизвестного продукта{hint}..."}
            detected = await detect_product_from_texts(texts, app_ids, llm)
            resolved_data.append((detected, total, texts, app_ids))
        else:
            resolved_data.append((name, total, texts, app_ids))

    # Шаг 2: объединяем похожие продукты (Zoom + Zoom Workplace и т.п.)
    if len(resolved_data) > 1:
        yield {"type": "stage", "message": "Объединяю дублирующиеся продукты..."}
        resolved_data = await merge_similar_products(resolved_data, llm)

    yield {"type": "start", "total_products": len(resolved_data)}

    # Шаг 3: независимая кластеризация каждого продукта
    clustered: list[tuple[str, int, list[dict]]] = []
    for product_name, total, texts, _app_ids in resolved_data:
        yield {"type": "product_start", "product": product_name, "total": total}
        clusters = await cluster_texts(product_name, texts, total, llm)
        clustered.append((product_name, total, clusters))

    # Шаг 4: нормализуем названия категорий между продуктами (если продуктов > 1)
    if len(clustered) > 1:
        yield {"type": "stage", "message": "Выравниваю категории между продуктами..."}
        all_clusters_map = {name: clust for name, _, clust in clustered}
        mapping = await normalize_category_names(all_clusters_map, llm)
        if mapping:
            clustered = [
                (name, total, _apply_category_mapping(clust, mapping))
                for name, total, clust in clustered
            ]

    # Шаг 5: формируем и отдаём отчёты
    for product_name, total, clusters in clustered:
        text = format_product_report(product_name, total, clusters)
        yield {"type": "product_done", "product": product_name, "total": total, "clusters": clusters, "text": text}


# ─── Кластеризация одного продукта ────────────────────────────────────────────

async def cluster_texts(
    product_name: str,
    texts: list[str],
    total: int,
    llm: "LLMClient",
) -> list[dict]:
    """
    Кластеризует жалобы одного продукта независимо.
    Счёт LLM ведётся в рамках показанной выборки; Python масштабирует на total.
    Returns [{"category": str, "count": int, "description": str, "example": str}]
    """
    sample = texts[:100]
    sample_size = len(sample)
    reviews_block = "\n".join(f"• {t}" for t in sample)

    prompt = (
        f'Проанализируй жалобы на "{product_name}". '
        f'Показано {sample_size} отзывов (всего жалоб: {total}).\n\n'
        f'Определи 3–7 основных категорий проблем. '
        f'Объединяй по смыслу, а не по совпадению слов.\n\n'
        f'Отзывы:\n{reviews_block}\n\n'
        f'Ответь ТОЛЬКО валидным JSON-массивом (без пояснений, без markdown).\n'
        f'Поле "count" — сколько из показанных {sample_size} отзывов относятся к категории '
        f'(целое число от 1 до {sample_size}):\n'
        f'[\n'
        f'  {{\n'
        f'    "category": "Краткое название проблемы (3–5 слов)",\n'
        f'    "count": число_из_{sample_size},\n'
        f'    "description": "1–2 предложения: в чём суть и как пользователи её описывают",\n'
        f'    "example": "Дословная цитата одного отзыва из показанного списка"\n'
        f'  }}\n'
        f']'
    )

    raw = await llm.chat_sync(
        prompt,
        system="Ты аналитик отзывов пользователей. Отвечай строго в формате JSON на русском языке.",
    )

    try:
        start = raw.index("[")
        end = raw.rindex("]") + 1
        clusters = json.loads(raw[start:end])
        # Масштабируем count с размера выборки на реальный total
        for cl in clusters:
            raw_count = cl.get("count", 0)
            cl["count"] = round(raw_count / sample_size * total) if sample_size > 0 else raw_count
        clusters.sort(key=lambda x: x.get("count", 0), reverse=True)
        return clusters
    except (ValueError, json.JSONDecodeError):
        return []
