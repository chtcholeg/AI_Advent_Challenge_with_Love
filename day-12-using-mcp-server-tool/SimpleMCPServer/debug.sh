#!/bin/bash

# Debug script for MCP Server
# Starts the server in debug mode, waiting for debugger to attach on port 5005
#
# Usage:
#   ./debug.sh           - Start with debugger (waits for connection)
#   ./debug.sh --no-wait - Start with debugger (doesn't wait)

DEBUG_PORT=5005

if [ "$1" == "--no-wait" ]; then
    SUSPEND="n"
else
    SUSPEND="y"
fi

echo "Starting MCP Server in debug mode on port $DEBUG_PORT"
echo "Connect your debugger to localhost:$DEBUG_PORT"

if [ "$SUSPEND" == "y" ]; then
    echo "Server will wait for debugger to connect..."
fi

./gradlew run --debug-jvm -PdebugPort=$DEBUG_PORT
