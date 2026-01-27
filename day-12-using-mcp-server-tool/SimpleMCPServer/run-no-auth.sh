#!/bin/bash

echo "🚀 Starting MCP Server WITHOUT authentication..."
echo "⚠️  WARNING: This is for development/testing only!"
echo ""

./gradlew run --args="--disable-auth"
