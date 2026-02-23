"""
RuStore scraper — uses the official RuStore Public API.
Docs: https://www.rustore.ru/help/work-with-rustore-api/api-feedback-process/get-feedback/

Auth: requires a JWE token from RuStore Console.
  1. Go to https://console.rustore.ru → Company → API RuStore
  2. Generate a key pair
  3. Call POST https://public-api.rustore.ru/public/auth/ with signed JWT to get a token
  4. Set env variable: export RUSTORE_TOKEN="<your jwe token>"
  OR pass --rustore-token on the command line.

Note: The token expires in 900 seconds. The token gives access to apps linked to
your company account. Competitor app reviews may be inaccessible via the official API.
"""

import os
import time
import requests

BASE_URL = "https://public-api.rustore.ru"
PAGE_SIZE = 100  # max allowed by the API


class RuStoreAuthError(RuntimeError):
    pass


def _get_token(token: str | None = None) -> str:
    t = token or os.environ.get("RUSTORE_TOKEN", "").strip()
    if not t:
        raise RuStoreAuthError(
            "\n"
            "RuStore requires an API token. Steps to get one:\n"
            "  1. Go to https://console.rustore.ru → Company → API RuStore\n"
            "  2. Generate a key pair\n"
            "  3. Sign a JWT with your private key, POST it to:\n"
            "       https://public-api.rustore.ru/public/auth/\n"
            "  4. Use the returned JWE token:\n"
            "       export RUSTORE_TOKEN='<token>'\n"
            "  Then re-run: python main.py fetch --store rustore --app <package>\n"
        )
    return t


def _make_headers(token: str) -> dict:
    return {
        "Content-Type": "application/json",
        "Public-Token": token,
    }


def fetch_reviews(app_id: str, count: int = 500, token: str | None = None) -> list[dict]:
    auth_token = _get_token(token)
    headers = _make_headers(auth_token)

    all_reviews: list[dict] = []
    page = 0

    while len(all_reviews) < count:
        try:
            resp = requests.get(
                f"{BASE_URL}/public/v1/application/{app_id}/comment",
                params={"page": page, "size": min(PAGE_SIZE, count - len(all_reviews))},
                headers=headers,
                timeout=10,
            )
        except requests.RequestException as e:
            print(f"  [rustore] network error on page {page}: {e}")
            break

        if resp.status_code == 401:
            raise RuStoreAuthError(
                "RuStore returned 401 Unauthorized. "
                "Your RUSTORE_TOKEN may be expired (tokens last 900 s). "
                "Get a fresh token and re-run."
            )

        resp.raise_for_status()
        data = resp.json()

        if data.get("code") != "OK":
            print(f"  [rustore] API error: {data.get('message')} (code={data.get('code')})")
            break

        content = data.get("body") or []
        if not content:
            break  # no more pages

        for r in content:
            all_reviews.append(
                {
                    "store":     "rustore",
                    "app_id":    app_id,
                    "app_name":  None,  # not returned in review list
                    "review_id": str(r.get("commentId", "")),
                    "author":    r.get("userName"),
                    "rating":    r.get("appRating"),
                    "text":      r.get("commentText"),
                    "date":      r.get("commentDateIso") or r.get("commentDate"),
                }
            )

        page += 1
        time.sleep(0.3)

    return all_reviews[:count]
