#!/bin/bash

echo "=== Debug SSE Connection ==="
echo ""

echo "Step 1: Testing basic connectivity..."
curl -s http://localhost:8081/health | python3 -m json.tool
echo ""

echo "Step 2: Getting server info..."
curl -s http://localhost:8081/ | python3 -m json.tool
echo ""

echo "Step 3: Opening SSE connection (will timeout after 5 seconds)..."
echo "Expected output: event: endpoint, data: /message?sessionId=..."
echo ""

timeout 5s curl -v -N -H "Accept: text/event-stream" -H "Cache-Control: no-cache" http://localhost:8081/sse 2>&1

echo ""
echo ""
echo "If you see 'event: endpoint' with sessionId above, the SSE endpoint is working correctly."
echo "If you see 401 Unauthorized, the server requires authentication."
echo "If you see connection refused, the server is not running."
