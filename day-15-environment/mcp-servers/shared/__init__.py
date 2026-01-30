"""Shared MCP server components.

This module provides common building blocks for MCP servers:
- McpProtocolHandler: JSON-RPC 2.0 message handling
- SseTransport: Server-Sent Events transport layer
- ToolResult: Standard tool execution result
- BaseTool: Abstract base class for tools
- Tool: Declarative tool definition
- ToolParameter: Tool parameter definition
- ToolCallRequest: Request to call a tool
"""

from .mcp_protocol import McpProtocolHandler, MCP_PROTOCOL_VERSION
from .sse_transport import SseTransport, SseSession
from .models import ToolResult, BaseTool, Tool, ToolParameter, ToolCallRequest

__all__ = [
    "McpProtocolHandler",
    "MCP_PROTOCOL_VERSION",
    "SseTransport",
    "SseSession",
    "ToolResult",
    "BaseTool",
    "Tool",
    "ToolParameter",
    "ToolCallRequest",
]
