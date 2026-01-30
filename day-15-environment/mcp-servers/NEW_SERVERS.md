# New MCP Servers: TimeService & Currency

Two new MCP servers have been added to provide time and currency exchange functionality.

## TimeService MCP Server

**Port:** 8003
**Module:** `timeservice`
**Data Source:** Python built-in datetime and zoneinfo

### Tools

#### 1. get_current_time
Get current UTC time with detailed information.

**Parameters:** None

**Returns:**
- Current UTC time in ISO 8601 format
- Date components (year, month, day)
- Time components (hour, minute, second)
- Weekday name
- Unix timestamp

**Example response:**
```
Current UTC Time
==================================================

🕐 Time: 2026-01-29 08:26:13 UTC
📅 Date: 2026-01-29
🔢 Components: 08:26:13
📆 Weekday: Wednesday
⏱️  Unix Timestamp: 1738142773
🌐 ISO 8601: 2026-01-29T08:26:13+00:00
```

#### 2. get_time_in_timezone
Get current time in a specific IANA timezone.

**Parameters:**
- `timezone` (string, required): IANA timezone name (e.g., "America/New_York", "Europe/Moscow", "Asia/Tokyo")

**Returns:**
- Local time in specified timezone
- UTC offset
- All time components
- ISO 8601 format

**Example response:**
```
Current Time in Europe/Moscow
==================================================

🕐 Local Time: 2026-01-29 11:26:13 MSK
📅 Date: 2026-01-29
🔢 Components: 11:26:13
📆 Weekday: Wednesday
🌐 Timezone: Europe/Moscow
⏰ UTC Offset: +0300
⏱️  Unix Timestamp: 1738142773
🌐 ISO 8601: 2026-01-29T11:26:13+03:00
```

#### 3. get_time_in_city
Get current local time for a city (auto-detects timezone).

**Parameters:**
- `city` (string, required): City name (e.g., "Moscow", "New York", "Tokyo", "London")

**Returns:**
- Local time in city's timezone
- Location details (country, region)
- Geographic coordinates
- All time components

**Example response:**
```
Current Time in Moscow, Moscow, Russia
==================================================

🕐 Local Time: 2026-01-29 11:26:13 MSK
📅 Date: 2026-01-29
🔢 Components: 11:26:13
📆 Weekday: Wednesday

🌐 Timezone: Europe/Moscow
⏰ UTC Offset: +0300
📍 Coordinates: 55.75°, 37.62°

⏱️  Unix Timestamp: 1738142773
🌐 ISO 8601: 2026-01-29T11:26:13+03:00
```

### Usage

```bash
# Start server
python launcher.py timeservice

# Or directly
python -m timeservice.main --no-auth --port 8003

# Test via curl
curl http://localhost:8003/health
```

---

## CurrencyExchange MCP Server

**Port:** 8004
**Module:** `currency`
**Data Source:** European Central Bank (via Frankfurter API)

Supports 30+ currencies: USD, EUR, GBP, JPY, CHF, CAD, AUD, NZD, CNY, RUB, INR, BRL, ZAR, SEK, NOK, DKK, PLN, CZK, HUF, TRY, MXN, KRW, SGD, HKD, THB, MYR, IDR, PHP, ISK, ILS, BGN, RON

### Tools

#### 1. get_exchange_rate
Get current exchange rate between two currencies.

**Parameters:**
- `from_currency` (string, required): Source currency code (e.g., "USD", "EUR", "GBP")
- `to_currency` (string, required): Target currency code (e.g., "RUB", "JPY", "CNY")

**Returns:**
- Exchange rate
- Inverse rate
- Last update date
- Data source

**Example response:**
```
Exchange Rate: US Dollar → Russian Ruble
==================================================

💱 Rate: 1 USD = 95.234567 RUB
🔄 Inverse: 1 RUB = 0.010500 USD

📅 Date: 2026-01-29
🏦 Source: European Central Bank (via Frankfurter API)
```

#### 2. convert_currency
Convert an amount from one currency to another.

