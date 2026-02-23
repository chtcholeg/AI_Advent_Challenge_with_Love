from google_play_scraper import reviews, app as gp_app, Sort


def fetch_app_name(app_id: str, lang: str = "ru", country: str = "ru") -> str | None:
    try:
        info = gp_app(app_id, lang=lang, country=country)
        return info.get("title")
    except Exception:
        return None


def fetch_reviews(
    app_id: str,
    count: int = 500,
    lang: str = "ru",
    country: str = "ru",
) -> list[dict]:
    app_name = fetch_app_name(app_id, lang=lang, country=country)

    result, _ = reviews(
        app_id,
        lang=lang,
        country=country,
        sort=Sort.NEWEST,
        count=count,
    )

    return [
        {
            "store":     "google_play",
            "app_id":    app_id,
            "app_name":  app_name,
            "review_id": r["reviewId"],
            "author":    r.get("userName"),
            "rating":    r.get("score"),
            "text":      r.get("content"),
            "date":      r["at"].isoformat() if r.get("at") else None,
        }
        for r in result
    ]
