"""Advanced search service with LLM query expansion and semantic search."""

import json
import logging
import os
import re
import uuid
from typing import Any, Optional
import httpx
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class SearchResult:
    """Search result with relevance score."""
    ticket: dict
    score: float
    match_reason: str


def get_word_stem(word: str, min_length: int = 4) -> str:
    """
    Extract word stem for better matching across word forms.
    For Russian words: "напоминания" → "напомина", "напоминаний" → "напомина"

    Args:
        word: Word to extract stem from
        min_length: Minimum stem length (default 4 for Russian words)

    Returns:
        Word stem (first 60-70% of word, minimum min_length chars)
    """
    if len(word) <= min_length:
        return word

    # Take first 60-70% of the word as stem
    stem_length = max(min_length, int(len(word) * 0.65))
    return word[:stem_length]


class SearchService:
    """
    Advanced search service supporting:
    1. Keyword search (default, always available)
    2. LLM query expansion (optional, requires GigaChat API)
    3. Semantic search (optional, requires embeddings)
    """

    def __init__(self):
        self.gigachat_client_id = os.getenv("GIGACHAT_CLIENT_ID")
        self.gigachat_client_secret = os.getenv("GIGACHAT_CLIENT_SECRET")
        self.gigachat_token: Optional[str] = None
        self.use_llm = bool(self.gigachat_client_id and self.gigachat_client_secret)

        # Cache for normalized ticket texts (ticket_id -> normalized text)
        self._normalized_tickets_cache: dict[str, str] = {}

        if self.use_llm:
            logger.info("[SearchService] LLM query expansion enabled (GigaChat)")
        else:
            logger.info("[SearchService] LLM query expansion disabled (no GigaChat credentials)")

    async def get_gigachat_token(self) -> Optional[str]:
        """Get GigaChat OAuth token."""
        if not self.use_llm:
            return None

        if self.gigachat_token:
            return self.gigachat_token

        try:
            async with httpx.AsyncClient(verify=False) as client:
                auth = httpx.BasicAuth(self.gigachat_client_id, self.gigachat_client_secret)
                response = await client.post(
                    "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
                    auth=auth,
                    headers={
                        "Content-Type": "application/x-www-form-urlencoded",
                        "Accept": "application/json",
                        "RqUID": str(uuid.uuid4()),
                    },
                    data={"scope": "GIGACHAT_API_PERS"},
                    timeout=30.0,
                )
                response.raise_for_status()
                data = response.json()
                self.gigachat_token = data["access_token"]
                logger.info("[SearchService] GigaChat token obtained successfully")
                return self.gigachat_token
        except Exception as e:
            logger.error(f"[SearchService] Failed to get GigaChat token: {e}")
            return None

    async def normalize_words_with_llm(self, words: list[str]) -> list[str]:
        """
        Use GigaChat to normalize Russian words to base form (lemmatization).
        This helps match different word forms: "напоминаний" → "напоминание"

        Args:
            words: List of words to normalize

        Returns:
            List of normalized words (base forms)
        """
        if not self.use_llm or not words:
            return words

        token = await self.get_gigachat_token()
        if not token:
            return words

        try:
            words_str = ", ".join(words)
            prompt = f"""Приведи каждое слово к начальной форме (именительный падеж, единственное число).

Слова: {words_str}

Правила:
- Верни только начальные формы слов через запятую
- Сохрани порядок слов
- Для английских слов - оставь как есть
- Не добавляй пояснений

Пример:
Слова: напоминаний, Telegram, авторизации
Ответ: напоминание, telegram, авторизация

Ответ:"""

            async with httpx.AsyncClient(verify=False) as client:
                response = await client.post(
                    "https://gigachat.devices.sberbank.ru/api/v1/chat/completions",
                    headers={
                        "Authorization": f"Bearer {token}",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": "GigaChat",
                        "messages": [{"role": "user", "content": prompt}],
                        "temperature": 0.1,  # Low temperature for consistent results
                        "max_tokens": 100,
                    },
                    timeout=30.0,
                )
                response.raise_for_status()
                data = response.json()

                normalized = data["choices"][0]["message"]["content"].strip()
                # Parse comma-separated terms
                terms = [term.strip().lower() for term in normalized.split(",") if len(term.strip()) > 2]

                logger.info(f"[SearchService] Words normalized: {words} → {terms}")
                return terms
        except Exception as e:
            logger.error(f"[SearchService] LLM word normalization failed: {e}")
            return words

    async def expand_query_with_llm(self, query: str) -> list[str]:
        """
        Use GigaChat to expand search query with synonyms and related terms.
        Returns list of search terms including original query.
        """
        if not self.use_llm:
            return [query]

        token = await self.get_gigachat_token()
        if not token:
            # Fallback: split query into words when LLM unavailable
            words = [word.strip().lower() for word in query.split() if len(word.strip()) > 2]
            logger.info(f"[SearchService] LLM unavailable, fallback to word splitting: {words}")
            return words

        try:
            prompt = f"""Ты - помощник для улучшения поискового запроса.

Пользователь ищет тикет техподдержки с запросом: "{query}"

Твоя задача - вернуть список ключевых слов и синонимов для поиска.

Правила:
- Приведи слова к начальной форме (именительный падеж)
- Добавь технические синонимы (например: "авторизация" → "authentication", "логин", "вход")
- Добавь связанные термины (например: "ошибка авторизации" → "Invalid credentials", "Token expired")
- Не добавляй общие слова (не, если, когда, etc)
- Верни только список слов через запятую

Пример:
Запрос: "не работает авторизация"
Ответ: авторизация, authentication, логин, вход, login, auth, креденшелы, credentials, токен, token

Ответ для запроса "{query}"':"""

            async with httpx.AsyncClient(verify=False) as client:
                response = await client.post(
                    "https://gigachat.devices.sberbank.ru/api/v1/chat/completions",
                    headers={
                        "Authorization": f"Bearer {token}",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": "GigaChat",
                        "messages": [{"role": "user", "content": prompt}],
                        "temperature": 0.3,
                        "max_tokens": 200,
                    },
                    timeout=30.0,
                )
                response.raise_for_status()
                data = response.json()

                expanded = data["choices"][0]["message"]["content"].strip()
                # Parse comma-separated terms
                terms = [term.strip().lower() for term in expanded.split(",") if len(term.strip()) > 2]

                logger.info(f"[SearchService] Query expanded: '{query}' → {len(terms)} terms: {terms[:5]}...")
                return terms
        except Exception as e:
            logger.error(f"[SearchService] LLM query expansion failed: {e}")
            # Fallback: split query into words
            words = [word.strip().lower() for word in query.split() if len(word.strip()) > 2]
            logger.info(f"[SearchService] Fallback to word splitting: {words}")
            return words

    async def search_tickets(
        self,
        tickets: list[dict[str, Any]],
        query: str,
        use_llm_expansion: bool = True,
    ) -> list[SearchResult]:
        """
        Search tickets with optional LLM query expansion.

        Args:
            tickets: List of ticket dicts
            query: Search query
            use_llm_expansion: Whether to use LLM for query expansion

        Returns:
            List of SearchResult sorted by relevance score
        """
        query_lower = query.lower()

        # Get search terms
        if use_llm_expansion and self.use_llm:
            # Full expansion with synonyms
            search_terms = await self.expand_query_with_llm(query)
        else:
            # Simple word splitting
            words = [word.strip() for word in query_lower.split() if len(word.strip()) > 2]

            # Try to normalize words with LLM (lemmatization)
            if self.use_llm:
                search_terms = await self.normalize_words_with_llm(words)
            else:
                search_terms = words

        logger.info(f"[SearchService] Searching with {len(search_terms)} terms: {search_terms}")

        results = []

        for ticket in tickets:
            subject_lower = ticket["subject"].lower()
            description_lower = ticket["description"].lower()
            text = subject_lower + " " + description_lower

            # Calculate relevance score
            score = 0.0
            matched_terms = []

            for term in search_terms:
                term_lower = term.lower()

                # Try exact match first
                if term_lower in text:
                    matched_terms.append(term)
                    # Weight matches in subject higher
                    if term_lower in subject_lower:
                        score += 2.0
                    else:
                        score += 1.0
                # Try stem matching for longer words (handles morphology)
                elif len(term_lower) >= 4:
                    stem = get_word_stem(term_lower)
                    # Use word boundary regex to avoid false matches
                    pattern = re.compile(r'\b' + re.escape(stem) + r'\w*\b', re.IGNORECASE)
                    if pattern.search(text):
                        matched_terms.append(f"{term}*")  # * indicates stem match
                        # Stem matches get slightly lower score
                        if pattern.search(subject_lower):
                            score += 1.5
                        else:
                            score += 0.8

            if score > 0:
                match_reason = f"Matched terms: {', '.join(matched_terms)}"
                results.append(SearchResult(
                    ticket=ticket,
                    score=score,
                    match_reason=match_reason
                ))

        # Sort by score (highest first)
        results.sort(key=lambda r: r.score, reverse=True)

        logger.info(f"[SearchService] Found {len(results)} tickets with scores")

        return results


# Singleton instance
_search_service: Optional[SearchService] = None


def get_search_service() -> SearchService:
    """Get singleton search service instance."""
    global _search_service
    if _search_service is None:
        _search_service = SearchService()
    return _search_service
