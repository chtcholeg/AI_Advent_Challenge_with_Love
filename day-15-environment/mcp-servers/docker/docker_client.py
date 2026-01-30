"""Docker client for container and image operations."""

import asyncio
import json
from typing import Dict, List, Optional, Any
import logging

logger = logging.getLogger(__name__)

try:
    import docker
    from docker.errors import DockerException, NotFound, APIError
    DOCKER_AVAILABLE = True
except ImportError:
    DOCKER_AVAILABLE = False
    logger.warning("docker package not installed. Install: pip install docker")


class DockerClient:
    """Client for Docker operations."""

    def __init__(self, base_url: Optional[str] = None):
        """Initialize Docker client.

        Args:
            base_url: Docker daemon URL (default: unix:///var/run/docker.sock)
        """
        if not DOCKER_AVAILABLE:
            raise ImportError("docker package not installed")

        self.client = docker.DockerClient(base_url=base_url) if base_url else docker.from_env()

    async def list_containers(self, all: bool = False) -> List[Dict[str, Any]]:
        """List Docker containers.

        Args:
            all: Show all containers (default: only running)

        Returns:
            List of container info dictionaries
        """
        try:
            loop = asyncio.get_event_loop()
            containers = await loop.run_in_executor(
                None,
                lambda: self.client.containers.list(all=all)
            )

            result = []
            for container in containers:
                result.append({
                    "id": container.short_id,
                    "name": container.name,
                    "image": container.image.tags[0] if container.image.tags else container.image.short_id,
                    "status": container.status,
                    "created": container.attrs.get("Created", ""),
                    "ports": container.ports,
                    "labels": container.labels
                })

            return result
        except DockerException as e:
            logger.error(f"Failed to list containers: {e}")
            raise RuntimeError(f"Docker error: {str(e)}")

    async def start_container(self, container_id: str) -> Dict[str, str]:
        """Start a container.

        Args:
            container_id: Container ID or name

        Returns:
            Result with status message
        """
        try:
            loop = asyncio.get_event_loop()
            container = await loop.run_in_executor(
                None,
                self.client.containers.get,
                container_id
            )

            await loop.run_in_executor(None, container.start)

            return {
                "status": "started",
                "container_id": container.short_id,
                "name": container.name
            }
        except NotFound:
            raise RuntimeError(f"Container not found: {container_id}")
        except DockerException as e:
            logger.error(f"Failed to start container {container_id}: {e}")
            raise RuntimeError(f"Docker error: {str(e)}")

    async def stop_container(self, container_id: str, timeout: int = 10) -> Dict[str, str]:
        """Stop a container.

        Args:
            container_id: Container ID or name
            timeout: Seconds to wait before killing

        Returns:
            Result with status message
        """
        try:
            loop = asyncio.get_event_loop()
            container = await loop.run_in_executor(
                None,
                self.client.containers.get,
                container_id
            )

            await loop.run_in_executor(None, container.stop, timeout)

            return {
                "status": "stopped",
                "container_id": container.short_id,
                "name": container.name
            }
        except NotFound:
            raise RuntimeError(f"Container not found: {container_id}")
        except DockerException as e:
            logger.error(f"Failed to stop container {container_id}: {e}")
            raise RuntimeError(f"Docker error: {str(e)}")

    async def remove_container(self, container_id: str, force: bool = False) -> Dict[str, str]:
        """Remove a container.

        Args:
            container_id: Container ID or name
            force: Force removal of running container

        Returns:
            Result with status message
        """
        try:
            loop = asyncio.get_event_loop()
            container = await loop.run_in_executor(
                None,
                self.client.containers.get,
                container_id
            )

            name = container.name
            short_id = container.short_id

            await loop.run_in_executor(None, container.remove, force)

            return {
                "status": "removed",
                "container_id": short_id,
                "name": name
            }
        except NotFound:
            raise RuntimeError(f"Container not found: {container_id}")
        except DockerException as e:
            logger.error(f"Failed to remove container {container_id}: {e}")
            raise RuntimeError(f"Docker error: {str(e)}")

    async def get_container_logs(
        self,
        container_id: str,
        tail: int = 100,
        timestamps: bool = False
    ) -> str:
        """Get container logs.

        Args:
            container_id: Container ID or name
            tail: Number of lines from the end (default: 100)
            timestamps: Include timestamps

        Returns:
            Container logs as string
        """
        try:
            loop = asyncio.get_event_loop()
            container = await loop.run_in_executor(
                None,
                self.client.containers.get,
                container_id
            )

            logs = await loop.run_in_executor(
                None,
                lambda: container.logs(tail=tail, timestamps=timestamps).decode('utf-8', errors='replace')
            )

            return logs
        except NotFound:
            raise RuntimeError(f"Container not found: {container_id}")
        except DockerException as e:
            logger.error(f"Failed to get logs for {container_id}: {e}")
            raise RuntimeError(f"Docker error: {str(e)}")

    async def inspect_container(self, container_id: str) -> Dict[str, Any]:
        """Inspect container details.

        Args:
            container_id: Container ID or name

        Returns:
            Detailed container information
        """
        try:
            loop = asyncio.get_event_loop()
            container = await loop.run_in_executor(
                None,
                self.client.containers.get,
                container_id
            )

            attrs = container.attrs

            return {
                "id": container.short_id,
                "name": container.name,
                "image": container.image.tags[0] if container.image.tags else container.image.short_id,
                "status": container.status,
                "state": attrs.get("State", {}),
                "config": {
                    "hostname": attrs.get("Config", {}).get("Hostname"),
                    "env": attrs.get("Config", {}).get("Env", []),
                    "cmd": attrs.get("Config", {}).get("Cmd"),
                    "working_dir": attrs.get("Config", {}).get("WorkingDir")
                },
                "network": attrs.get("NetworkSettings", {}),
                "mounts": attrs.get("Mounts", [])
            }
        except NotFound:
            raise RuntimeError(f"Container not found: {container_id}")
        except DockerException as e:
            logger.error(f"Failed to inspect container {container_id}: {e}")
            raise RuntimeError(f"Docker error: {str(e)}")

    async def list_images(self) -> List[Dict[str, Any]]:
        """List Docker images.

        Returns:
            List of image info dictionaries
        """
        try:
            loop = asyncio.get_event_loop()
            images = await loop.run_in_executor(None, self.client.images.list)

            result = []
            for image in images:
                result.append({
                    "id": image.short_id.replace("sha256:", ""),
                    "tags": image.tags,
                    "created": image.attrs.get("Created", ""),
                    "size": self._format_size(image.attrs.get("Size", 0)),
                    "labels": image.labels
                })

            return result
        except DockerException as e:
            logger.error(f"Failed to list images: {e}")
            raise RuntimeError(f"Docker error: {str(e)}")

    async def pull_image(self, image: str, tag: str = "latest") -> Dict[str, str]:
        """Pull Docker image from registry.

        Args:
            image: Image name (e.g., "nginx", "myrepo/myimage")
            tag: Image tag (default: "latest")

        Returns:
            Result with status message
        """
        try:
            loop = asyncio.get_event_loop()
            full_name = f"{image}:{tag}"

            logger.info(f"Pulling image: {full_name}")

            image_obj = await loop.run_in_executor(
                None,
                self.client.images.pull,
                image,
                tag
            )

            return {
                "status": "pulled",
                "image": full_name,
                "id": image_obj.short_id.replace("sha256:", "")
            }
        except DockerException as e:
            logger.error(f"Failed to pull image {image}:{tag}: {e}")
            raise RuntimeError(f"Docker error: {str(e)}")

    async def remove_image(self, image_id: str, force: bool = False) -> Dict[str, str]:
        """Remove Docker image.

        Args:
            image_id: Image ID or tag
            force: Force removal

        Returns:
            Result with status message
        """
        try:
            loop = asyncio.get_event_loop()

            await loop.run_in_executor(
                None,
                self.client.images.remove,
                image_id,
                force
            )

            return {
                "status": "removed",
                "image": image_id
            }
        except NotFound:
            raise RuntimeError(f"Image not found: {image_id}")
        except DockerException as e:
            logger.error(f"Failed to remove image {image_id}: {e}")
            raise RuntimeError(f"Docker error: {str(e)}")

    async def execute_command(
        self,
        container_id: str,
        command: str
    ) -> Dict[str, Any]:
        """Execute command in running container.

        Args:
            container_id: Container ID or name
            command: Command to execute

        Returns:
            Command output and exit code
        """
        try:
            loop = asyncio.get_event_loop()
            container = await loop.run_in_executor(
                None,
                self.client.containers.get,
                container_id
            )

            # Execute command
            exec_result = await loop.run_in_executor(
                None,
                container.exec_run,
                command
            )

            exit_code, output = exec_result

            return {
                "exit_code": exit_code,
                "output": output.decode('utf-8', errors='replace'),
                "success": exit_code == 0
            }
        except NotFound:
            raise RuntimeError(f"Container not found: {container_id}")
        except DockerException as e:
            logger.error(f"Failed to execute command in {container_id}: {e}")
            raise RuntimeError(f"Docker error: {str(e)}")

    def _format_size(self, size_bytes: int) -> str:
        """Format size in bytes to human-readable string."""
        for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
            if size_bytes < 1024.0:
                return f"{size_bytes:.1f} {unit}"
            size_bytes /= 1024.0
        return f"{size_bytes:.1f} PB"

    async def get_system_info(self) -> Dict[str, Any]:
        """Get Docker system information.

        Returns:
            System info dictionary
        """
        try:
            loop = asyncio.get_event_loop()
            info = await loop.run_in_executor(None, self.client.info)

            return {
                "containers": info.get("Containers", 0),
                "containers_running": info.get("ContainersRunning", 0),
                "containers_paused": info.get("ContainersPaused", 0),
                "containers_stopped": info.get("ContainersStopped", 0),
                "images": info.get("Images", 0),
                "server_version": info.get("ServerVersion", ""),
                "os": info.get("OperatingSystem", ""),
                "architecture": info.get("Architecture", ""),
                "memory": self._format_size(info.get("MemTotal", 0))
            }
        except DockerException as e:
            logger.error(f"Failed to get system info: {e}")
            raise RuntimeError(f"Docker error: {str(e)}")
