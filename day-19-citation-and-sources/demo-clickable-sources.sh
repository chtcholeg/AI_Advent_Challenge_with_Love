#!/bin/bash

# Demo script for testing clickable URL sources in AI Agent

set -e

echo "🚀 Demo: Clickable Web Sources in AI Agent"
echo "=========================================="
echo ""

# Check if credentials are set
if [ -z "$GIGACHAT_CLIENT_ID" ] || [ -z "$GIGACHAT_CLIENT_SECRET" ]; then
    echo "⚠️  Warning: GigaChat credentials not set!"
    echo "   Set them using:"
    echo "   export GIGACHAT_CLIENT_ID='...'"
    echo "   export GIGACHAT_CLIENT_SECRET='...'"
    echo ""
fi

# Demo index file
INDEX_FILE="./demo-web-index.db"

echo "Step 1: Indexing a sample web page"
echo "-----------------------------------"
echo "We'll index Kotlin documentation page as an example"
echo ""

# Index a web page (using Kotlin docs as example)
echo "📥 Indexing: https://kotlinlang.org/docs/coroutines-guide.html"
./gradlew :shared:runIndexing --args="indexUrl 'https://kotlinlang.org/docs/coroutines-guide.html' $INDEX_FILE" --quiet

echo ""
echo "✅ Web page indexed successfully!"
echo ""

echo "Step 2: Verify index content"
echo "----------------------------"
./gradlew :shared:runIndexing --args="stats $INDEX_FILE" --quiet

echo ""
echo "Step 3: Test search"
echo "------------------"
echo "🔍 Searching for 'coroutine scope'"
./gradlew :shared:runIndexing --args="search $INDEX_FILE 'coroutine scope' 3" --quiet

echo ""
echo "=========================================="
echo "✅ Demo setup complete!"
echo ""
echo "Next steps:"
echo "1. Run AI Agent:    ./gradlew :ai-agent:run"
echo "2. Go to Settings:  Click ⚙️ button"
echo "3. Enable RAG:"
echo "   - RAG Mode: ON"
echo "   - Index Path: $INDEX_FILE"
echo "4. Ask a question: 'What is a coroutine scope in Kotlin?'"
echo "5. Click on [Источник 1] in the response"
echo "6. 🌐 The web page will open in your browser!"
echo ""
echo "To clean up: rm $INDEX_FILE"
