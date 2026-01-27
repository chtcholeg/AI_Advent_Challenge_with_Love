#!/bin/bash

echo "=== Testing SSE Connection to MCP Server ==="
echo ""

echo "1. Testing GET /sse endpoint..."
echo "   Expected: SSE stream with 'event: endpoint' and 'data: /message?sessionId=...'"
echo ""

# Start SSE connection in background and capture output
timeout 5s curl -N -H "Accept: text/event-stream" http://localhost:8081/sse 2>&1 &
SSE_PID=$!

sleep 2

# Extract session ID if available
SESSION_ID=$(curl -N -H "Accept: text/event-stream" http://localhost:8081/sse 2>&1 | head -10 | grep "data: /message" | sed 's/.*sessionId=\([^&]*\).*/\1/')

if [ -z "$SESSION_ID" ]; then
    echo "❌ Failed to get session ID from SSE endpoint"
    echo "   Check if server is running on http://localhost:8081"
    kill $SSE_PID 2>/dev/null
    exit 1
else
    echo "✅ Received session ID: $SESSION_ID"
    echo ""
fi

kill $SSE_PID 2>/dev/null

echo "2. Testing POST /message endpoint with session ID..."
echo ""

# Test initialize request
RESPONSE=$(curl -s -X POST \
    -H "Content-Type: application/json" \
    "http://localhost:8081/message?sessionId=$SESSION_ID" \
    -d '{
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {
                "name": "test-client",
                "version": "1.0.0"
            }
        }
    }')

if echo "$RESPONSE" | grep -q "result"; then
    echo "✅ Successfully sent message and received response:"
    echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
    echo ""
    echo "=== All tests passed! SSE transport is working correctly. ==="
else
    echo "❌ Failed to get valid response:"
    echo "$RESPONSE"
    echo ""
    echo "=== Tests failed. Check server logs for details. ==="
    exit 1
fi
