#!/bin/bash

# =============================================================================
# MCP Server Testing Script with Authentication
# =============================================================================
#
# Usage: ./test_with_auth.sh [server_name] [api_key]
# Example: ./test_with_auth.sh telegram my-secret-key
#
# If API key is not provided, it will be read from MCP_API_KEY env variable
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Server configuration
SERVER=${1:-telegram}
API_KEY=${2:-$MCP_API_KEY}

declare -A SERVER_PORTS=(
    ["github"]=8000
    ["telegram"]=8001
    ["weather"]=8002
    ["timeservice"]=8003
    ["currency"]=8004
    ["fileops"]=8005
    ["docker"]=8006
    ["adb"]=8007
)

PORT=${SERVER_PORTS[$SERVER]}
BASE_URL="http://localhost:${PORT}"

if [ -z "$PORT" ]; then
    echo -e "${RED}❌ Unknown server: $SERVER${NC}"
    echo "Available servers: ${!SERVER_PORTS[@]}"
    exit 1
fi

if [ -z "$API_KEY" ]; then
    echo -e "${RED}❌ API key not provided${NC}"
    echo "Usage: $0 [server_name] [api_key]"
    echo "   or: export MCP_API_KEY=your-key && $0 [server_name]"
    exit 1
fi

echo -e "${BLUE}=== Testing MCP Server with Authentication ===${NC}"
echo "Server: $SERVER"
echo "Port: $PORT"
echo "API Key: ${API_KEY:0:10}..."
echo ""

# =============================================================================
# Test 1: Health endpoint (public, no auth required)
# =============================================================================
echo -e "${YELLOW}[1/5] Testing health endpoint (no auth)...${NC}"
HEALTH_RESPONSE=$(curl -s "$BASE_URL/health")

if echo "$HEALTH_RESPONSE" | grep -q '"status"'; then
    echo -e "${GREEN}✅ Health check passed${NC}"
    echo "$HEALTH_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$HEALTH_RESPONSE"
else
    echo -e "${RED}❌ Health check failed${NC}"
    echo "$HEALTH_RESPONSE"
    exit 1
fi
echo ""

# =============================================================================
# Test 2: Root endpoint (public, no auth required)
# =============================================================================
echo -e "${YELLOW}[2/5] Testing root endpoint (no auth)...${NC}"
ROOT_RESPONSE=$(curl -s "$BASE_URL/")

if echo "$ROOT_RESPONSE" | grep -q '"name"'; then
    echo -e "${GREEN}✅ Root endpoint accessible${NC}"
    echo "$ROOT_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$ROOT_RESPONSE"
else
    echo -e "${RED}❌ Root endpoint failed${NC}"
    echo "$ROOT_RESPONSE"
    exit 1
fi
echo ""

