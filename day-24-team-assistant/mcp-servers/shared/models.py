"""Shared models for MCP protocol."""

from dataclasses import dataclass
from typing import Optional


@dataclass
class ToolResult:
    """Result of a tool execution."""

    content: str
    is_error: bool = False

    def to_dict(self) -> dict:
        """Convert to MCP protocol format."""
        return {
            "content": [{"type": "text", "text": self.content}],
            "isError": self.is_error,
        }


class BaseTool:
    """Base class for MCP tools."""

    name: str = ""
    description: str = ""
    input_schema: dict = {}
    few_shot_examples: list[dict] = []
    negative_few_shot_examples: list[dict] = []

    async def execute(self, arguments: dict) -> ToolResult:
        """Execute the tool with given arguments."""
        raise NotImplementedError

    def to_dict(self) -> dict:
        """Convert tool to MCP protocol format."""
        result = {
            "name": self.name,
            "description": self.description,
            "inputSchema": self.input_schema,
        }
        if self.few_shot_examples:
            result["fewShotExamples"] = self.few_shot_examples
        if self.negative_few_shot_examples:
            result["negativeFewShotExamples"] = self.negative_few_shot_examples
        return result
