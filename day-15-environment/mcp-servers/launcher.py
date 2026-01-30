#!/usr/bin/env python3
"""MCP Servers Launcher.

Centralized launcher for all MCP servers with port management.

Usage:
    python launcher.py                    # List all servers and ports
    python launcher.py telegram           # Start telegram server
    python launcher.py telegram github    # Start multiple servers
    python launcher.py --all              # Start all servers
    python launcher.py --all --no-auth    # Start all without auth
    python launcher.py --check            # Check which ports are in use

Environment:
    MCP_API_KEY    Shared API key for all servers (unless --no-auth)
"""

import argparse
import asyncio
import importlib
import signal
import socket
import sys
from dataclasses import dataclass
from typing import Optional

# =============================================================================
# Server Registry - Add new servers here
# =============================================================================

@dataclass
class ServerConfig:
    name: str
    module: str
    port: int
    description: str


SERVERS: dict[str, ServerConfig] = {
    "github": ServerConfig(
        name="github",
        module="github.main",
        port=8000,
        description="GitHub repository data",
    ),
    "telegram": ServerConfig(
        name="telegram",
        module="telegram.main",
        port=8001,
        description="Telegram channel reader",
    ),
    "weather": ServerConfig(
        name="weather",
        module="weather.main",
        port=8002,
        description="Weather data (Open-Meteo)",
    ),
    "timeservice": ServerConfig(
        name="timeservice",
        module="timeservice.main",
        port=8003,
        description="Current time in any timezone/city",
    ),
    "currency": ServerConfig(
        name="currency",
        module="currency.main",
        port=8004,
        description="Currency exchange rates (Frankfurter)",
    ),
    "fileops": ServerConfig(
        name="fileops",
        module="fileops.main",
        port=8005,
        description="File system operations (read, write, search)",
    ),
    "docker": ServerConfig(
        name="docker",
        module="docker.main",
        port=8006,
        description="Docker container and image management",
    ),
    "adb": ServerConfig(
        name="adb",
        module="adb.main",
        port=8007,
        description="Android Debug Bridge (ADB) and emulator control",
    ),
    # Add new servers here:
    # "calendar": ServerConfig(
    #     name="calendar",
    #     module="calendar.main",
    #     port=8008,
    #     description="Google Calendar integration",
    # ),
}


# =============================================================================
# Port utilities
# =============================================================================

def is_port_free(port: int, host: str = "127.0.0.1") -> bool:
    """Check if a port is available."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.bind((host, port))
            return True
    except OSError:
        return False


def check_ports() -> dict[str, bool]:
    """Check availability of all registered ports."""
    return {name: is_port_free(cfg.port) for name, cfg in SERVERS.items()}


def get_next_free_port(start: int = 8000, end: int = 8099) -> Optional[int]:
    """Find next available port in range."""
    for port in range(start, end + 1):
        if is_port_free(port):
            return port
    return None


# =============================================================================
# Server management
# =============================================================================

def list_servers():
    """Print table of all registered servers."""
    print("\nRegistered MCP Servers:")
    print("=" * 60)
    print(f"{'Name':<12} {'Port':<8} {'Status':<10} Description")
    print("-" * 60)

    port_status = check_ports()
    for name, cfg in sorted(SERVERS.items(), key=lambda x: x[1].port):
        status = "free" if port_status[name] else "IN USE"
        status_color = "" if port_status[name] else "!"
        print(f"{status_color}{name:<12} {cfg.port:<8} {status:<10} {cfg.description}")

    print("-" * 60)
    print(f"Total: {len(SERVERS)} servers\n")


def validate_servers(names: list[str]) -> list[ServerConfig]:
    """Validate server names and return configs."""
    configs = []
    for name in names:
        if name not in SERVERS:
            print(f"Error: Unknown server '{name}'")
            print(f"Available: {', '.join(SERVERS.keys())}")
            sys.exit(1)
        configs.append(SERVERS[name])
    return configs


async def run_server(config: ServerConfig, no_auth: bool = False):
    """Run a single server."""
    # Build command
    cmd = [sys.executable, "-m", config.module, "--port", str(config.port)]
    if no_auth:
        cmd.append("--no-auth")

    print(f"Starting {config.name} on port {config.port}...")

    process = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
    )

    # Stream output with prefix
    async for line in process.stdout:
        text = line.decode().rstrip()
        print(f"[{config.name}] {text}")

    await process.wait()
    return process.returncode


async def run_servers(configs: list[ServerConfig], no_auth: bool = False):
    """Run multiple servers concurrently."""
    # Check ports first
    for cfg in configs:
        if not is_port_free(cfg.port):
            print(f"Error: Port {cfg.port} is already in use (needed for {cfg.name})")
            sys.exit(1)

    print(f"\nStarting {len(configs)} server(s)...")
    print("Press Ctrl+C to stop all servers\n")

    # Create tasks for all servers
    tasks = [run_server(cfg, no_auth) for cfg in configs]

    try:
        await asyncio.gather(*tasks)
    except asyncio.CancelledError:
        print("\nShutting down servers...")


def main():
    parser = argparse.ArgumentParser(
        description="MCP Servers Launcher",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python launcher.py                    List all servers
  python launcher.py telegram           Start telegram server
  python launcher.py telegram github    Start multiple servers
  python launcher.py --all              Start all servers
  python launcher.py --all --no-auth    Start all without authentication
  python launcher.py --check            Check port availability
        """,
    )
    parser.add_argument(
        "servers",
        nargs="*",
        help="Server names to start",
    )
    parser.add_argument(
        "--all", "-a",
        action="store_true",
        help="Start all registered servers",
    )
    parser.add_argument(
        "--no-auth",
        action="store_true",
        help="Disable API key authentication",
    )
    parser.add_argument(
        "--check", "-c",
        action="store_true",
        help="Check port availability and exit",
    )
    parser.add_argument(
        "--list", "-l",
        action="store_true",
        help="List all servers and exit",
    )

    args = parser.parse_args()

    # Handle info commands
    if args.list or args.check or (not args.servers and not args.all):
        list_servers()
        return

    # Determine which servers to run
    if args.all:
        configs = list(SERVERS.values())
    else:
        configs = validate_servers(args.servers)

    # Sort by port for consistent output
    configs.sort(key=lambda c: c.port)

    # Run servers
    try:
        asyncio.run(run_servers(configs, args.no_auth))
    except KeyboardInterrupt:
        print("\nAll servers stopped.")


if __name__ == "__main__":
    main()
