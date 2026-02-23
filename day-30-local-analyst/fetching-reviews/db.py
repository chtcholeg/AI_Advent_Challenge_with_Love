import sqlite3
from pathlib import Path
from contextlib import contextmanager

DB_PATH = Path(__file__).parent / "reviews.db"

SCHEMA = """
CREATE TABLE IF NOT EXISTS reviews (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    store       TEXT    NOT NULL,
    app_id      TEXT    NOT NULL,
    app_name    TEXT,
    review_id   TEXT,
    author      TEXT,
    rating      INTEGER,
    text        TEXT,
    date        TEXT,
    scraped_at  TEXT    DEFAULT (datetime('now')),
    UNIQUE(store, review_id)
);
"""


def init_db():
    with sqlite3.connect(DB_PATH) as conn:
        conn.executescript(SCHEMA)


@contextmanager
def get_conn():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def save_reviews(reviews: list[dict]) -> int:
    """Inserts reviews, skipping duplicates. Returns count of newly inserted rows."""
    inserted = 0
    with get_conn() as conn:
        for r in reviews:
            conn.execute(
                """INSERT OR IGNORE INTO reviews
                       (store, app_id, app_name, review_id, author, rating, text, date)
                   VALUES (:store, :app_id, :app_name, :review_id, :author, :rating, :text, :date)""",
                {
                    "store":     r.get("store"),
                    "app_id":    r.get("app_id"),
                    "app_name":  r.get("app_name"),
                    "review_id": r.get("review_id"),
                    "author":    r.get("author"),
                    "rating":    r.get("rating"),
                    "text":      r.get("text"),
                    "date":      r.get("date"),
                },
            )
            inserted += conn.execute("SELECT changes()").fetchone()[0]
    return inserted


def get_stats() -> list[dict]:
    with get_conn() as conn:
        rows = conn.execute(
            """
            SELECT store, app_id, app_name,
                   COUNT(*)           AS count,
                   ROUND(AVG(rating), 2) AS avg_rating,
                   MIN(date)          AS oldest,
                   MAX(date)          AS newest,
                   MAX(scraped_at)    AS last_scraped
            FROM reviews
            GROUP BY store, app_id
            ORDER BY store, app_id
            """
        ).fetchall()
    return [dict(r) for r in rows]
