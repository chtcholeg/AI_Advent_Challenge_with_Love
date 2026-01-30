"""MCP (Model Context Protocol) JSON-RPC 2.0 handler.

Implements the server side of the MCP protocol:
- initialize / initialized handshake
- tools/list — advertise available tools
- tools/call — execute a specific tool
- ping — keepalive check

All communication uses JSON-RPC 2.0 framing over an SSE transport.
"""

import json
import logging
from typing import Any

logger = logging.getLogger(__name__)

MCP_PROTOCOL_VERSION = "2024-11-05"


def _response(request_id: Any, result: Any = None, error: dict = None) -> dict:
    """Build a JSON-RPC 2.0 response object."""
    resp = {"jsonrpc": "2.0", "id": request_id}
    if error:
        resp["error"] = error
    else:
        resp["result"] = result
    return resp


class McpProtocolHandler:
    """Stateless handler for incoming MCP JSON-RPC requests."""

    def __init__(self, tools: list, server_name: str, server_version: str = "1.0.0"):
        self.tools = {t.name: t for t in tools}
        self.server_name = server_name
        self.server_version = server_version

    async def handle_request(self, request_json: str) -> str:
        """Parse and dispatch a single JSON-RPC request. Returns a JSON string."""
        try:
            request = json.loads(request_json)
        except json.JSONDecodeError as e:
            return json.dumps(_response(None, error={"code": -32700, "message": f"Parse error: {e}"}))

        request_id = request.get("id")
        method = request.get("method", "")
        params = request.get("params", {})

        if method == "initialize":
            result = self._handle_initialize()
        elif method == "initialized":
            result = {}
        elif method == "tools/list":
            result = self._handle_tools_list()
        elif method == "tools/call":
            return await self._handle_tools_call(request_id, params)
        elif method == "ping":
            result = {}
        else:
            return json.dumps(_response(request_id, error={
                "code": -32601,
                "message": f"Method not found: {method}",
            }))

        return json.dumps(_response(request_id, result=result))

    def _handle_initialize(self) -> dict:
        return {
            "protocolVersion": MCP_PROTOCOL_VERSION,
            "capabilities": {
                "tools": {"listChanged": False},
            },
            "serverInfo": {
                "name": self.server_name,
                "version": self.server_version,
            },
        }

    def _handle_tools_list(self) -> dict:
        tools_list = []
        for tool in self.tools.values():
            tool_info = {
                "name": tool.name,
                "description": tool.description,
                "inputSchema": tool.input_schema,
            }
            # Include fewShotExamples if available (support both snake_case and camelCase)
            few_shot = getattr(tool, 'few_shot_examples', None) or getattr(tool, 'fewShotExamples', None)
            if few_shot:
                tool_info["fewShotExamples"] = few_shot
            # Include negativeFewShotExamples if available (examples when NOT to use this tool)
            negative_few_shot = getattr(tool, 'negativeFewShotExamples', None)
            if negative_few_shot:
                tool_info["negativeFewShotExamples"] = negative_few_shot
            tools_list.append(tool_info)
        return {"tools": tools_list}

    async def _handle_tools_call(self, request_id: Any, params: dict) -> str:
        tool_name = params.get("name")
        if not tool_name:
            return json.dumps(_response(request_id, error={
                "code": -32602,
                "message": "Invalid params: missing tool name",
            }))

        tool = self.tools.get(tool_name)
        if not tool:
            return json.dumps(_response(request_id, error={
                "code": -32602,
                "message": f"Tool not found: {tool_name}",
            }))

        arguments = params.get("arguments", {})
        logger.info(f"Tool call: {tool_name} | arguments: {json.dumps(arguments)}")

        try:
            tool_result = await tool.execute(arguments)
            result = {
                "content": [{"type": "text", "text": tool_result.content}],
                "isError": tool_result.is_error,
            }
            return json.dumps(_response(request_id, result=result))
        except Exception as e:
            logger.error(f"Tool execution error ({tool_name}): {e}")
            return json.dumps(_response(request_id, error={
                "code": -32000,
                "message": f"Tool execution error: {e}",
            }))
