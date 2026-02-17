"""AI service for priority analysis using GigaChat."""

import asyncio
import base64
import json
import logging
import uuid
from datetime import datetime
from typing import Any, Optional

import httpx

from . import config

logger = logging.getLogger(__name__)


class AiService:
    """Service for AI-powered task analysis"""

    def __init__(self):
        self.client_id = config.GIGACHAT_CLIENT_ID
        self.client_secret = config.GIGACHAT_CLIENT_SECRET
        self.access_token: Optional[str] = None
        self.token_expires_at: Optional[datetime] = None

    async def get_access_token(self) -> str:
        """Get GigaChat access token (cached)"""
        now = datetime.now()

        # Return cached token if valid
        if self.access_token and self.token_expires_at and now < self.token_expires_at:
            logger.info(f"Using cached token (expires at {self.token_expires_at})")
            return self.access_token

        # Request new token
        if not self.client_id or not self.client_secret:
            raise ValueError("GigaChat credentials not configured")

        auth = base64.b64encode(
            f"{self.client_id}:{self.client_secret}".encode()
        ).decode()

        async with httpx.AsyncClient(verify=False) as client:
            response = await client.post(
                "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
                headers={
                    "Authorization": f"Basic {auth}",
                    "Accept": "application/json",
                    "RqUID": str(uuid.uuid4()),
                    "Content-Type": "application/x-www-form-urlencoded"
                },
                data={"scope": "GIGACHAT_API_PERS"}
            )
            response.raise_for_status()
            data = response.json()

            self.access_token = data["access_token"]
            expires_at_ms = data.get("expires_at", 1800000)  # Absolute time in milliseconds
            self.token_expires_at = datetime.fromtimestamp(expires_at_ms / 1000)

            logger.info(f"New token obtained (expires at {self.token_expires_at})")
            return self.access_token

    async def chat_completion(self, prompt: str, model: str = "GigaChat") -> str:
        """Send chat completion request to GigaChat"""
        token = await self.get_access_token()

        async with httpx.AsyncClient(verify=False, timeout=60.0) as client:
            response = await client.post(
                "https://gigachat.devices.sberbank.ru/api/v1/chat/completions",
                headers={
                    "Authorization": f"Bearer {token}",
                    "Content-Type": "application/json"
                },
                json={
                    "model": model,
                    "messages": [
                        {
                            "role": "user",
                            "content": prompt
                        }
                    ],
                    "temperature": 0.7,
                    "max_tokens": 2048
                }
            )
            response.raise_for_status()
            data = response.json()

            return data["choices"][0]["message"]["content"]

    async def analyze_priorities(self, project_id: str) -> dict[str, Any]:
        """Analyze task priorities and provide recommendations"""
        from .main import load_tasks

        tasks = load_tasks()
        project_tasks = [t for t in tasks if t.get("project_id") == project_id]

        if not project_tasks:
            return {
                "success": False,
                "error": "No tasks found for this project"
            }

        # Prepare task summary for AI
        task_summary = []
        for task in project_tasks:
            task_summary.append({
                "id": task["id"],
                "title": task["title"],
                "description": task.get("description", "")[:200],
                "status": task["status"],
                "priority": task["priority"],
                "assignee": task.get("assignee", "unassigned"),
                "due_date": task.get("due_date", ""),
                "estimated_hours": task.get("estimated_hours", 0),
                "spent_hours": task.get("spent_hours", 0)
            })

        prompt = f"""Проанализируй список задач проекта и дай рекомендации по приоритетам.

Задачи:
{json.dumps(task_summary, ensure_ascii=False, indent=2)}

Проанализируй:
1. Правильность установленных приоритетов
2. Задачи, которые могут блокировать другие
3. Задачи с истекающими дедлайнами
4. Недооценённые или переоценённые задачи
5. Оптимальный порядок выполнения

Предоставь рекомендации в формате JSON:
{{
  "summary": "Общий анализ ситуации",
  "recommendations": [
    {{
      "task_id": "task_001",
      "current_priority": "medium",
      "suggested_priority": "high",
      "reason": "Причина изменения приоритета",
      "urgency_score": 0.8
    }}
  ],
  "execution_order": ["task_002", "task_001", "task_003"],
  "risks": [
    {{
      "task_id": "task_001",
      "risk": "Описание риска",
      "mitigation": "Как снизить риск"
    }}
  ],
  "insights": [
    "Инсайт 1",
    "Инсайт 2"
  ]
}}

Отвечай только JSON, без дополнительного текста."""

        try:
            response = await self.chat_completion(prompt)

            # Extract JSON from response
            # Sometimes GigaChat wraps JSON in markdown code blocks
            response = response.strip()
            if response.startswith("```json"):
                response = response[7:]
            if response.startswith("```"):
                response = response[3:]
            if response.endswith("```"):
                response = response[:-3]
            response = response.strip()

            analysis = json.loads(response)

            logger.info("Priority analysis completed successfully")

            return {
                "success": True,
                "analysis": analysis,
                "tasks_analyzed": len(project_tasks)
            }

        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse AI response: {e}")
            return {
                "success": False,
                "error": "Failed to parse AI analysis",
                "raw_response": response
            }
        except Exception as e:
            logger.error(f"Error in priority analysis: {e}", exc_info=True)
            return {
                "success": False,
                "error": str(e)
            }

    async def suggest_priority(self, title: str, description: str) -> dict[str, Any]:
        """Suggest priority for a new task"""
        from .main import load_tasks

        tasks = load_tasks()

        # Get similar tasks for context
        similar_tasks = []
        for task in tasks[-10:]:  # Last 10 tasks for context
            similar_tasks.append({
                "title": task["title"],
                "priority": task["priority"],
                "status": task["status"]
            })

        prompt = f"""Предложи приоритет для новой задачи на основе контекста проекта.

Новая задача:
Название: {title}
Описание: {description}

Контекст (последние задачи проекта):
{json.dumps(similar_tasks, ensure_ascii=False, indent=2)}

Проанализируй:
1. Сложность задачи
2. Срочность (на основе описания)
3. Зависимости от других задач
4. Влияние на проект

Предложи приоритет в формате JSON:
{{
  "suggested_priority": "high",
  "confidence": 0.85,
  "reasoning": "Подробное объяснение выбора приоритета",
  "estimated_hours": 8,
  "labels": ["feature", "backend"],
  "dependencies": ["task_001"],
  "risks": "Потенциальные риски"
}}

Приоритеты: low, medium, high, critical
Отвечай только JSON, без дополнительного текста."""

        try:
            response = await self.chat_completion(prompt)

            # Extract JSON
            response = response.strip()
            if response.startswith("```json"):
                response = response[7:]
            if response.startswith("```"):
                response = response[3:]
            if response.endswith("```"):
                response = response[:-3]
            response = response.strip()

            suggestion = json.loads(response)

            logger.info(f"Priority suggestion: {suggestion.get('suggested_priority')}")

            return {
                "success": True,
                "suggestion": suggestion
            }

        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse AI response: {e}")
            return {
                "success": False,
                "error": "Failed to parse AI suggestion",
                "raw_response": response
            }
        except Exception as e:
            logger.error(f"Error in priority suggestion: {e}", exc_info=True)
            return {
                "success": False,
                "error": str(e)
            }


# Singleton instance
_ai_service: Optional[AiService] = None


def get_ai_service() -> AiService:
    """Get or create AI service instance"""
    global _ai_service
    if _ai_service is None:
        _ai_service = AiService()
    return _ai_service
