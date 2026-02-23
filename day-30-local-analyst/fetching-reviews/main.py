"""
Usage examples:

  # Google Play — no auth needed
  python main.py fetch --store google_play --app com.example.app --count 500

  # RuStore official API — requires RUSTORE_TOKEN (own apps only)
  export RUSTORE_TOKEN="<jwe_token>"
  python main.py fetch --store rustore --app ru.example.app --count 500

  # RuStore web scraping — any app, no token, uses Playwright
  python main.py fetch --store rustore_web --app ru.competitor.app --count 200

  # Show stats for all collected data
  python main.py stats

  # Query raw data with sqlite3
  sqlite3 reviews.db "SELECT store, app_id, rating, text FROM reviews LIMIT 10;"
"""

import argparse
import sys

from db import init_db, save_reviews, get_stats

STORES = ("google_play", "rustore", "rustore_web")


def cmd_fetch(args):
    store = args.store
    app_id = args.app
    count = args.count

    print(f"Fetching up to {count} reviews for '{app_id}' from {store}...")

    try:
        if store == "google_play":
            from scrapers.google_play import fetch_reviews
            fetched = fetch_reviews(app_id, count=count)
        elif store == "rustore":
            from scrapers.rustore import fetch_reviews, RuStoreAuthError
            try:
                fetched = fetch_reviews(app_id, count=count, token=getattr(args, "rustore_token", None))
            except RuStoreAuthError as e:
                print(e, file=sys.stderr)
                sys.exit(1)
        elif store == "rustore_web":
            from scrapers.rustore_web import fetch_reviews
            fetched = fetch_reviews(app_id, count=count)
        else:
            print(f"Unknown store: {store}", file=sys.stderr)
            sys.exit(1)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    print(f"  Fetched : {len(fetched)}")

    inserted = save_reviews(fetched)
    print(f"  New in DB: {inserted}  (duplicates skipped: {len(fetched) - inserted})")


def cmd_stats(_args):
    rows = get_stats()
    if not rows:
        print("DB is empty. Run: python main.py fetch --store <store> --app <package>")
        return

    header = f"{'Store':<15} {'App ID':<40} {'App Name':<25} {'Count':>6} {'Avg':>5}"
    print(header)
    print("-" * len(header))
    for r in rows:
        print(
            f"{r['store']:<15} "
            f"{r['app_id']:<40} "
            f"{(r['app_name'] or ''):<25} "
            f"{r['count']:>6} "
            f"{(r['avg_rating'] or 0):>5.2f}"
        )


def main():
    init_db()

    parser = argparse.ArgumentParser(
        description="App store review scraper — Google Play & RuStore → SQLite",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    sub = parser.add_subparsers(dest="command", metavar="COMMAND")

    fetch_p = sub.add_parser("fetch", help="Download reviews and save to DB")
    fetch_p.add_argument(
        "--store", required=True, choices=STORES,
        help="google_play | rustore (official API, needs token) | rustore_web (Playwright, any app)",
    )
    fetch_p.add_argument("--app", required=True, metavar="PACKAGE", help="App package name")
    fetch_p.add_argument("--count", type=int, default=500, metavar="N", help="Max reviews to fetch (default: 500)")
    fetch_p.add_argument("--rustore-token", metavar="TOKEN", help="RuStore JWE token (or set RUSTORE_TOKEN env var)")
    fetch_p.set_defaults(func=cmd_fetch)

    stats_p = sub.add_parser("stats", help="Show summary of collected reviews")
    stats_p.set_defaults(func=cmd_stats)

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        return

    args.func(args)


if __name__ == "__main__":
    main()
