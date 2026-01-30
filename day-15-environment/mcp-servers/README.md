# MCP Servers Collection

A comprehensive collection of 6 MCP (Model Context Protocol) servers for AI integrations, demonstrating MCP Composition capabilities.

## Overview (Day 14)

This collection showcases how multiple specialized MCP servers can work together as a unified ecosystem. Each server provides distinct capabilities while sharing common infrastructure for consistency and maintainability.

### Server Collection

| Server | Port | Status | Description |
|--------|------|--------|-------------|
| **github** | 8000 | ✅ Stable | GitHub repository data and operations |
| **telegram** | 8001 | ✅ Stable | Telegram channel reader with monitoring |
| **weather** | 8002 | ✅ NEW | Weather data via Open-Meteo API |
| **timeservice** | 8003 | ✅ NEW | Time in any timezone or city |
| **currency** | 8004 | ✅ NEW | Currency exchange rates (30+ currencies) |
| **fileops** | 8005 | ✅ NEW | Secure file system operations |

**Total:** 6 servers • **Tools:** 20+ AI-callable functions

## Structure

```
mcp-servers/
├── shared/                  # Common MCP components
│   ├── mcp_protocol.py      # JSON-RPC 2.0 protocol handler
│   ├── sse_transport.py     # Server-Sent Events transport
│   └── models.py            # ToolResult, BaseTool classes
│
├── github/                  # GitHub MCP (port 8000)
│   ├── main.py              # FastAPI server
│   ├── github_client.py     # GitHub API wrapper
│   ├── tools.py             # MCP tool definitions
│   └── README.md            # Server documentation
│
├── telegram/                # Telegram MCP (port 8001)
│   ├── main.py
│   ├── telegram_client.py
│   ├── tools.py
│   ├── setup_session.py     # OTP authentication
│   └── README.md
│
├── weather/                 # Weather MCP (port 8002, NEW)
│   ├── main.py
│   ├── weather_client.py    # Open-Meteo API wrapper
│   ├── tools.py             # get_current_weather, get_weather_forecast
│   └── README.md
│
├── timeservice/             # TimeService MCP (port 8003, NEW)
│   ├── main.py
│   ├── time_client.py       # Python datetime + zoneinfo
│   ├── tools.py             # 3 time-related tools
│   └── README.md
│
├── currency/                # Currency MCP (port 8004, NEW)
│   ├── main.py
│   ├── currency_client.py   # Frankfurter API wrapper
│   ├── tools.py             # 3 currency tools
│   └── README.md
│
├── fileops/                 # FileOps MCP (port 8005, NEW)
│   ├── main.py
│   ├── file_client.py       # File operations handler
│   ├── tools.py             # 8 file operation tools
│   ├── README.md
│   ├── EXAMPLES.md          # Usage examples
│   └── QUICKSTART.md
│
├── launcher.py              # Centralized launcher (NEW)
├── pyproject.toml           # Python package configuration
├── requirements.txt         # Shared dependencies
├── README.md                # This file (UPDATED Day 14)
├── QUICKSTART.md            # 5-minute quick start (NEW)
├── NEW_SERVERS.md           # Weather, TimeService, Currency docs (NEW)
├── FILEOPS_SUMMARY.md       # FileOps implementation summary (NEW)
├── FILEOPS_IMPLEMENTATION.md # FileOps technical details (NEW)
├── TESTING.md               # Testing guide for all servers (NEW)
└── TESTING_WITH_AUTH.md     # Testing with API key authentication (NEW Day 15)
```

## Quick Start (5 Minutes)

### 1. Installation

```bash
cd mcp-servers

# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install all servers
pip install -e ".[all]"

# Or install specific servers
pip install -e ".[weather,currency,timeservice]"
```

### 2. Start All Servers

**Recommended:** Use the centralized launcher

```bash
# Start all 6 servers at once (no authentication)
python launcher.py --all --no-auth
```

Expected output:
```
Starting 6 server(s)...
Press Ctrl+C to stop all servers

[github] Starting GitHub MCP on port 8000...
[telegram] Starting Telegram MCP on port 8001...
[weather] Starting Weather MCP on port 8002...
[timeservice] Starting TimeService MCP on port 8003...
[currency] Starting Currency MCP on port 8004...
[fileops] Starting FileOps MCP on port 8005...

All servers ready!
```

### 3. Verify Servers

```bash
# In another terminal
curl http://localhost:8002/health  # Weather
curl http://localhost:8003/health  # TimeService
curl http://localhost:8004/health  # Currency
curl http://localhost:8005/health  # FileOps
```

### 4. Add to GigaChat App

In app Settings → MCP Servers → Add:
- Weather: `http://localhost:8002/sse`
- TimeService: `http://localhost:8003/sse`
- Currency: `http://localhost:8004/sse`
- FileOps: `http://localhost:8005/sse`

Leave API Key empty (auth disabled with `--no-auth`).

For detailed quick start guide, see [QUICKSTART.md](QUICKSTART.md).

## Running Servers

### Using Launcher (Recommended)

The launcher provides centralized port management and can start multiple servers:

```bash
# List all servers and their ports
python launcher.py

# Start a single server
python launcher.py telegram

# Start multiple servers
python launcher.py telegram github

# Start all servers
python launcher.py --all

# Start all without authentication
python launcher.py --all --no-auth

# Check port availability
python launcher.py --check[README_RU.md](../../day-13-reminder-mcp/README_RU.md)
```

