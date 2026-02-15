#!/bin/bash
# Test script for CRM smart search

echo "🧪 Testing CRM Smart Search"
echo "============================"
echo ""

# Check if server is running
if ! curl -s http://localhost:8011/health > /dev/null 2>&1; then
    echo "❌ CRM MCP Server is not running!"
    echo "   Start it with: cd mcp-servers && ./START.sh"
    exit 1
fi

echo "✓ CRM MCP Server is running"
echo ""

# Test health endpoint
echo "1. Testing health endpoint..."
HEALTH=$(curl -s http://localhost:8011/health)
echo "   Response: $HEALTH"
echo ""

# Test queries
echo "2. Testing search queries..."
echo ""

TEST_QUERIES=(
    "авторизация не работает"
    "ошибка индексирования pdf"
    "mcp сервер"
    "медленно работает"
    "креденшелы"
)

for query in "${TEST_QUERIES[@]}"; do
    echo "   Query: '$query'"

    # Create JSON request
    JSON=$(cat <<EOF
{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
        "name": "search_tickets",
        "arguments": {
            "query": "$query"
        }
    }
}
EOF
)

    # Make request
    RESPONSE=$(curl -s -X POST "http://localhost:8011/message?sessionId=test" \
        -H "Content-Type: application/json" \
        -d "$JSON")

    # Extract result
    RESULT=$(echo "$RESPONSE" | python3 -c "import sys, json; data = json.load(sys.stdin); print(data.get('result', {}).get('content', [{}])[0].get('text', 'No result'))" 2>/dev/null)

    # Show first line of result
    FIRST_LINE=$(echo "$RESULT" | head -1)
    echo "   → $FIRST_LINE"
    echo ""
done

echo "============================"
echo "✓ Test completed!"
echo ""
echo "To see detailed logs:"
echo "  tail -f /tmp/crm-mcp.log | grep -E 'search_tickets|SearchService'"
