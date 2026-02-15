#!/usr/bin/env python3
"""Quick test for LLM word normalization"""

import asyncio
import sys
from pathlib import Path

# Add parent to path
sys.path.append(str(Path(__file__).parent))

from crm.search_service import SearchService


async def test_normalization():
    """Test word normalization with GigaChat"""
    service = SearchService()

    if not service.use_llm:
        print("❌ LLM not available (no GigaChat credentials)")
        print("Set GIGACHAT_CLIENT_ID and GIGACHAT_CLIENT_SECRET")
        return

    print("🧪 Testing LLM word normalization")
    print("=" * 50)

    test_cases = [
        ["напоминаний", "Telegram"],
        ["авторизации", "ошибка"],
        ["индексирования", "PDF"],
        ["креденшелов", "токена"],
    ]

    for words in test_cases:
        normalized = await service.normalize_words_with_llm(words)
        print(f"\n{words}")
        print(f"  → {normalized}")

    print("\n" + "=" * 50)
    print("✅ Test completed")


if __name__ == "__main__":
    asyncio.run(test_normalization())
