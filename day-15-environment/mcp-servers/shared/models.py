"""Common data models for MCP tools."""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any


@dataclass
class ToolResult:
    """Wraps tool output with error flag for MCP protocol."""
    content: str
    is_error: bool = False


class BaseTool(ABC):
    """Abstract base class for MCP tools.

    Subclasses must define:
    - name: unique tool identifier
    - description: human-readable description
    - input_schema: JSON Schema for arguments
    - execute(): async method to run the tool
    """

    name: str = ""
    description: str = ""
    input_schema: dict = {}

    @abstractmethod
    async def execute(self, arguments: dict) -> ToolResult:
        """Execute the tool with given arguments."""
        pass


@dataclass
class ToolParameter:
    """Tool parameter definition for declarative tool schemas."""
    name: str
    type: str
    description: str
    required: bool = True
    default: Any = None


@dataclass
class Tool:
    """Declarative tool definition.

    This provides an alternative to BaseTool for servers that prefer
    declarative schemas with separate handler functions.
    """
    name: str
    description: str
    parameters: list[ToolParameter] = field(default_factory=list)
    fewShotExamples: list[dict] = field(default_factory=list)
    negativeFewShotExamples: list[dict] = field(default_factory=list)  # Examples when NOT to use this tool

    @property
    def input_schema(self) -> dict:
        """Convert to JSON Schema format for MCP protocol."""
        properties = {}
        required = []

        for param in self.parameters:
            prop = {
                "type": param.type,
                "description": param.description
            }
            if param.default is not None:
                prop["default"] = param.default
            properties[param.name] = prop

            if param.required:
                required.append(param.name)

        schema = {
            "type": "object",
            "properties": properties
        }
        if required:
            schema["required"] = required

        return schema


@dataclass
class ToolCallRequest:
    """Request to call a tool."""
    name: str
    arguments: dict = field(default_factory=dict)
