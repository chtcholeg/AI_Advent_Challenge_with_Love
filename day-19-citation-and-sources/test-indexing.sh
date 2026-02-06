#!/bin/bash

# Test script for document indexing

set -e

echo "🚀 Document Indexing Test Script"
echo "================================"
echo

# Set credentials (replace with your actual credentials or use from local.properties)
if [ -f "local.properties" ]; then
    export GIGACHAT_CLIENT_ID=$(grep "gigachat.clientId" local.properties | cut -d'=' -f2)
    export GIGACHAT_CLIENT_SECRET=$(grep "gigachat.clientSecret" local.properties | cut -d'=' -f2)
fi

# Check if credentials are set
if [ -z "$GIGACHAT_CLIENT_ID" ] || [ -z "$GIGACHAT_CLIENT_SECRET" ]; then
    echo "❌ Error: GigaChat credentials not set"
    echo "Please set GIGACHAT_CLIENT_ID and GIGACHAT_CLIENT_SECRET environment variables"
    echo "or create local.properties file with:"
    echo "  gigachat.clientId=YOUR_CLIENT_ID"
    echo "  gigachat.clientSecret=YOUR_CLIENT_SECRET"
    exit 1
fi

echo "✅ Credentials loaded"
echo

# Test 1: Index markdown files in current directory
echo "📚 Test 1: Indexing markdown files..."
echo "------------------------------------"
./gradlew :shared:runIndexing --args="index . ./document-index.json md" --quiet

echo
echo "📊 Test 2: Show index statistics..."
echo "------------------------------------"
./gradlew :shared:runIndexing --args="stats ./document-index.json" --quiet

echo
echo "🔍 Test 3: Search test..."
echo "------------------------------------"
./gradlew :shared:runIndexing --args="search ./document-index.json 'GigaChat API' 3" --quiet

echo
echo "✅ All tests completed!"
echo "📁 Index saved to: ./document-index.json"
