# Quick Start Guide - New MCP Servers

## Quick Test (5 minutes)

### 1. Check Available Servers
```bash
cd mcp-servers
source venv/bin/activate
python launcher.py
```

You should see:
```
Registered MCP Servers:
============================================================
Name         Port     Status     Description
------------------------------------------------------------
github       8000     free       GitHub repository data
telegram     8001     free       Telegram channel reader
weather      8002     free       Weather data (Open-Meteo)
timeservice  8003     free       Current time in any timezone/city
currency     8004     free       Currency exchange rates (Frankfurter)
------------------------------------------------------------
Total: 5 servers
```

### 2. Start New Servers
```bash
# Start both new servers
python launcher.py timeservice currency
```

### 3. Test TimeService (in another terminal)
```bash
# Check health
curl http://localhost:8003/health

# List available tools
curl http://localhost:8003/tools

# Open API docs in browser
open http://localhost:8003/docs
```

### 4. Test Currency (in another terminal)
```bash
# Check health
curl http://localhost:8004/health

# List available tools
curl http://localhost:8004/tools

# Open API docs in browser
open http://localhost:8004/docs
```

## Integration with GigaChat App

### 1. Open MCP Management Screen
In your GigaChat Multiplatform app, go to Settings → MCP Servers

### 2. Add TimeService Server
- Name: `TimeService`
- Transport Type: `SSE (Remote)`
- URL: `http://localhost:8003/sse`
- API Key: Leave empty
- Enabled: ✓

### 3. Add Currency Server
- Name: `Currency Exchange`
- Transport Type: `SSE (Remote)`
- URL: `http://localhost:8004/sse`
- API Key: Leave empty
- Enabled: ✓

### 4. Test in Chat
Ask the AI:
- "What time is it now?"
- "What time is it in Tokyo?"
- "What's the current USD to RUB exchange rate?"
- "Convert 100 EUR to USD"

## Example Conversations

### Time Queries
```
User: What time is it in New York right now?
AI: [Calls get_time_in_city tool]
    Current Time in New York, New York, United States
    ==================================================
    🕐 Local Time: 2026-01-29 03:26:13 EST
    📅 Date: 2026-01-29
    📆 Weekday: Wednesday
    🌐 Timezone: America/New_York
    ⏰ UTC Offset: -0500
```

```
User: Show me the time in multiple cities
AI: [Calls get_time_in_city for each city]
    Here's the current time across different cities:

    - Moscow: 11:26 AM (MSK, UTC+3)
    - London: 8:26 AM (GMT, UTC+0)
    - New York: 3:26 AM (EST, UTC-5)
    - Tokyo: 5:26 PM (JST, UTC+9)
    - Sydney: 7:26 PM (AEDT, UTC+11)
```

### Currency Queries
```
User: What's the exchange rate for USD to RUB?
AI: [Calls get_exchange_rate tool]
    Exchange Rate: US Dollar → Russian Ruble
    ==================================================
    💱 Rate: 1 USD = 95.23 RUB
    🔄 Inverse: 1 RUB = 0.0105 USD
    📅 Date: 2026-01-29
```

```
User: I want to exchange 500 EUR to USD
AI: [Calls convert_currency tool]
    Currency Conversion: Euro → US Dollar
    ==================================================
    💰 500.00 EUR = 542.50 USD

    💱 Exchange Rate: 1 EUR = 1.085 USD
    🧮 Calculation: 500.00 × 1.085 = 542.50
```

```
User: Show me all rates for EUR
AI: [Calls get_latest_rates tool]
    Exchange Rates for Euro (EUR)
    ==================================================
    USD (US Dollar): 1.085
    GBP (British Pound): 0.876
    JPY (Japanese Yen): 162.34
    RUB (Russian Ruble): 103.45
    CNY (Chinese Yuan): 7.823
    ... (30+ currencies)
```

## Troubleshooting

### Port Already in Use
```bash
# Check which ports are in use
python launcher.py --check

# Kill process on specific port (example: 8003)
lsof -ti:8003 | xargs kill
```

### Server Not Starting
```bash
# Check logs
cat /tmp/timeservice.log
cat /tmp/currency.log

# Try starting directly
python -m timeservice.main --no-auth --port 8003
python -m currency.main --no-auth --port 8004
```

### Connection Issues from App
1. Make sure servers are running: `python launcher.py --check`
2. Check firewall settings
3. On Android: Use `10.0.2.2` instead of `localhost`
4. Test endpoints with curl first

### Tools Not Appearing in App
1. Check MCP server is enabled in app settings
2. Restart the app after adding new servers
3. Check server connection status in MCP Management screen
4. View server logs for errors

## Advanced Usage

### Running All Servers
```bash
python launcher.py --all
```

### Running with Authentication
```bash
export MCP_API_KEY="your-secret-key"
python launcher.py timeservice currency
```

In app, add the same API key to server configuration.

### Custom Ports
```bash
# TimeService on custom port
python -m timeservice.main --no-auth --port 9003

# Currency on custom port
python -m currency.main --no-auth --port 9004
```

Update URLs in app accordingly.

## Next Steps

- Explore full documentation in `NEW_SERVERS.md`
- Check OpenAPI docs at http://localhost:8003/docs and http://localhost:8004/docs
- Add more servers following the pattern in `README.md`
- Customize tool descriptions for better AI understanding

## Support

For issues or questions:
1. Check server logs
2. Review MCP protocol documentation
3. Test with curl before integrating with app
4. Verify JSON-RPC 2.0 message format
