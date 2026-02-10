#!/bin/bash

# Test script to verify README.md search functionality

echo "=== Testing README.md Search ==="
echo ""

# Check current directory
echo "📂 Current directory:"
pwd
echo ""

# Check if README.md exists in current directory
echo "📄 README.md in current directory:"
if [ -f "README.md" ]; then
    echo "✅ Found: $(pwd)/README.md"
    echo "📊 Size: $(wc -l < README.md) lines"
else
    echo "❌ Not found in current directory"
fi
echo ""

# Check parent directory
echo "📄 README.md in parent directory:"
if [ -f "../README.md" ]; then
    echo "✅ Found: $(cd .. && pwd)/README.md"
    echo "📊 Size: $(wc -l < ../README.md) lines"
else
    echo "❌ Not found in parent directory"
fi
echo ""

# Check Git root
echo "🔍 Searching for Git root (.git folder):"
current_dir=$(pwd)
while [ "$current_dir" != "/" ]; do
    if [ -d "$current_dir/.git" ]; then
        echo "✅ Git root found: $current_dir"
        if [ -f "$current_dir/README.md" ]; then
            echo "✅ README.md found in Git root"
            echo "📊 Size: $(wc -l < "$current_dir/README.md") lines"
            echo "📝 First lines:"
            head -5 "$current_dir/README.md" | sed 's/^/   /'
        else
            echo "❌ README.md not found in Git root"
        fi
        break
    fi
    current_dir=$(dirname "$current_dir")
done

if [ "$current_dir" = "/" ]; then
    echo "❌ Git root not found"
fi
echo ""

# Summary
echo "=== Summary ==="
echo "Working directory: $(pwd)"
echo "README.md search will use:"
echo "  1. Git root detection (.git folder)"
echo "  2. Hierarchy search (up to 5 levels)"
echo ""
echo "💡 Tip: Run the application to test /help command"
echo "   ./gradlew :ai-agent:run"
