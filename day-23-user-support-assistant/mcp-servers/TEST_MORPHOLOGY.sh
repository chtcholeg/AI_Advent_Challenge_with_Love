#!/bin/bash
# Test script for Russian morphology in search

echo "🧪 Testing Russian morphology in ticket search"
echo "================================================"

# Test queries with different word forms
test_queries=(
    "напоминаний"      # родительный падеж
    "напоминания"      # именительный падеж
    "напоминание"      # единственное число
    "авторизации"      # родительный падеж
    "авторизация"      # именительный падеж
    "индексирование"   # единственное число
    "индексирования"   # родительный падеж
)

echo ""
echo "Test queries (different word forms):"
for query in "${test_queries[@]}"; do
    echo "  - '$query'"
done

echo ""
echo "Expected: All queries should find relevant tickets"
echo "================================================"
echo ""

# Python test script
python3 -c "
import sys
import asyncio
sys.path.append('crm')

from search_service import SearchService, get_word_stem
import json

# Test stem extraction
print('📝 Testing stem extraction:')
test_words = [
    'напоминаний',
    'напоминания',
    'напоминание',
    'авторизации',
    'авторизация',
]

for word in test_words:
    stem = get_word_stem(word)
    print(f'  {word:20s} → {stem}')

print()

# Load tickets
with open('crm/data/tickets.json', 'r', encoding='utf-8') as f:
    tickets = json.load(f)

# Test search
async def test_search():
    service = SearchService()

    print('🔍 Testing search queries:')
    print()

    test_queries = [
        'напоминаний Telegram',
        'авторизация',
        'индексирование PDF',
    ]

    for query in test_queries:
        print(f'Query: \"{query}\"')
        results = await service.search_tickets(tickets, query, use_llm_expansion=False)

        if results:
            print(f'  ✅ Found {len(results)} ticket(s):')
            for r in results[:3]:  # Show top 3
                ticket = r.ticket
                print(f'     - {ticket[\"id\"]}: {ticket[\"subject\"]} (score: {r.score:.1f})')
        else:
            print(f'  ❌ No tickets found')
        print()

asyncio.run(test_search())
"

echo ""
echo "✅ Test completed!"