Example output:
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
fileops      8005     free       File system operations (read, write, search)
------------------------------------------------------------
Total: 6 servers
```

### Running Individual Servers

Each server can also be run directly:

```bash
python -m telegram.main --no-auth --port 8001
python -m github.main --no-auth --port 8000
python -m weather.main --no-auth --port 8002
python -m timeservice.main --no-auth --port 8003
python -m currency.main --no-auth --port 8004
python -m fileops.main --no-auth --port 8005
```

Or using installed commands:

```bash
mcp-telegram --no-auth
mcp-github --no-auth
mcp-weather --no-auth
mcp-timeservice --no-auth
mcp-currency --no-auth
mcp-fileops --no-auth
```

## Environment Variables

### Common
- `MCP_API_KEY` - Server API key (required unless `--no-auth`)
  - See [TESTING_WITH_AUTH.md](TESTING_WITH_AUTH.md) for authentication testing guide
- `HOST` - Bind address (default: 0.0.0.0)
- `PORT` - Bind port (varies by server)

### Telegram
- `TELEGRAM_API_ID` - From https://my.telegram.org
- `TELEGRAM_API_HASH` - From https://my.telegram.org
- `TELEGRAM_SESSION_FILE` - Session file path (default: telegram_session)

### GitHub
- `GITHUB_TOKEN` - Personal access token (optional, raises rate limit)

### Weather
No additional configuration required. Uses Open-Meteo (free, no API key).

### TimeService
No additional configuration required. Uses Python's built-in datetime and zoneinfo.

### Currency
No additional configuration required. Uses Frankfurter API (free, no API key).

### FileOps
- `FILEOPS_ROOT_DIR` - Root directory for file operations (default: current directory)
- `FILEOPS_MAX_FILE_SIZE` - Maximum file size in bytes (default: 10MB)
- `FILEOPS_MAX_SEARCH_RESULTS` - Maximum search results (default: 100)

## MCP Composition Use Cases

The power of MCP Composition lies in combining multiple servers in a single conversation:

### Example 1: Multi-Tool Query
```
User: What's the weather in Tokyo right now and what time is it there?

AI: [Calls weather.get_current_weather(city="Tokyo")]
    [Calls timeservice.get_time_in_city(city="Tokyo")]

    In Tokyo, it's currently 5°C with partly cloudy skies.
    The local time is 17:26 JST (UTC+9).
```

### Example 2: Currency + Time Context
```
User: If I exchange 1000 USD to EUR, what rate do I get?
      And when do European markets open?

AI: [Calls currency.convert_currency(amount=1000, from="USD", to="EUR")]
    [Calls timeservice.get_time_in_city(city="Frankfurt")]

    1000 USD = 921.66 EUR at rate 1.085.
    Frankfurt time is 9:26 AM. Markets opened at 9:00 AM.
```

### Example 3: File + Data Operations
```
User: Create a weather report file for 5 major cities

AI: [Calls weather.get_current_weather for each city]
    [Calls fileops.write_file(path="weather_report.txt", content="...")]

    Created weather_report.txt with current conditions for:
    - New York: 2°C, Clear
    - London: 8°C, Rainy
    - Tokyo: 5°C, Cloudy
    - Moscow: -10°C, Snowy
    - Sydney: 28°C, Sunny
```

## Adding a New Server

Follow the established pattern for consistency:

### 1. Create Server Directory Structure
```
mcp-servers/myserver/
├── __init__.py          # Package initialization
├── config.py            # Environment variable configuration
├── main.py              # FastAPI server entry point
├── tools.py             # MCP tool definitions
├── my_client.py         # External API/service client
└── README.md            # Server-specific documentation
```

### 2. Import Shared Components
```python
from shared import ToolResult, BaseTool, McpProtocolHandler, SseTransport
```

### 3. Implement Tools
```python
class MyTool(BaseTool):
    name = "my_tool"
    description = "Does something useful"
    input_schema = {
        "type": "object",
        "properties": {
            "param": {"type": "string", "description": "Parameter description"}
        },
        "required": ["param"]
    }

    async def execute(self, arguments: dict) -> ToolResult:
        # Your implementation
        result = await self.client.do_something(arguments["param"])
        return ToolResult(result)
```

### 4. Register in Launcher
Edit `launcher.py`:
```python
SERVERS: dict[str, ServerConfig] = {
    # ... existing servers ...
    "myserver": ServerConfig(
        name="myserver",
        module="myserver.main",
        port=8006,  # Next available port
        description="My awesome integration",
    ),
}
```

### 5. Add Entry Point
Edit `pyproject.toml`:
```toml
[project.scripts]
mcp-myserver = "myserver.main:main"

[project.optional-dependencies]
myserver = [
    "httpx>=0.27.0",
    "your-dependencies-here",
]
```

### 6. Test Your Server
```bash
pip install -e ".[myserver]"
python launcher.py myserver --no-auth
curl http://localhost:8006/health
```

## API Endpoints

Each server exposes:

| Endpoint | Description |
|----------|-------------|
| `GET /` | Server info |
| `GET /health` | Health check |
| `GET /sse` | SSE connection for MCP |
| `POST /message?sessionId=...` | Send MCP message |
| `GET /tools` | List available tools |
| `GET /docs` | OpenAPI documentation |
