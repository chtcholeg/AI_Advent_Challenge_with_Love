"""CurrencyExchange MCP tool definitions.

Tools for currency exchange operations:
- get_exchange_rate: Get exchange rate between two currencies
- convert_currency: Convert amount from one currency to another
- get_latest_rates: Get all current rates for a base currency
"""

import logging
from shared import ToolResult, BaseTool
from .currency_client import CurrencyClient

logger = logging.getLogger(__name__)


class CurrencyTool(BaseTool):
    """Base class for CurrencyExchange MCP tools."""

    def __init__(self, client: CurrencyClient):
        self.client = client

    async def execute(self, arguments: dict) -> ToolResult:
        raise NotImplementedError


# ---------------------------------------------------------------------------
# Tool: get_exchange_rate
# ---------------------------------------------------------------------------

class GetExchangeRateTool(CurrencyTool):
    name = "get_exchange_rate"
    description = (
        "Get current exchange rate between two currencies. "
        "Returns the rate, inverse rate, and last update date. "
        "Supports 30+ major world currencies (USD, EUR, GBP, JPY, CNY, RUB, etc.)."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "from_currency": {
                "type": "string",
                "description": "Source currency code (e.g., 'USD', 'EUR', 'GBP')",
            },
            "to_currency": {
                "type": "string",
                "description": "Target currency code (e.g., 'RUB', 'JPY', 'CNY')",
            },
        },
        "required": ["from_currency", "to_currency"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        from_currency = arguments.get("from_currency", "").strip().upper()
        to_currency = arguments.get("to_currency", "").strip().upper()

        if not from_currency or not to_currency:
            return ToolResult("Missing required parameters: from_currency and to_currency", is_error=True)

        try:
            rate_info = await self.client.get_exchange_rate(from_currency, to_currency)

            if not rate_info:
                return ToolResult(
                    f"Invalid currency codes: '{from_currency}' or '{to_currency}'. "
                    f"Use standard 3-letter codes like USD, EUR, GBP, JPY, RUB, CNY.",
                    is_error=True,
                )

            from_name = self.client.get_currency_name(rate_info["from"])
            to_name = self.client.get_currency_name(rate_info["to"])

            lines = [
                f"Exchange Rate: {from_name} → {to_name}",
                "=" * 50,
                "",
                f"💱 Rate: 1 {rate_info['from']} = {rate_info['rate']:.6f} {rate_info['to']}",
                f"🔄 Inverse: 1 {rate_info['to']} = {rate_info['inverse_rate']:.6f} {rate_info['from']}",
                "",
                f"📅 Date: {rate_info['date']}",
                f"🏦 Source: European Central Bank (via Frankfurter API)",
            ]

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"GetExchangeRateTool error: {e}")
            return ToolResult(
                f"Failed to get exchange rate for {from_currency}/{to_currency}: {e}",
                is_error=True,
            )


# ---------------------------------------------------------------------------
# Tool: convert_currency
# ---------------------------------------------------------------------------

