"""FileOps MCP tool definitions.

Tools for file system operations:
- write_file: Write content to a file
- read_file: Read content from a file
- search_files: Search for files by name pattern
- search_content: Search for content within files
- list_directory: List files in a directory
- delete_file: Delete a file
- create_directory: Create a directory
- get_file_info: Get file/directory information
"""

import logging
import json
from shared import ToolResult, BaseTool
from .file_client import FileClient

logger = logging.getLogger(__name__)


class FileOpsTool(BaseTool):
    """Base class for FileOps MCP tools."""

    def __init__(self, client: FileClient):
        self.client = client

    async def execute(self, arguments: dict) -> ToolResult:
        raise NotImplementedError


# ---------------------------------------------------------------------------
# Tool: write_file
# ---------------------------------------------------------------------------

class WriteFileTool(FileOpsTool):
    name = "write_file"
    description = (
        "Write content to a file. Creates parent directories if needed. "
        "Can overwrite or append to existing files."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "File path (relative to root directory or absolute)",
            },
            "content": {
                "type": "string",
                "description": "Content to write to the file",
            },
            "append": {
                "type": "boolean",
                "description": "If true, append to file; if false, overwrite (default: false)",
            },
        },
        "required": ["path", "content"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        path = arguments.get("path", "").strip()
        content = arguments.get("content", "")
        append = arguments.get("append", False)

        if not path:
            return ToolResult("Missing required parameter: path", is_error=True)

        try:
            result = await self.client.write_file(path, content, append)

            mode = "Appended to" if append else "Written to"
            lines = [
                f"{mode} file: {result['path']}",
                f"Size: {result['size_bytes']} bytes",
                f"Absolute path: {result['absolute_path']}",
            ]

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"WriteFileTool error: {e}")
            return ToolResult(f"Failed to write file '{path}': {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: read_file
# ---------------------------------------------------------------------------

class ReadFileTool(FileOpsTool):
    name = "read_file"
    description = "Read content from a file."
    input_schema = {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "File path (relative to root directory or absolute)",
            },
        },
        "required": ["path"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        path = arguments.get("path", "").strip()

        if not path:
            return ToolResult("Missing required parameter: path", is_error=True)

        try:
            result = await self.client.read_file(path)

            lines = [
                f"File: {result['path']}",
                f"Size: {result['size_bytes']} bytes, {result['lines']} lines",
                f"Absolute path: {result['absolute_path']}",
                "",
                "--- Content ---",
                result['content'],
            ]

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"ReadFileTool error: {e}")
            return ToolResult(f"Failed to read file '{path}': {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: list_directory
# ---------------------------------------------------------------------------

class ListDirectoryTool(FileOpsTool):
    name = "list_directory"
    description = "List files and directories in a directory."
    input_schema = {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "Directory path (default: current directory)",
            },
            "pattern": {
                "type": "string",
                "description": "Glob pattern for filtering (e.g., '*.py', 'test_*') (default: '*')",
            },
        },
    }

    async def execute(self, arguments: dict) -> ToolResult:
        path = arguments.get("path", ".").strip()
        pattern = arguments.get("pattern", "*").strip()

        try:
            result = await self.client.list_directory(path, pattern)

            lines = [
                f"Directory: {result['directory']}",
                f"Pattern: {result['pattern']}",
                f"Total entries: {result['count']}",
                "",
            ]

            if result['entries']:
                lines.append("Entries:")
                for entry in result['entries']:
                    type_icon = "📄" if entry['type'] == "file" else "📁"
                    size_str = f" ({entry['size_bytes']} bytes)" if entry['size_bytes'] is not None else ""
                    lines.append(f"  {type_icon} {entry['name']}{size_str}")
            else:
                lines.append("No entries found.")

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"ListDirectoryTool error: {e}")
            return ToolResult(f"Failed to list directory '{path}': {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: search_files
# ---------------------------------------------------------------------------

class SearchFilesTool(FileOpsTool):
    name = "search_files"
    description = (
        "Search for files by name pattern using glob syntax. "
        "Supports wildcards: * (any chars), ? (single char), ** (recursive)."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "pattern": {
                "type": "string",
                "description": "Glob pattern (e.g., '*.py', 'test_*.txt', '*.{py,js}')",
            },
            "path": {
                "type": "string",
                "description": "Starting directory (default: root directory)",
            },
            "recursive": {
                "type": "boolean",
                "description": "If true, search recursively (default: true)",
            },
        },
        "required": ["pattern"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        pattern = arguments.get("pattern", "").strip()
        path = arguments.get("path", ".").strip()
        recursive = arguments.get("recursive", True)

        if not pattern:
            return ToolResult("Missing required parameter: pattern", is_error=True)

        try:
            result = await self.client.search_files(pattern, path, recursive)

            lines = [
                f"Search pattern: {result['pattern']}",
                f"Directory: {result['search_directory']}",
                f"Recursive: {result['recursive']}",
                f"Found: {result['count']} file(s)",
            ]

            if result['truncated']:
                lines.append("⚠️  Results truncated (max limit reached)")

            if result['results']:
                lines.append("")
                lines.append("Files:")
                for item in result['results']:
                    lines.append(f"  📄 {item['path']} ({item['size_bytes']} bytes)")
            else:
                lines.append("")
                lines.append("No files found.")

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"SearchFilesTool error: {e}")
            return ToolResult(f"Failed to search files: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: search_content
# ---------------------------------------------------------------------------

class SearchContentTool(FileOpsTool):
    name = "search_content"
    description = (
        "Search for text content within files. "
        "Supports both literal text search and regex patterns."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": "Search query (text or regex pattern)",
            },
            "path": {
                "type": "string",
                "description": "Starting directory (default: root directory)",
            },
            "file_pattern": {
                "type": "string",
                "description": "Glob pattern for files to search (e.g., '*.py') (default: '*')",
            },
            "case_sensitive": {
                "type": "boolean",
                "description": "If true, search is case-sensitive (default: false)",
            },
            "regex": {
                "type": "boolean",
                "description": "If true, treat query as regex pattern (default: false)",
            },
        },
        "required": ["query"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        query = arguments.get("query", "").strip()
        path = arguments.get("path", ".").strip()
        file_pattern = arguments.get("file_pattern", "*").strip()
        case_sensitive = arguments.get("case_sensitive", False)
        regex = arguments.get("regex", False)

        if not query:
            return ToolResult("Missing required parameter: query", is_error=True)

        try:
            result = await self.client.search_content(
                query, path, file_pattern, case_sensitive, regex
            )

            lines = [
                f"Query: {result['query']}",
                f"Directory: {result['search_directory']}",
                f"File pattern: {result['file_pattern']}",
                f"Case sensitive: {result['case_sensitive']}",
                f"Regex: {result['regex']}",
                f"Files searched: {result['files_searched']}",
                f"Files with matches: {result['count']}",
            ]

            if result['truncated']:
                lines.append("⚠️  Results truncated (max limit reached)")

            if result['results']:
                lines.append("")
                for file_result in result['results']:
                    lines.append(f"📄 {file_result['path']} ({file_result['total_matches']} match(es))")
                    for match in file_result['matches']:
                        lines.append(f"   Line {match['line_number']}: {match['line']}")
                    if file_result['total_matches'] > len(file_result['matches']):
                        lines.append(f"   ... and {file_result['total_matches'] - len(file_result['matches'])} more matches")
                    lines.append("")
            else:
                lines.append("")
                lines.append("No matches found.")

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"SearchContentTool error: {e}")
            return ToolResult(f"Failed to search content: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: delete_file
# ---------------------------------------------------------------------------

class DeleteFileTool(FileOpsTool):
    name = "delete_file"
    description = "Delete a file."
    input_schema = {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "File path (relative to root directory or absolute)",
            },
        },
        "required": ["path"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        path = arguments.get("path", "").strip()

        if not path:
            return ToolResult("Missing required parameter: path", is_error=True)

        try:
            result = await self.client.delete_file(path)

            lines = [
                f"Deleted file: {result['path']}",
                f"Size: {result['size_bytes']} bytes",
            ]

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"DeleteFileTool error: {e}")
            return ToolResult(f"Failed to delete file '{path}': {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: create_directory
# ---------------------------------------------------------------------------

class CreateDirectoryTool(FileOpsTool):
    name = "create_directory"
    description = "Create a directory. Creates parent directories if needed."
    input_schema = {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "Directory path (relative to root directory or absolute)",
            },
        },
        "required": ["path"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        path = arguments.get("path", "").strip()

        if not path:
            return ToolResult("Missing required parameter: path", is_error=True)

        try:
            result = await self.client.create_directory(path)

            lines = [
                f"Created directory: {result['path']}",
                f"Absolute path: {result['absolute_path']}",
            ]

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"CreateDirectoryTool error: {e}")
            return ToolResult(f"Failed to create directory '{path}': {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: get_file_info
# ---------------------------------------------------------------------------

class GetFileInfoTool(FileOpsTool):
    name = "get_file_info"
    description = "Get information about a file or directory."
    input_schema = {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "File or directory path",
            },
        },
        "required": ["path"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        path = arguments.get("path", "").strip()

        if not path:
            return ToolResult("Missing required parameter: path", is_error=True)

        try:
            result = await self.client.get_file_info(path)

            import datetime

            lines = [
                f"Path: {result['path']}",
                f"Absolute path: {result['absolute_path']}",
                f"Type: {result['type']}",
            ]

            if result['size_bytes'] is not None:
                lines.append(f"Size: {result['size_bytes']} bytes")

            lines.append(f"Modified: {datetime.datetime.fromtimestamp(result['modified']).isoformat()}")
            lines.append(f"Created: {datetime.datetime.fromtimestamp(result['created']).isoformat()}")

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"GetFileInfoTool error: {e}")
            return ToolResult(f"Failed to get file info for '{path}': {e}", is_error=True)


# ---------------------------------------------------------------------------
# Registry
# ---------------------------------------------------------------------------

def get_all_tools(client: FileClient) -> list[FileOpsTool]:
    """Return all registered FileOps MCP tools."""
    return [
        WriteFileTool(client),
        ReadFileTool(client),
        ListDirectoryTool(client),
        SearchFilesTool(client),
        SearchContentTool(client),
        DeleteFileTool(client),
        CreateDirectoryTool(client),
        GetFileInfoTool(client),
    ]