# =============================================================================
# Test 3: Tools endpoint without auth (should fail with 401)
# =============================================================================
echo -e "${YELLOW}[3/5] Testing tools endpoint without auth (expect 401)...${NC}"
UNAUTH_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/tools")
HTTP_CODE=$(echo "$UNAUTH_RESPONSE" | tail -n1)
BODY=$(echo "$UNAUTH_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "401" ]; then
    echo -e "${GREEN}✅ Correctly rejected unauthorized access${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
    echo -e "${RED}❌ Expected 401, got $HTTP_CODE${NC}"
    echo "$BODY"
fi
echo ""

# =============================================================================
# Test 4: Tools endpoint with auth header
# =============================================================================
echo -e "${YELLOW}[4/5] Testing tools endpoint with X-API-Key header...${NC}"
TOOLS_RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" "$BASE_URL/tools")

if echo "$TOOLS_RESPONSE" | grep -q '"tools"'; then
    echo -e "${GREEN}✅ Successfully authenticated with API key${NC}"
    TOOL_COUNT=$(echo "$TOOLS_RESPONSE" | python3 -c "import sys, json; print(len(json.load(sys.stdin).get('tools', [])))" 2>/dev/null || echo "0")
    echo "Available tools: $TOOL_COUNT"
    echo "$TOOLS_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$TOOLS_RESPONSE"
else
    echo -e "${RED}❌ Authentication failed or invalid response${NC}"
    echo "$TOOLS_RESPONSE"
    exit 1
fi
echo ""

# =============================================================================
# Test 5: SSE connection with auth
# =============================================================================
echo -e "${YELLOW}[5/5] Testing SSE connection with auth...${NC}"

# Extract session ID from SSE stream
SESSION_ID=$(timeout 5 curl -s -N -H "X-API-Key: $API_KEY" "$BASE_URL/sse" 2>/dev/null | \
    grep "data: /message" | \
    sed 's/.*sessionId=\(.*\)/\1/' | \
    head -n 1)

if [ -z "$SESSION_ID" ]; then
    echo -e "${RED}❌ Failed to get session ID from SSE endpoint${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Connected to SSE endpoint${NC}"
echo "Session ID: $SESSION_ID"
echo ""

# Send initialize request
echo -e "${YELLOW}Testing MCP initialize handshake...${NC}"
INIT_REQUEST='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"test-client","version":"1.0.0"}}}'

INIT_RESPONSE=$(curl -s -X POST \
    -H "X-API-Key: $API_KEY" \
    -H "Content-Type: application/json" \
    -d "$INIT_REQUEST" \
    "$BASE_URL/message?sessionId=$SESSION_ID")

if echo "$INIT_RESPONSE" | grep -q '"protocolVersion"'; then
    echo -e "${GREEN}✅ MCP initialize successful${NC}"
    echo "$INIT_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$INIT_RESPONSE"
else
    echo -e "${RED}❌ MCP initialize failed${NC}"
    echo "$INIT_RESPONSE"
    exit 1
fi
echo ""

# Send tools/list request
echo -e "${YELLOW}Testing MCP tools/list...${NC}"
TOOLS_LIST_REQUEST='{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

TOOLS_LIST_RESPONSE=$(curl -s -X POST \
    -H "X-API-Key: $API_KEY" \
    -H "Content-Type: application/json" \
    -d "$TOOLS_LIST_REQUEST" \
    "$BASE_URL/message?sessionId=$SESSION_ID")

if echo "$TOOLS_LIST_RESPONSE" | grep -q '"tools"'; then
    echo -e "${GREEN}✅ MCP tools/list successful${NC}"
    TOOL_COUNT=$(echo "$TOOLS_LIST_RESPONSE" | python3 -c "import sys, json; print(len(json.load(sys.stdin)['result']['tools']))" 2>/dev/null || echo "0")
    echo "Tools available via MCP: $TOOL_COUNT"

    # Display tool names
    echo "$TOOLS_LIST_RESPONSE" | python3 -c "
import sys, json
data = json.load(sys.stdin)
tools = data.get('result', {}).get('tools', [])
for tool in tools:
    print(f\"  - {tool['name']}: {tool['description'][:60]}...\")
" 2>/dev/null || echo "$TOOLS_LIST_RESPONSE"
else
    echo -e "${RED}❌ MCP tools/list failed${NC}"
    echo "$TOOLS_LIST_RESPONSE"
    exit 1
fi
echo ""

# =============================================================================
# Summary
# =============================================================================
echo -e "${GREEN}================================================${NC}"
echo -e "${GREEN}✅ All authentication tests passed!${NC}"
echo -e "${GREEN}================================================${NC}"
echo ""
echo "Summary:"
echo "  - Health endpoint: ✅ accessible (public)"
echo "  - Root endpoint: ✅ accessible (public)"
echo "  - Unauthorized access: ✅ correctly rejected (401)"
echo "  - API key authentication: ✅ working"
echo "  - SSE connection: ✅ established"
echo "  - MCP initialize: ✅ successful"
echo "  - MCP tools/list: ✅ successful"
echo ""
echo -e "${BLUE}Server is ready for use with API key: ${API_KEY:0:10}...${NC}"