**Parameters:**
- `amount` (number, required): Amount to convert (e.g., 100, 50.5)
- `from_currency` (string, required): Source currency code
- `to_currency` (string, required): Target currency code

**Returns:**
- Converted amount
- Exchange rate used
- Calculation details
- Last update date

**Example response:**
```
Currency Conversion: US Dollar → Russian Ruble
==================================================

💰 100.00 USD = 9523.46 RUB

💱 Exchange Rate: 1 USD = 95.234567 RUB
🧮 Calculation: 100.00 × 95.234567 = 9523.46

📅 Date: 2026-01-29
🏦 Source: European Central Bank (via Frankfurter API)
```

#### 3. get_latest_rates
Get all current exchange rates for a base currency.

**Parameters:**
- `base_currency` (string, required): Base currency code (default: "USD")
- `target_currencies` (string, optional): Comma-separated list of target currencies (e.g., "EUR,GBP,JPY")

**Returns:**
- All exchange rates relative to base currency
- Last update date
- Total number of currencies

**Example response:**
```
Exchange Rates for US Dollar (USD)
==================================================

  AUD (Australian Dollar): 1.523456
  BGN (Bulgarian Lev): 1.834567
  BRL (Brazilian Real): 5.678901
  ...
  RUB (Russian Ruble): 95.234567
  ...

📅 Date: 2026-01-29
📊 Total currencies: 30
🏦 Source: European Central Bank (via Frankfurter API)
```

### Usage

```bash
# Start server
python launcher.py currency

# Or directly
python -m currency.main --no-auth --port 8004

# Test via curl
curl http://localhost:8004/health
```

---

## Running Both Servers Together

```bash
# Start both new servers
python launcher.py timeservice currency

# Start all servers
python launcher.py --all

# Check port status
python launcher.py --check
```

## Integration with Kotlin App

To integrate these servers with the GigaChat Multiplatform app:

1. **Add to MCP Management Screen:**
   - TimeService: `http://localhost:8003/sse`
   - Currency: `http://localhost:8004/sse`

2. **Configure in app settings:**
   - Name: "TimeService" / "Currency Exchange"
   - Transport: SSE (Remote)
   - URL: Server SSE endpoint
   - API Key: Leave empty (auth disabled)

3. **AI will be able to:**
   - Ask about current time in any timezone or city
   - Get real-time currency exchange rates
   - Convert amounts between currencies
   - Compare multiple currencies at once

## API Documentation

Each server provides OpenAPI documentation:

- TimeService: http://localhost:8003/docs
- Currency: http://localhost:8004/docs

## Technical Details

### TimeService
- **Dependencies:** Python built-in modules (datetime, zoneinfo), httpx (for geocoding)
- **Geocoding:** Uses Open-Meteo API to resolve city names to coordinates
- **Timezones:** Supports all IANA timezones (400+ zones)
- **No API key required**

### Currency
- **Dependencies:** httpx
- **API:** Frankfurter (https://www.frankfurter.app)
- **Update frequency:** Daily from European Central Bank
- **Rate limit:** None (free tier sufficient for normal use)
- **No API key required**

## Examples

### TimeService Examples
```json
// Get current UTC time
{"method": "tools/call", "params": {"name": "get_current_time", "arguments": {}}}

// Get time in New York
{"method": "tools/call", "params": {"name": "get_time_in_timezone", "arguments": {"timezone": "America/New_York"}}}

// Get time in Tokyo
{"method": "tools/call", "params": {"name": "get_time_in_city", "arguments": {"city": "Tokyo"}}}
```

### Currency Examples
```json
// Get USD to RUB rate
{"method": "tools/call", "params": {"name": "get_exchange_rate", "arguments": {"from_currency": "USD", "to_currency": "RUB"}}}

// Convert 100 EUR to USD
{"method": "tools/call", "params": {"name": "convert_currency", "arguments": {"amount": 100, "from_currency": "EUR", "to_currency": "USD"}}}

// Get all rates for EUR
{"method": "tools/call", "params": {"name": "get_latest_rates", "arguments": {"base_currency": "EUR"}}}
```
