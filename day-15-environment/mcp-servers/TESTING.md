# MCP Server Testing Scripts

This directory contains shell scripts for testing MCP server functionality.

## Prerequisites

- MCP server must be running
- `curl` and `python3` must be installed
- Server must be started with `--no-auth` flag for testing

## Available Test Scripts

### 1. test_sse_connection.sh

Tests basic SSE connection and message sending functionality.

**Usage:**
```bash
./test_sse_connection.sh [server_name]
```

**Examples:**
```bash
./test_sse_connection.sh telegram  # Test telegram server (default)
./test_sse_connection.sh github    # Test github server
./test_sse_connection.sh weather   # Test weather server
```

**What it tests:**
- SSE endpoint connectivity
- Session ID extraction
- Initialize request/response
- Basic JSON-RPC 2.0 protocol

### 2. test_sse_debug.sh

Debug script for troubleshooting SSE connection issues.

**Usage:**
```bash
./test_sse_debug.sh [server_name]
```

**Examples:**
```bash
./test_sse_debug.sh telegram
./test_sse_debug.sh github
```

**What it checks:**
- Health endpoint (`/health`)
- Server info endpoint (`/`)
- SSE connection with verbose output
- Authentication status

### 3. test_sse_integration.sh

Full integration test covering the complete MCP protocol flow.

**Usage:**
```bash
./test_sse_integration.sh [server_name]
```

**Examples:**
```bash
./test_sse_integration.sh telegram
./test_sse_integration.sh weather
```

**What it tests:**
- SSE connection establishment
- Initialize handshake
- Tools list retrieval
- Complete MCP protocol workflow

## Server Ports

| Server   | Port |
|----------|------|
| github   | 8000 |
| telegram | 8001 |
| weather  | 8002 |

## Starting Servers for Testing

Before running tests, start the desired server:

```bash
# Start a single server
python launcher.py telegram --no-auth

# Start all servers
python launcher.py --all --no-auth

# Check server status
python launcher.py --check
```

## Troubleshooting

### Connection Refused
**Symptom:** `curl: (7) Failed to connect to localhost port XXXX: Connection refused`

**Solution:** Server is not running. Start with:
```bash
python launcher.py [server_name] --no-auth
```

### 401 Unauthorized
**Symptom:** `HTTP/1.1 401 Unauthorized`

**Solution:** Server requires authentication. Use `--no-auth` flag:
```bash
python launcher.py [server_name] --no-auth
```

### Empty Session ID
**Symptom:** `❌ Failed to get session ID from SSE endpoint`

**Solutions:**
1. Check server logs for errors
2. Verify correct port is being used
3. Ensure SSE endpoint is enabled
4. Try debug script: `./test_sse_debug.sh [server_name]`

### Tool List Empty
**Symptom:** Test passes but shows `Available tools: 0`

**Solutions:**
1. Check server implementation has tools registered
2. Verify server started successfully without errors
3. Check server logs for tool initialization messages

## Expected Output

### Successful Test Run

```
=== Testing SSE Connection to MCP Server ===
Server: telegram (port 8001)

1. Testing GET /sse endpoint...
   Expected: SSE stream with 'event: endpoint' and 'data: /message?sessionId=...'

✅ Received session ID: abc123...

2. Testing POST /message endpoint with session ID...

✅ Successfully sent message and received response:
{
    "jsonrpc": "2.0",
    "id": 1,
    "result": {
        "protocolVersion": "2024-11-05",
        "serverInfo": {...}
    }
}

=== All tests passed! SSE transport is working correctly. ===
```

## Continuous Testing

For continuous integration or monitoring, you can run all tests in sequence:

```bash
#!/bin/bash
for server in github telegram weather; do
    echo "Testing $server server..."
    ./test_sse_integration.sh $server || exit 1
done
echo "All servers passed!"
```

## Contributing

When adding new MCP servers:
1. Add server port to the `SERVER_PORTS` array in each script
2. Update this documentation with the new server
3. Test all three scripts with the new server
