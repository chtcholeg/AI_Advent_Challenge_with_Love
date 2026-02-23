"""
RuStore web scraper — works for ANY app, no API token needed.

RuStore renders reviews server-side (SSR), so plain HTTP requests return
full HTML with review data. No browser automation required.

URL pattern:
  Page 1: https://www.rustore.ru/catalog/app/{packageName}/reviews
  Page N: https://www.rustore.ru/catalog/app/{packageName}/reviews/page-{N}

Usage:
  python main.py fetch --store rustore_web --app com.vkontakte.android --count 200
"""

import re
import time
import requests

BASE = "https://www.rustore.ru/catalog/app/{app_id}/reviews"
PAGE_URL = "https://www.rustore.ru/catalog/app/{app_id}/reviews/page-{page}"
PAGE_SIZE = 15  # RuStore shows 15 reviews per page

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "ru-RU,ru;q=0.9",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
}


def _fetch_html(url: str) -> str:
    resp = requests.get(url, headers=HEADERS, timeout=15)
    resp.raise_for_status()
    return resp.text


def _get_app_name(html: str) -> str | None:
    # Reviews page title: "💬 N отзывов о приложении APP_NAME в RuStore"
    m = re.search(r"о приложении\s+(.+?)\s+в RuStore", html)
    if m:
        return m.group(1).strip()
    # Main page title: "APP_NAME — скачать ... в RuStore"
    m = re.search(r"<title>([^<]+?)\s*—\s*скачать", html)
    if m:
        return m.group(1).strip()
    return None


def _get_total_pages(html: str) -> int:
    pages = re.findall(r"/reviews/page-(\d+)", html)
    if pages:
        return max(int(p) for p in pages)
    return 1


def _parse_reviews(html: str, app_id: str, app_name: str | None) -> list[dict]:
    """Extract review dicts from a reviews page HTML."""
    reviews = []

    # Split HTML into per-card chunks on data-review-id boundaries
    parts = re.split(r"(?=<[^>]+data-review-id=\")", html)

    for part in parts:
        id_match = re.search(r'data-review-id="(\d+)"', part)
        if not id_match:
            continue

        review_id = id_match.group(1)

        # Rating = number of filled star SVG icons in this card
        # Stop counting at the first "review_likes" boundary to avoid counting other cards
        card_body = part.split('data-testid="review_likes"')[0]
        rating = len(re.findall(r"star_new_full", card_body))

        # Author — span with class giIK213i (CSS Module hash, stable per build)
        author_m = re.search(r'class="giIK213i"[^>]*>(.*?)</span>', card_body)
        author = author_m.group(1).strip() if author_m else None

        # Date — span with class X7K5JxYD
        date_m = re.search(r'class="X7K5JxYD"[^>]*>(.*?)</span>', card_body)
        date_raw = date_m.group(1).strip() if date_m else None

        # Review text — <p> with class YOMjlbtO
        text_m = re.search(r'class="YOMjlbtO"[^>]*>(.*?)</p>', card_body, re.DOTALL)
        text = re.sub(r"<[^>]+>", "", text_m.group(1)).strip() if text_m else None

        if not text:
            continue  # skip developer reply cards and empty entries

        reviews.append(
            {
                "store":    "rustore",
                "app_id":   app_id,
                "app_name": app_name,
                "review_id": review_id,
                "author":   author,
                "rating":   rating or None,
                "text":     text,
                "date":     date_raw,
            }
        )

    return reviews


def fetch_reviews(app_id: str, count: int = 200) -> list[dict]:
    all_reviews: list[dict] = []
    seen_ids: set[str] = set()

    # ── Page 1 ────────────────────────────────────────────────────────────────
    try:
        html = _fetch_html(BASE.format(app_id=app_id))
    except requests.HTTPError as e:
        print(f"  [rustore_web] HTTP error: {e}")
        print(f"  Make sure '{app_id}' exists on RuStore.")
        return []

    app_name = _get_app_name(html)
    total_pages = _get_total_pages(html)
    print(f"  [rustore_web] found {total_pages} page(s), app: {app_name}")

    for r in _parse_reviews(html, app_id, app_name):
        if r["review_id"] not in seen_ids:
            seen_ids.add(r["review_id"])
            all_reviews.append(r)

    # ── Pages 2..N ────────────────────────────────────────────────────────────
    page = 2
    while len(all_reviews) < count and page <= total_pages:
        url = PAGE_URL.format(app_id=app_id, page=page)
        try:
            html = _fetch_html(url)
        except Exception as e:
            print(f"  [rustore_web] error on page {page}: {e}")
            break

        batch = _parse_reviews(html, app_id, app_name)
        if not batch:
            break

        for r in batch:
            if r["review_id"] not in seen_ids:
                seen_ids.add(r["review_id"])
                all_reviews.append(r)

        page += 1
        time.sleep(0.5)

    return all_reviews[:count]
