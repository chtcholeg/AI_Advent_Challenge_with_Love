#!/bin/bash

# Quick Start Script for User Support Assistant (Day 23)

set -e

echo "🚀 User Support Assistant - Quick Start"
echo "========================================"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if credentials are set
if [ -z "$GIGACHAT_CLIENT_ID" ] || [ -z "$GIGACHAT_CLIENT_SECRET" ]; then
    echo -e "${YELLOW}⚠️  GigaChat credentials not found in environment${NC}"
    echo "Please set GIGACHAT_CLIENT_ID and GIGACHAT_CLIENT_SECRET"
    echo ""
    echo "Option 1: Export environment variables:"
    echo "  export GIGACHAT_CLIENT_ID=\"your_client_id\""
    echo "  export GIGACHAT_CLIENT_SECRET=\"your_client_secret\""
    echo ""
    echo "Option 2: Add to local.properties:"
    echo "  gigachat.clientId=your_client_id"
    echo "  gigachat.clientSecret=your_client_secret"
    echo ""
    read -p "Do you want to continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Step 1: Index FAQ documentation
echo ""
echo "📚 Step 1: Indexing FAQ documentation..."
echo "----------------------------------------"

if [ ! -d "support-docs/faq" ]; then
    echo -e "${RED}❌ FAQ directory not found!${NC}"
    exit 1
fi

echo "FAQ files found:"
ls -1 support-docs/faq/*.md

echo ""
read -p "Index FAQ documents? (Y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Nn]$ ]]; then
    echo "Running indexing..."
    ./gradlew :shared:runIndexing --args="index ./support-docs/faq ./support-index.json md"

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ FAQ indexed successfully!${NC}"

        # Show stats
        echo ""
        echo "Index statistics:"
        ./gradlew :shared:runIndexing --args="stats ./support-index.json"
    else
        echo -e "${RED}❌ Indexing failed!${NC}"
        exit 1
    fi
fi

# Step 2: Setup MCP config
echo ""
echo "🔧 Step 2: Setting up MCP configuration..."
echo "------------------------------------------"

mkdir -p ~/.ai-agent

if [ ! -f ~/.ai-agent/mcp-config.json ]; then
    echo "Copying MCP configuration to ~/.ai-agent/"
    # Update path in config
    CURRENT_DIR=$(pwd)
    sed "s|\"./mcp-servers\"|\"$CURRENT_DIR/mcp-servers\"|g" support-docs/config/mcp-config.json > ~/.ai-agent/mcp-config.json
    echo -e "${GREEN}✅ MCP config created${NC}"
else
    echo -e "${YELLOW}⚠️  MCP config already exists. Skipping...${NC}"
fi

# Step 3: Start CRM MCP Server
echo ""
echo "🖥️  Step 3: Starting CRM MCP Server..."
echo "--------------------------------------"

cd mcp-servers

# Check if venv exists
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
else
    source venv/bin/activate
fi

echo "Checking MCP server..."
python -c "import mcp" 2>/dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ MCP dependencies OK${NC}"
else
    echo -e "${YELLOW}⚠️  Installing MCP dependencies...${NC}"
    pip install -r requirements.txt
fi

# Check if server is already running
if pgrep -f "crm.main" > /dev/null; then
    echo -e "${YELLOW}⚠️  CRM MCP Server already running${NC}"
else
    echo "Starting CRM MCP Server in background..."
    cd crm
    python -m crm.main > /tmp/crm-mcp.log 2>&1 &
    CRM_PID=$!
    echo -e "${GREEN}✅ CRM MCP Server started (PID: $CRM_PID)${NC}"
    echo "Log: /tmp/crm-mcp.log"
    cd ..
fi

cd ..

# Step 4: Instructions for AI Agent
echo ""
echo "🤖 Step 4: Configure AI Agent"
echo "------------------------------"
echo ""
echo "Next steps:"
echo ""
echo "1. Start AI Agent:"
echo "   ${GREEN}./gradlew :ai-agent:run${NC}"
echo ""
echo "2. In AI Agent UI, configure:"
echo ""
echo "   a) System Prompt:"
echo "      - Go to Settings → System Prompt"
echo "      - Copy from: ${YELLOW}support-docs/config/support-assistant-prompt.md${NC}"
echo ""
echo "   b) RAG Settings:"
echo "      - Enable RAG Mode"
echo "      - Index Path: ${YELLOW}./support-index.json${NC}"
echo "      - Top K: 5"
echo "      - Similarity Threshold: 0.7"
echo ""
echo "   c) MCP Settings:"
echo "      - Enable 'crm' MCP Server"
echo "      - Check connection status"
echo ""
echo "3. Test with questions:"
echo "   ${GREEN}\"Почему не работает авторизация?\"${NC}"
echo "   ${GREEN}\"Расскажи про пользователя user_001\"${NC}"
echo "   ${GREEN}\"У user_001 проблема с авторизацией\"${NC}"
echo ""
echo "📖 Full documentation:"
echo "   - Quick overview: ${YELLOW}support-docs/DAY_23_README.md${NC}"
echo "   - Setup guide: ${YELLOW}support-docs/SETUP_GUIDE.md${NC}"
echo "   - Usage guide: ${YELLOW}support-docs/USAGE_GUIDE.md${NC}"
echo "   - Test scenarios: ${YELLOW}support-docs/TEST_SCENARIOS.md${NC}"
echo ""
echo "🎉 Setup complete! Ready to use User Support Assistant!"
echo ""
