#!/usr/bin/env python3
"""Simple test for FileOps MCP Server using direct tool calls."""

import asyncio
import sys
from pathlib import Path

# Add parent directory to path to import fileops
sys.path.insert(0, str(Path(__file__).parent))

from fileops.file_client import FileClient
from fileops.tools import get_all_tools


async def test_fileops():
    """Test all FileOps tools."""
    print("FileOps MCP Server - Direct Tool Test")
    print("=" * 50)
    print()

    # Create client with test root directory
    test_root = Path(__file__).parent / "test_workspace"
    test_root.mkdir(exist_ok=True)

    client = FileClient(
        root_dir=str(test_root),
        max_file_size=10 * 1024 * 1024,
        max_search_results=100,
    )

    # Get all tools
    tools = get_all_tools(client)
    tool_map = {tool.name: tool for tool in tools}

    print(f"Root directory: {test_root}")
    print(f"Available tools: {len(tools)}")
    print()

    # Test 1: Create directory
    print("1. Create directory")
    result = await tool_map["create_directory"].execute({"path": "test_files"})
    print(result.content)
    print(f"   Error: {result.is_error}")
    print()

    # Test 2: Write file
    print("2. Write file")
    result = await tool_map["write_file"].execute({
        "path": "test_files/hello.txt",
        "content": "Hello, World!\nThis is a test file.",
    })
    print(result.content)
    print(f"   Error: {result.is_error}")
    print()

    # Test 3: Read file
    print("3. Read file")
    result = await tool_map["read_file"].execute({"path": "test_files/hello.txt"})
    print(result.content[:200] + "..." if len(result.content) > 200 else result.content)
    print(f"   Error: {result.is_error}")
    print()

    # Test 4: Append to file
    print("4. Append to file")
    result = await tool_map["write_file"].execute({
        "path": "test_files/hello.txt",
        "content": "\nAppended line!",
        "append": True,
    })
    print(result.content)
    print(f"   Error: {result.is_error}")
    print()

    # Test 5: Get file info
    print("5. Get file info")
    result = await tool_map["get_file_info"].execute({"path": "test_files/hello.txt"})
    print(result.content)
    print(f"   Error: {result.is_error}")
    print()

    # Test 6: Create test files for search
    print("6. Create test files for search")
    await tool_map["write_file"].execute({
        "path": "test_files/test1.py",
        "content": "def main():\n    print('Hello')\n",
    })
    await tool_map["write_file"].execute({
        "path": "test_files/test2.py",
        "content": "def helper():\n    return 42\n",
    })
    await tool_map["write_file"].execute({
        "path": "test_files/data.json",
        "content": '{"key": "value"}',
    })
    print("   Created: test1.py, test2.py, data.json")
    print()

    # Test 7: List directory
    print("7. List directory")
    result = await tool_map["list_directory"].execute({"path": "test_files"})
    print(result.content)
    print(f"   Error: {result.is_error}")
    print()

    # Test 8: Search files by pattern
    print("8. Search files by pattern (*.py)")
    result = await tool_map["search_files"].execute({
        "pattern": "*.py",
        "path": "test_files",
    })
    print(result.content)
    print(f"   Error: {result.is_error}")
    print()

    # Test 9: Search content
    print("9. Search content (def)")
    result = await tool_map["search_content"].execute({
        "query": "def",
        "path": "test_files",
        "file_pattern": "*.py",
    })
    print(result.content)
    print(f"   Error: {result.is_error}")
    print()

    # Test 10: Delete file
    print("10. Delete file (data.json)")
    result = await tool_map["delete_file"].execute({"path": "test_files/data.json"})
    print(result.content)
    print(f"   Error: {result.is_error}")
    print()

    # Test 11: List directory after deletion
    print("11. List directory after deletion")
    result = await tool_map["list_directory"].execute({"path": "test_files"})
    print(result.content)
    print(f"   Error: {result.is_error}")
    print()

    print("=" * 50)
    print("✓ All tests completed!")
    print()
    print(f"Test files created in: {test_root}")
    print("To clean up: rm -rf test_workspace")


if __name__ == "__main__":
    asyncio.run(test_fileops())