class ConvertCurrencyTool(CurrencyTool):
    name = "convert_currency"
    description = (
        "Convert an amount from one currency to another using current exchange rates. "
        "Returns converted amount, exchange rate, and calculation details. "
        "Supports all major world currencies."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "amount": {
                "type": "number",
                "description": "Amount to convert (e.g., 100, 50.5)",
            },
            "from_currency": {
                "type": "string",
                "description": "Source currency code (e.g., 'USD', 'EUR', 'GBP')",
            },
            "to_currency": {
                "type": "string",
                "description": "Target currency code (e.g., 'RUB', 'JPY', 'CNY')",
            },
        },
        "required": ["amount", "from_currency", "to_currency"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        amount = arguments.get("amount")
        from_currency = arguments.get("from_currency", "").strip().upper()
        to_currency = arguments.get("to_currency", "").strip().upper()

        if amount is None:
            return ToolResult("Missing required parameter: amount", is_error=True)
        if not from_currency or not to_currency:
            return ToolResult("Missing required parameters: from_currency and to_currency", is_error=True)

        try:
            amount = float(amount)
            if amount <= 0:
                return ToolResult("Amount must be a positive number", is_error=True)
        except (ValueError, TypeError):
            return ToolResult(f"Invalid amount: '{amount}'. Must be a number.", is_error=True)

        try:
            conversion = await self.client.convert_amount(amount, from_currency, to_currency)

            if not conversion:
                return ToolResult(
                    f"Invalid currency codes: '{from_currency}' or '{to_currency}'. "
                    f"Use standard 3-letter codes like USD, EUR, GBP, JPY, RUB, CNY.",
                    is_error=True,
                )

            from_name = self.client.get_currency_name(conversion["from"])
            to_name = self.client.get_currency_name(conversion["to"])

            lines = [
                f"Currency Conversion: {from_name} → {to_name}",
                "=" * 50,
                "",
                f"💰 {conversion['amount']:.2f} {conversion['from']} = {conversion['result']:.2f} {conversion['to']}",
                "",
                f"💱 Exchange Rate: 1 {conversion['from']} = {conversion['rate']:.6f} {conversion['to']}",
                f"🧮 Calculation: {conversion['amount']:.2f} × {conversion['rate']:.6f} = {conversion['result']:.2f}",
                "",
                f"📅 Date: {conversion['date']}",
                f"🏦 Source: European Central Bank (via Frankfurter API)",
            ]

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"ConvertCurrencyTool error: {e}")
            return ToolResult(
                f"Failed to convert {amount} {from_currency} to {to_currency}: {e}",
                is_error=True,
            )


# ---------------------------------------------------------------------------
# Tool: get_latest_rates
# ---------------------------------------------------------------------------

class GetLatestRatesTool(CurrencyTool):
    name = "get_latest_rates"
    description = (
        "Get all current exchange rates for a base currency. "
        "Returns rates for 30+ currencies relative to the base currency. "
        "Useful for comparing multiple currencies at once."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "base_currency": {
                "type": "string",
                "description": "Base currency code (e.g., 'USD', 'EUR', 'RUB'). Default: USD",
            },
            "target_currencies": {
                "type": "string",
                "description": "Optional: comma-separated list of target currencies (e.g., 'EUR,GBP,JPY'). If not specified, returns all available currencies.",
            },
        },
        "required": ["base_currency"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        base_currency = arguments.get("base_currency", "USD").strip().upper()
        target_currencies_str = arguments.get("target_currencies", "").strip()

        target_currencies = None
        if target_currencies_str:
            target_currencies = [c.strip().upper() for c in target_currencies_str.split(",")]

        try:
            rates_data = await self.client.get_latest_rates(base_currency, target_currencies)

            if not rates_data:
                return ToolResult(
                    f"Invalid base currency: '{base_currency}'. "
                    f"Use standard 3-letter codes like USD, EUR, GBP, JPY, RUB, CNY.",
                    is_error=True,
                )

            base_name = self.client.get_currency_name(rates_data["base"])
            rates = rates_data["rates"]

            lines = [
                f"Exchange Rates for {base_name} ({rates_data['base']})",
                "=" * 50,
                "",
            ]

            # Sort rates by currency code for consistent output
            sorted_rates = sorted(rates.items())

            for currency_code, rate in sorted_rates:
                currency_name = self.client.get_currency_name(currency_code)
                lines.append(f"  {currency_code} ({currency_name}): {rate:.6f}")

            lines.extend([
                "",
                f"📅 Date: {rates_data['date']}",
                f"📊 Total currencies: {len(rates)}",
                f"🏦 Source: European Central Bank (via Frankfurter API)",
            ])

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"GetLatestRatesTool error: {e}")
            return ToolResult(
                f"Failed to get latest rates for {base_currency}: {e}",
                is_error=True,
            )


# ---------------------------------------------------------------------------
# Registry
# ---------------------------------------------------------------------------

def get_all_tools(client: CurrencyClient) -> list[CurrencyTool]:
    """Return all registered CurrencyExchange MCP tools."""
    return [
        GetExchangeRateTool(client),
        ConvertCurrencyTool(client),
        GetLatestRatesTool(client),
    ]
