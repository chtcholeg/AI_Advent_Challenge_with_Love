#!/bin/bash
# Test script for Git MCP Server

set -e

echo "================================="
echo "Git MCP Server Test Script"
echo "================================="
echo ""

BASE_URL="http://localhost:8010"

echo "1. Testing health endpoint..."
curl -s "$BASE_URL/health" | python3 -m json.tool
echo ""
echo "✓ Health check passed"
echo ""

echo "2. Testing server info..."
curl -s "$BASE_URL/" | python3 -m json.tool
echo ""
echo "✓ Server info retrieved"
echo ""

echo "3. Testing tools list..."
TOOLS=$(curl -s "$BASE_URL/tools")
TOOL_COUNT=$(echo "$TOOLS" | python3 -c "import sys, json; print(len(json.load(sys.stdin)['tools']))")
echo "Found $TOOL_COUNT tools:"
echo "$TOOLS" | python3 -c "import sys, json; [print(f\"  - {t['name']}: {t['description'][:60]}...\") for t in json.load(sys.stdin)['tools']]"
echo ""
echo "✓ Tools list retrieved"
echo ""

echo "================================="
echo "All tests passed! ✓"
echo "================================="
echo ""
echo "Server is ready to use with AI Agent"
echo "Add this URL to AI Agent MCP settings:"
echo "  $BASE_URL/sse"
