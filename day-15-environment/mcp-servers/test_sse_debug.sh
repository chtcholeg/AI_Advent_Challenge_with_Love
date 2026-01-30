#!/bin/bash

# Debug SSE connection to MCP servers
# Usage: ./test_sse_debug.sh [server_name]
# Example: ./test_sse_debug.sh telegram

# Default to telegram server
SERVER=${1:-telegram}

# Define server port based on name
case "$SERVER" in
    github)
        PORT=8000
        ;;
    telegram)
        PORT=8001
        ;;
    weather)
        PORT=8002
        ;;
    *)
        echo "❌ Unknown server: $SERVER"
        echo "   Available servers: github (8000), telegram (8001), weather (8002)"
        echo "   Usage: $0 [github|telegram|weather]"
        exit 1
        ;;
esac

echo "=== Debug SSE Connection ==="
echo "Server: $SERVER (port $PORT)"
echo ""

echo "Step 1: Testing basic connectivity..."
curl -s http://localhost:$PORT/health | python3 -m json.tool
echo ""

echo "Step 2: Getting server info..."
curl -s http://localhost:$PORT/ | python3 -m json.tool
echo ""

echo "Step 3: Opening SSE connection (will timeout after 5 seconds)..."
echo "Expected output: event: endpoint, data: /message?sessionId=..."
echo ""

timeout 5s curl -v -N -H "Accept: text/event-stream" -H "Cache-Control: no-cache" http://localhost:$PORT/sse 2>&1

echo ""
echo ""
echo "If you see 'event: endpoint' with sessionId above, the SSE endpoint is working correctly."
echo "If you see 401 Unauthorized, the server requires authentication."
echo "If you see connection refused, the server is not running."
echo ""
echo "To start the server: python launcher.py $SERVER --no-auth"
