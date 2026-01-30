#!/bin/bash

# Test full MCP SSE integration flow
# Usage: ./test_sse_integration.sh [server_name]
# Example: ./test_sse_integration.sh telegram

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

echo "=== MCP SSE Integration Test ==="
echo "Server: $SERVER (port $PORT)"
echo ""

# Step 1: Establish SSE connection and capture session ID
echo "Step 1: Establishing SSE connection..."
SSE_OUTPUT=$(timeout 3s curl -N -H "Accept: text/event-stream" http://localhost:$PORT/sse 2>&1)

SESSION_ID=$(echo "$SSE_OUTPUT" | grep "data: /message" | sed 's/.*sessionId=\([^&^ ]*\).*/\1/')

if [ -z "$SESSION_ID" ]; then
    echo "❌ Failed to extract session ID"
    echo "   Check if $SERVER server is running on http://localhost:$PORT"
    echo "   Start with: python launcher.py $SERVER --no-auth"
    echo ""
    echo "SSE Output:"
    echo "$SSE_OUTPUT"
    exit 1
fi

echo "✅ Connected! Session ID: $SESSION_ID"
echo ""

# Step 2: Send initialize request to the session
echo "Step 2: Sending initialize request..."
RESPONSE=$(curl -s -X POST \
    -H "Content-Type: application/json" \
    "http://localhost:$PORT/message?sessionId=$SESSION_ID" \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}')

if echo "$RESPONSE" | grep -q '"result"'; then
    echo "✅ Initialize successful!"
    echo ""
    echo "Response:"
    echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
    echo ""

    # Step 3: List available tools
    echo "Step 3: Listing available tools..."
    TOOLS_RESPONSE=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        "http://localhost:$PORT/message?sessionId=$SESSION_ID" \
        -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}')

    if echo "$TOOLS_RESPONSE" | grep -q '"tools"'; then
        echo "✅ Tools list retrieved!"
        echo ""
        TOOL_COUNT=$(echo "$TOOLS_RESPONSE" | grep -o '"name"' | wc -l)
        echo "Available tools: $TOOL_COUNT"
        echo ""
        echo "Tool details:"
        echo "$TOOLS_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$TOOLS_RESPONSE"
        echo ""
        echo "=== ✅ All tests passed! SSE integration is working correctly. ==="
    else
        echo "⚠️  Tools list request returned unexpected response:"
        echo "$TOOLS_RESPONSE"
    fi
else
    echo "❌ Initialize failed!"
    echo "Response: $RESPONSE"
    exit 1
fi
