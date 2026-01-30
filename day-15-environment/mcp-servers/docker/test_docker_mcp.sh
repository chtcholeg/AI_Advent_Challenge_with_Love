#!/bin/bash
# Quick test script for Docker MCP Server

echo "🧪 Docker MCP Server Test Script"
echo "================================"
echo

# Test 1: Check Docker
echo "1️⃣  Checking Docker installation..."
if command -v docker &> /dev/null; then
    docker version > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo "✅ Docker is installed and running"
    else
        echo "❌ Docker is installed but not running"
        echo "   Please start Docker Desktop or Docker daemon"
        exit 1
    fi
else
    echo "❌ Docker is not installed"
    echo "   Install from: https://docs.docker.com/get-docker/"
    exit 1
fi
echo

# Test 2: Check if server is running
echo "2️⃣  Checking if Docker MCP Server is running..."
if curl -s http://localhost:8006/sse > /dev/null 2>&1; then
    echo "✅ Server is running on port 8006"
else
    echo "❌ Server is not running"
    echo "   Start with: python launcher.py docker --no-auth"
    exit 1
fi
echo

# Test 3: Test docker_ps tool
echo "3️⃣  Testing docker_ps tool..."
RESPONSE=$(curl -s -X POST http://localhost:8006/tools/call \
    -H "Content-Type: application/json" \
    -d '{
        "method": "tools/call",
        "params": {
            "name": "docker_ps",
            "arguments": {}
        }
    }')

if echo "$RESPONSE" | grep -q "result"; then
    echo "✅ docker_ps tool works"
    echo "   Response: $(echo $RESPONSE | python3 -m json.tool 2>/dev/null | head -n 5)..."
else
    echo "❌ docker_ps tool failed"
    echo "   Response: $RESPONSE"
    exit 1
fi
echo

# Test 4: Test docker_images tool
echo "4️⃣  Testing docker_images tool..."
RESPONSE=$(curl -s -X POST http://localhost:8006/tools/call \
    -H "Content-Type: application/json" \
    -d '{
        "method": "tools/call",
        "params": {
            "name": "docker_images",
            "arguments": {}
        }
    }')

if echo "$RESPONSE" | grep -q "result"; then
    echo "✅ docker_images tool works"
else
    echo "❌ docker_images tool failed"
    exit 1
fi
echo

# Test 5: List all available tools
echo "5️⃣  Listing available tools..."
RESPONSE=$(curl -s -X POST http://localhost:8006/tools/list \
    -H "Content-Type: application/json" \
    -d '{
        "method": "tools/list",
        "params": {}
    }')

TOOL_COUNT=$(echo "$RESPONSE" | grep -o '"name"' | wc -l)
echo "✅ Found $TOOL_COUNT tools"
echo

# Success!
echo "🎉 All tests passed!"
echo
echo "Docker MCP Server is working correctly."
echo "You can now connect it to GigaChat app:"
echo "  Settings → MCP Servers → Add"
echo "  URL: http://localhost:8006/sse"
echo
echo "Try asking AI:"
echo "  - 'Show me Docker containers'"
echo "  - 'Start a PostgreSQL database'"
echo "  - 'List all Docker images'"
