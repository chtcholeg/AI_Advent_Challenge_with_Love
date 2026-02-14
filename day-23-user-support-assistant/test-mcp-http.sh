#!/bin/bash
# Test script for HTTP MCP servers

echo "🧪 Testing MCP HTTP Servers"
echo "=============================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test function
test_endpoint() {
    local name=$1
    local url=$2
    local expected=$3

    echo -n "Testing $name... "

    response=$(curl -s -w "\n%{http_code}" "$url" 2>/dev/null)
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" = "200" ]; then
        if echo "$body" | grep -q "$expected"; then
            echo -e "${GREEN}✓ OK${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠ Unexpected response${NC}"
            echo "Expected to contain: $expected"
            echo "Got: $body"
            return 1
        fi
    else
        echo -e "${RED}✗ FAIL${NC} (HTTP $http_code)"
        return 1
    fi
}

# Check if servers are running
echo "1. Checking if servers are running..."
echo ""

test_endpoint "Git MCP root" "http://localhost:8010/" "Git MCP Server"
test_endpoint "Git MCP health" "http://localhost:8010/health" "status"
test_endpoint "Git MCP tools" "http://localhost:8010/tools" "tools"

echo ""

test_endpoint "CRM MCP root" "http://localhost:8011/" "CRM MCP Server"
test_endpoint "CRM MCP health" "http://localhost:8011/health" "status"
test_endpoint "CRM MCP tools" "http://localhost:8011/tools" "tools"

echo ""
echo "=============================="
echo "2. Testing tool availability..."
echo ""

# Test Git tools
echo "Git MCP Tools:"
git_tools=$(curl -s http://localhost:8010/tools 2>/dev/null | grep -o '"name":"[^"]*"' | cut -d'"' -f4)
if [ -n "$git_tools" ]; then
    echo "$git_tools" | while read tool; do
        echo "  - $tool"
    done
    echo -e "${GREEN}✓ Git tools available${NC}"
else
    echo -e "${RED}✗ No Git tools found${NC}"
fi

echo ""

# Test CRM tools
echo "CRM MCP Tools:"
crm_tools=$(curl -s http://localhost:8011/tools 2>/dev/null | grep -o '"name":"[^"]*"' | cut -d'"' -f4)
if [ -n "$crm_tools" ]; then
    echo "$crm_tools" | while read tool; do
        echo "  - $tool"
    done
    echo -e "${GREEN}✓ CRM tools available${NC}"
else
    echo -e "${RED}✗ No CRM tools found${NC}"
fi

echo ""
echo "=============================="
echo "3. Summary"
echo ""

# Count results
git_status=$(curl -s http://localhost:8010/health 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
crm_status=$(curl -s http://localhost:8011/health 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

echo "Git MCP Server (port 8010): $git_status"
echo "CRM MCP Server (port 8011): $crm_status"
echo ""

if [ "$git_status" = "healthy" ] && [ "$crm_status" = "healthy" ]; then
    echo -e "${GREEN}✓ All systems operational!${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Copy config: cp support-docs/config/mcp-config.json ~/.ai-agent/"
    echo "  2. Run AI Agent: ./gradlew :ai-agent:run"
    echo "  3. Enable both MCP servers in Settings"
else
    echo -e "${RED}✗ Some systems are not healthy${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Check if servers are running: cd mcp-servers && ./START.sh"
    echo "  2. Check logs: tail -f /tmp/git-mcp.log /tmp/crm-mcp.log"
    echo "  3. Check ports: lsof -i :8010 -i :8011"
fi
