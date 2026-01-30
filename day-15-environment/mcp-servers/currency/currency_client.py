"""Currency exchange rate client using Frankfurter API.

Frankfurter is a free, open-source currency data API (https://www.frankfurter.app)
- No API key required
- Updates daily from European Central Bank
- Supports 30+ currencies
- Historical rates available

API docs: https://www.frankfurter.app/docs
"""

import logging
from typing import Optional
import httpx

logger = logging.getLogger(__name__)

API_BASE_URL = "https://api.frankfurter.app"

# Currency code to name mapping (major currencies)
CURRENCY_NAMES = {
    "EUR": "Euro",
    "USD": "US Dollar",
    "GBP": "British Pound",
    "JPY": "Japanese Yen",
    "CHF": "Swiss Franc",
    "CAD": "Canadian Dollar",
    "AUD": "Australian Dollar",
    "NZD": "New Zealand Dollar",
    "CNY": "Chinese Yuan",
    "RUB": "Russian Ruble",
    "INR": "Indian Rupee",
    "BRL": "Brazilian Real",
    "ZAR": "South African Rand",
    "SEK": "Swedish Krona",
    "NOK": "Norwegian Krone",
    "DKK": "Danish Krone",
    "PLN": "Polish Zloty",
    "CZK": "Czech Koruna",
    "HUF": "Hungarian Forint",
    "TRY": "Turkish Lira",
    "MXN": "Mexican Peso",
    "KRW": "South Korean Won",
    "SGD": "Singapore Dollar",
    "HKD": "Hong Kong Dollar",
    "THB": "Thai Baht",
    "MYR": "Malaysian Ringgit",
    "IDR": "Indonesian Rupiah",
    "PHP": "Philippine Peso",
    "ISK": "Icelandic Krona",
    "ILS": "Israeli Shekel",
    "BGN": "Bulgarian Lev",
    "RON": "Romanian Leu",
}


class CurrencyClient:
    """Client for Frankfurter currency exchange API."""

    def __init__(self):
        self.http_client = httpx.AsyncClient(timeout=30.0)

    async def get_supported_currencies(self) -> dict:
        """Get list of all supported currency codes.

        Returns:
            dict mapping currency code to full name
        """
        try:
            response = await self.http_client.get(f"{API_BASE_URL}/currencies")
            response.raise_for_status()
            return response.json()
        except Exception as e:
            logger.error(f"Failed to get currencies: {e}")
            return CURRENCY_NAMES  # Fallback to hardcoded list

    async def get_latest_rates(
        self, base: str = "USD", currencies: Optional[list[str]] = None
    ) -> Optional[dict]:
        """Get latest exchange rates for a base currency.

        Args:
            base: Base currency code (default: USD)
            currencies: Optional list of target currencies (all if None)

        Returns:
            dict with rates and metadata, or None on error
        """
        try:
            params = {"from": base.upper()}
            if currencies:
                params["to"] = ",".join([c.upper() for c in currencies])

            response = await self.http_client.get(f"{API_BASE_URL}/latest", params=params)
            response.raise_for_status()
            data = response.json()

            return {
                "base": data["base"],
                "date": data["date"],
                "rates": data["rates"],
            }
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                logger.error(f"Invalid currency code: {base}")
                return None
            logger.error(f"API error: {e}")
            return None
        except Exception as e:
            logger.error(f"Failed to get latest rates: {e}")
            return None

    async def get_exchange_rate(self, from_currency: str, to_currency: str) -> Optional[dict]:
        """Get exchange rate between two currencies.

        Args:
            from_currency: Source currency code
            to_currency: Target currency code

        Returns:
            dict with rate information, or None on error
        """
        try:
            from_curr = from_currency.upper()
            to_curr = to_currency.upper()

            response = await self.http_client.get(
                f"{API_BASE_URL}/latest",
                params={"from": from_curr, "to": to_curr},
            )
            response.raise_for_status()
            data = response.json()

            rate = data["rates"].get(to_curr)
            if rate is None:
                return None

            return {
                "from": from_curr,
                "to": to_curr,
                "rate": rate,
                "date": data["date"],
                "inverse_rate": 1 / rate if rate != 0 else 0,
            }
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                logger.error(f"Invalid currency code: {from_currency} or {to_currency}")
                return None
            logger.error(f"API error: {e}")
            return None
        except Exception as e:
            logger.error(f"Failed to get exchange rate: {e}")
            return None

    async def convert_amount(
        self, amount: float, from_currency: str, to_currency: str
    ) -> Optional[dict]:
        """Convert amount from one currency to another.

        Args:
            amount: Amount to convert
            from_currency: Source currency code
            to_currency: Target currency code

        Returns:
            dict with conversion details, or None on error
        """
        try:
            from_curr = from_currency.upper()
            to_curr = to_currency.upper()

            response = await self.http_client.get(
                f"{API_BASE_URL}/latest",
                params={"amount": amount, "from": from_curr, "to": to_curr},
            )
            response.raise_for_status()
            data = response.json()

            converted = data["rates"].get(to_curr)
            if converted is None:
                return None

            rate = converted / amount if amount != 0 else 0

            return {
                "amount": amount,
                "from": from_curr,
                "to": to_curr,
                "result": converted,
                "rate": rate,
                "date": data["date"],
            }
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                logger.error(f"Invalid currency code: {from_currency} or {to_currency}")
                return None
            logger.error(f"API error: {e}")
            return None
        except Exception as e:
            logger.error(f"Failed to convert currency: {e}")
            return None

    def get_currency_name(self, code: str) -> str:
        """Get full name for currency code.

        Args:
            code: Currency code (e.g., 'USD')

        Returns:
            Full currency name or the code if unknown
        """
        return CURRENCY_NAMES.get(code.upper(), code.upper())

    async def close(self):
        """Close HTTP client."""
        await self.http_client.aclose()
