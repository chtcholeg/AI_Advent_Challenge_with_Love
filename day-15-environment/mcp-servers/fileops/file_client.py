"""Client for file system operations."""

import os
import glob
import re
from pathlib import Path
from typing import List, Dict, Any


class FileClient:
    """Client for safe file system operations."""

    def __init__(self, root_dir: str, max_file_size: int, max_search_results: int):
        """
        Initialize FileClient.

        Args:
            root_dir: Root directory for file operations
            max_file_size: Maximum allowed file size in bytes
            max_search_results: Maximum number of search results
        """
        self.root_dir = Path(root_dir).resolve()
        self.max_file_size = max_file_size
        self.max_search_results = max_search_results

        # Ensure root directory exists
        self.root_dir.mkdir(parents=True, exist_ok=True)

    def _resolve_path(self, path: str) -> Path:
        """
        Resolve path relative to root_dir and ensure it's within root_dir.

        Args:
            path: File path (absolute or relative)

        Returns:
            Resolved absolute path

        Raises:
            ValueError: If path is outside root_dir (security check)
        """
        # Convert to Path and resolve
        target = Path(path)

        # If relative, make it relative to root_dir
        if not target.is_absolute():
            target = self.root_dir / target

        # Resolve to absolute path
        target = target.resolve()

        # Security check: ensure path is within root_dir
        try:
            target.relative_to(self.root_dir)
        except ValueError:
            raise ValueError(
                f"Access denied: path '{path}' is outside root directory '{self.root_dir}'"
            )

        return target

    async def write_file(self, path: str, content: str, append: bool = False) -> Dict[str, Any]:
        """
        Write content to a file.

        Args:
            path: File path
            content: Content to write
            append: If True, append to file; if False, overwrite

        Returns:
            Dict with operation details
        """
        target = self._resolve_path(path)

        # Check content size
        content_bytes = content.encode("utf-8")
        if len(content_bytes) > self.max_file_size:
            raise ValueError(
                f"Content size ({len(content_bytes)} bytes) exceeds maximum "
                f"allowed size ({self.max_file_size} bytes)"
            )

        # Ensure parent directory exists
        target.parent.mkdir(parents=True, exist_ok=True)

        # Write file
        mode = "a" if append else "w"
        target.write_text(content, encoding="utf-8")

        return {
            "path": str(target.relative_to(self.root_dir)),
            "absolute_path": str(target),
            "size_bytes": len(content_bytes),
            "mode": "appended" if append else "written",
        }

    async def read_file(self, path: str) -> Dict[str, Any]:
        """
        Read content from a file.

        Args:
            path: File path

        Returns:
            Dict with file content and metadata
        """
        target = self._resolve_path(path)

        if not target.exists():
            raise FileNotFoundError(f"File not found: {path}")

        if not target.is_file():
            raise ValueError(f"Path is not a file: {path}")

        # Check file size
        size = target.stat().st_size
        if size > self.max_file_size:
            raise ValueError(
                f"File size ({size} bytes) exceeds maximum "
                f"allowed size ({self.max_file_size} bytes)"
            )

        # Read file
        content = target.read_text(encoding="utf-8")

        return {
            "path": str(target.relative_to(self.root_dir)),
            "absolute_path": str(target),
            "content": content,
            "size_bytes": size,
            "lines": len(content.splitlines()),
        }

    async def list_directory(self, path: str = ".", pattern: str = "*") -> Dict[str, Any]:
        """
        List files in a directory.

        Args:
            path: Directory path (default: current directory)
            pattern: Glob pattern for filtering (default: all files)

        Returns:
            Dict with directory listing
        """
        target = self._resolve_path(path)

        if not target.exists():
            raise FileNotFoundError(f"Directory not found: {path}")

        if not target.is_dir():
            raise ValueError(f"Path is not a directory: {path}")

        # List files matching pattern
        entries = []
        for item in sorted(target.glob(pattern)):
            try:
                relative_path = item.relative_to(self.root_dir)
                stat = item.stat()
                entries.append({
                    "name": item.name,
                    "path": str(relative_path),
                    "type": "file" if item.is_file() else "directory",
                    "size_bytes": stat.st_size if item.is_file() else None,
                })
            except (PermissionError, OSError):
                continue  # Skip inaccessible files

        return {
            "directory": str(target.relative_to(self.root_dir)),
            "pattern": pattern,
            "entries": entries,
            "count": len(entries),
        }

    async def search_files(self, pattern: str, path: str = ".", recursive: bool = True) -> Dict[str, Any]:
        """
        Search for files by name pattern.

        Args:
            pattern: Glob pattern (e.g., "*.py", "test_*.txt")
            path: Starting directory (default: root)
            recursive: If True, search recursively

        Returns:
            Dict with search results
        """
        target = self._resolve_path(path)

        if not target.exists():
            raise FileNotFoundError(f"Directory not found: {path}")

        if not target.is_dir():
            raise ValueError(f"Path is not a directory: {path}")

        # Search for files
        glob_pattern = f"**/{pattern}" if recursive else pattern
        results = []

        for item in target.glob(glob_pattern):
            if len(results) >= self.max_search_results:
                break

            try:
                if item.is_file():
                    relative_path = item.relative_to(self.root_dir)
                    stat = item.stat()
                    results.append({
                        "path": str(relative_path),
                        "absolute_path": str(item),
                        "size_bytes": stat.st_size,
                    })
            except (PermissionError, OSError):
                continue  # Skip inaccessible files

        return {
            "pattern": pattern,
            "search_directory": str(target.relative_to(self.root_dir)),
            "recursive": recursive,
            "results": results,
            "count": len(results),
            "truncated": len(results) >= self.max_search_results,
        }

    async def search_content(
        self, query: str, path: str = ".", file_pattern: str = "*",
        case_sensitive: bool = False, regex: bool = False
    ) -> Dict[str, Any]:
        """
        Search for content within files.

        Args:
            query: Search query (text or regex)
            path: Starting directory (default: root)
            file_pattern: Glob pattern for files to search (default: all files)
            case_sensitive: If True, search is case-sensitive
            regex: If True, treat query as regex pattern

        Returns:
            Dict with search results
        """
        target = self._resolve_path(path)

        if not target.exists():
            raise FileNotFoundError(f"Directory not found: {path}")

        if not target.is_dir():
            raise ValueError(f"Path is not a directory: {path}")

        # Compile search pattern
        if regex:
            flags = 0 if case_sensitive else re.IGNORECASE
            try:
                pattern = re.compile(query, flags)
            except re.error as e:
                raise ValueError(f"Invalid regex pattern: {e}")
        else:
            # Escape query for literal matching
            escaped_query = re.escape(query)
            flags = 0 if case_sensitive else re.IGNORECASE
            pattern = re.compile(escaped_query, flags)

        # Search in files
        results = []
        files_searched = 0

        for item in target.rglob(file_pattern):
            if len(results) >= self.max_search_results:
                break

            if not item.is_file():
                continue

            # Skip large files
            try:
                if item.stat().st_size > self.max_file_size:
                    continue
            except (PermissionError, OSError):
                continue

            files_searched += 1

            # Search in file
            try:
                content = item.read_text(encoding="utf-8", errors="ignore")
                matches = []

                for line_num, line in enumerate(content.splitlines(), start=1):
                    if pattern.search(line):
                        matches.append({
                            "line_number": line_num,
                            "line": line.strip(),
                        })

                if matches:
                    relative_path = item.relative_to(self.root_dir)
                    results.append({
                        "path": str(relative_path),
                        "absolute_path": str(item),
                        "matches": matches[:10],  # Limit matches per file
                        "total_matches": len(matches),
                    })
            except (PermissionError, OSError, UnicodeDecodeError):
                continue  # Skip problematic files

        return {
            "query": query,
            "search_directory": str(target.relative_to(self.root_dir)),
            "file_pattern": file_pattern,
            "case_sensitive": case_sensitive,
            "regex": regex,
            "files_searched": files_searched,
            "results": results,
            "count": len(results),
            "truncated": len(results) >= self.max_search_results,
        }

    async def delete_file(self, path: str) -> Dict[str, Any]:
        """
        Delete a file.

        Args:
            path: File path

        Returns:
            Dict with operation details
        """
        target = self._resolve_path(path)

        if not target.exists():
            raise FileNotFoundError(f"File not found: {path}")

        if not target.is_file():
            raise ValueError(f"Path is not a file: {path}")

        # Get file info before deletion
        size = target.stat().st_size
        relative_path = str(target.relative_to(self.root_dir))

        # Delete file
        target.unlink()

        return {
            "path": relative_path,
            "deleted": True,
            "size_bytes": size,
        }

    async def create_directory(self, path: str) -> Dict[str, Any]:
        """
        Create a directory.

        Args:
            path: Directory path

        Returns:
            Dict with operation details
        """
        target = self._resolve_path(path)

        # Create directory (with parents if needed)
        target.mkdir(parents=True, exist_ok=True)

        return {
            "path": str(target.relative_to(self.root_dir)),
            "absolute_path": str(target),
            "created": True,
        }

    async def get_file_info(self, path: str) -> Dict[str, Any]:
        """
        Get file/directory information.

        Args:
            path: File or directory path

        Returns:
            Dict with file information
        """
        target = self._resolve_path(path)

        if not target.exists():
            raise FileNotFoundError(f"Path not found: {path}")

        stat = target.stat()

        return {
            "path": str(target.relative_to(self.root_dir)),
            "absolute_path": str(target),
            "type": "file" if target.is_file() else "directory",
            "size_bytes": stat.st_size if target.is_file() else None,
            "modified": stat.st_mtime,
            "created": stat.st_ctime,
        }
