#!/bin/bash
# Test script for FileOps MCP Server

set -e

BASE_URL="http://localhost:8005"
SESSION_ID="test-session-$(date +%s)"

echo "Testing FileOps MCP Server"
echo "============================"
echo ""

# Function to call MCP tool via SSE transport
call_tool() {
    local tool_name=$1
    local arguments=$2

    echo "→ Testing: $tool_name"

    # First, establish SSE connection (in background)
    curl -s -N "$BASE_URL/sse?sessionId=$SESSION_ID" > /dev/null 2>&1 &
    SSE_PID=$!
    sleep 0.5  # Wait for connection

    # Send tool call
    response=$(curl -s -X POST "$BASE_URL/message?sessionId=$SESSION_ID" \
        -H "Content-Type: application/json" \
        -d "{
            \"jsonrpc\": \"2.0\",
            \"id\": 1,
            \"method\": \"tools/call\",
            \"params\": {
                \"name\": \"$tool_name\",
                \"arguments\": $arguments
            }
        }")

    # Kill SSE connection
    kill $SSE_PID 2>/dev/null || true

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"
    echo ""
}

# Test 1: Create directory
echo "1. Create test directory"
call_tool "create_directory" '{"path": "test_files"}'

# Test 2: Write file
echo "2. Write file"
call_tool "write_file" '{
    "path": "test_files/hello.txt",
    "content": "Hello, World!\nThis is a test file."
}'

# Test 3: Read file
echo "3. Read file"
call_tool "read_file" '{"path": "test_files/hello.txt"}'

# Test 4: Append to file
echo "4. Append to file"
call_tool "write_file" '{
    "path": "test_files/hello.txt",
    "content": "\nAppended line!",
    "append": true
}'

# Test 5: Get file info
echo "5. Get file info"
call_tool "get_file_info" '{"path": "test_files/hello.txt"}'

# Test 6: List directory
echo "6. List directory"
call_tool "list_directory" '{"path": "test_files"}'

# Test 7: Create multiple files for search test
echo "7. Create test files for search"
call_tool "write_file" '{"path": "test_files/test1.py", "content": "def main():\n    print(\"Hello\")"}'
call_tool "write_file" '{"path": "test_files/test2.py", "content": "def helper():\n    return 42"}'
call_tool "write_file" '{"path": "test_files/data.json", "content": "{\"key\": \"value\"}"}'

# Test 8: Search files by pattern
echo "8. Search files by pattern (*.py)"
call_tool "search_files" '{"pattern": "*.py", "path": "test_files"}'

# Test 9: Search content
echo "9. Search content (def)"
call_tool "search_content" '{
    "query": "def",
    "path": "test_files",
    "file_pattern": "*.py"
}'

# Test 10: Delete file
echo "10. Delete file"
call_tool "delete_file" '{"path": "test_files/data.json"}'

# Test 11: List directory again (should not have data.json)
echo "11. List directory after deletion"
call_tool "list_directory" '{"path": "test_files"}'

echo ""
echo "✓ All tests completed!"
echo ""
echo "Cleanup: rm -rf test_files"
