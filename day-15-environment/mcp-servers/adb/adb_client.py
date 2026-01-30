"""ADB client for Android device and emulator operations."""

import asyncio
import base64
import os
import subprocess
from pathlib import Path
from typing import Dict, List, Optional, Any, Tuple
import logging

logger = logging.getLogger(__name__)


class ADBClient:
    """Client for Android Debug Bridge operations."""

    def __init__(self, adb_path: str = "adb", android_home: Optional[str] = None):
        """Initialize ADB client.

        Args:
            adb_path: Path to adb executable (default: system PATH)
            android_home: Android SDK home directory
        """
        self.adb_path = adb_path
        self.android_home = android_home or os.getenv("ANDROID_HOME") or os.getenv("ANDROID_SDK_ROOT")

        if self.android_home:
            # Add platform-tools and emulator to PATH
            platform_tools = Path(self.android_home) / "platform-tools"
            emulator_dir = Path(self.android_home) / "emulator"

            if platform_tools.exists():
                self.adb_path = str(platform_tools / "adb")

            if emulator_dir.exists():
                self.emulator_path = str(emulator_dir / "emulator")
            else:
                self.emulator_path = "emulator"
        else:
            self.emulator_path = "emulator"

    async def _run_command(
        self,
        cmd: List[str],
        timeout: int = 30,
        check: bool = True,
        cwd: Optional[str] = None
    ) -> Tuple[int, str, str]:
        """Run command and return (returncode, stdout, stderr).

        Args:
            cmd: Command to execute
            timeout: Timeout in seconds
            check: Raise exception on non-zero exit
            cwd: Working directory for command

        Returns:
            Tuple of (return_code, stdout, stderr)
        """
        try:
            loop = asyncio.get_event_loop()
            process = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                cwd=cwd
            )

            try:
                stdout_bytes, stderr_bytes = await asyncio.wait_for(
                    process.communicate(),
                    timeout=timeout
                )
            except asyncio.TimeoutError:
                process.kill()
                await process.wait()
                raise RuntimeError(f"Command timed out after {timeout}s: {' '.join(cmd)}")

            stdout = stdout_bytes.decode('utf-8', errors='replace').strip()
            stderr = stderr_bytes.decode('utf-8', errors='replace').strip()

            if check and process.returncode != 0:
                error_msg = stderr or stdout or "Command failed"
                raise RuntimeError(f"Command failed: {error_msg}")

            return process.returncode, stdout, stderr

        except FileNotFoundError:
            raise RuntimeError(f"Command not found: {cmd[0]}")
        except Exception as e:
            logger.error(f"Command execution failed: {e}")
            raise

    async def list_devices(self, include_avd_names: bool = True) -> List[Dict[str, str]]:
        """List connected devices and emulators.

        Args:
            include_avd_names: If True, fetch AVD names for emulators (slightly slower)

        Returns:
            List of device info dictionaries
        """
        try:
            _, output, _ = await self._run_command([self.adb_path, "devices", "-l"])

            devices = []
            for line in output.split('\n')[1:]:  # Skip header
                if not line.strip():
                    continue

                parts = line.split()
                if len(parts) < 2:
                    continue

                device_id = parts[0]
                status = parts[1]

                # Parse additional info (model, device, etc.)
                info = {"id": device_id, "status": status}

                for part in parts[2:]:
                    if ':' in part:
                        key, value = part.split(':', 1)
                        info[key] = value

                devices.append(info)

            # Fetch AVD names for emulators
            if include_avd_names:
                for device in devices:
                    if "emulator" in device.get("id", "") and device.get("status") == "device":
                        avd_name = await self._get_avd_name_for_emulator(device["id"])
                        if avd_name:
                            device["avd_name"] = avd_name

            return devices
        except Exception as e:
            logger.error(f"Failed to list devices: {e}")
            raise RuntimeError(f"ADB error: {str(e)}")

    async def list_avds(self) -> List[Dict[str, str]]:
        """List available Android Virtual Devices.

        Returns:
            List of AVD info dictionaries
        """
        try:
            _, output, _ = await self._run_command([self.emulator_path, "-list-avds"])

            avds = []
            for line in output.split('\n'):
                line = line.strip()
                if line:
                    avds.append({
                        "name": line,
                        "path": self._get_avd_path(line)
                    })

            return avds
        except Exception as e:
            logger.error(f"Failed to list AVDs: {e}")
            raise RuntimeError(f"Emulator error: {str(e)}")

    def _get_avd_path(self, avd_name: str) -> str:
        """Get path to AVD directory."""
        avd_home = os.getenv("ANDROID_AVD_HOME")
        if not avd_home:
            avd_home = os.path.expanduser("~/.android/avd")

        return os.path.join(avd_home, f"{avd_name}.avd")

    async def start_emulator(
        self,
        avd_name: str,
        no_window: bool = True,
        no_audio: bool = True,
        timeout: int = 180,
        gpu_mode: str = "auto",
        memory_mb: int = 2048,
        cores: int = 2
    ) -> Dict[str, Any]:
        """Start Android emulator.

        Args:
            avd_name: Name of AVD to start
            no_window: Run headless (no GUI)
            no_audio: Disable audio
            timeout: Seconds to wait for emulator to boot
            gpu_mode: GPU mode - 'auto', 'host', 'swiftshader_indirect', 'off'
            memory_mb: RAM in MB (default 2048)
            cores: Number of CPU cores (default 2)

        Returns:
            Emulator info dictionary
        """
        try:
            # Check if already running
            devices = await self.list_devices()
            for device in devices:
                if device.get("status") == "device" and "emulator" in device.get("id", ""):
                    logger.info(f"Emulator already running: {device['id']}")
                    return {
                        "status": "already_running",
                        "device_id": device["id"]
                    }

            # Build command
            cmd = [self.emulator_path, "-avd", avd_name]

            if no_window:
                cmd.append("-no-window")

            if no_audio:
                cmd.append("-no-audio")

            # Performance optimizations
            cmd.append("-no-boot-anim")      # Skip boot animation
            cmd.append("-no-snapshot-load")  # Don't load snapshot (faster cold boot)
            cmd.append("-no-snapshot-save")  # Don't save snapshot on exit

            # GPU mode: 'auto' tries hardware, falls back to software
            cmd.extend(["-gpu", gpu_mode])

            # Memory and CPU
            cmd.extend(["-memory", str(memory_mb)])
            cmd.extend(["-cores", str(cores)])

            # Try hardware acceleration if available (KVM on Linux)
            cmd.extend(["-accel", "auto"])

            # Reduce skin overhead
            cmd.append("-no-skin")

            # Faster network
            cmd.extend(["-netfast"])

            logger.info(f"Starting emulator: {' '.join(cmd)}")

            # Start emulator in background
            loop = asyncio.get_event_loop()
            process = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE
            )

            # Wait for device to appear
            logger.info("Waiting for emulator to boot...")
            device_id = await self._wait_for_emulator(timeout)

            return {
                "status": "started",
                "device_id": device_id,
                "avd_name": avd_name,
                "pid": process.pid
            }

        except Exception as e:
            logger.error(f"Failed to start emulator: {e}")
            raise RuntimeError(f"Emulator error: {str(e)}")

    async def _wait_for_emulator(self, timeout: int = 60) -> str:
        """Wait for emulator to appear and boot.

        Args:
            timeout: Maximum seconds to wait

        Returns:
            Device ID (e.g., "emulator-5554")
        """
        start_time = asyncio.get_event_loop().time()

        while (asyncio.get_event_loop().time() - start_time) < timeout:
            devices = await self.list_devices(include_avd_names=False)

            for device in devices:
                device_id = device.get("id", "")
                if "emulator" in device_id and device.get("status") == "device":
                    # Wait for boot complete
                    try:
                        _, output, _ = await self._run_command(
                            [self.adb_path, "-s", device_id, "shell", "getprop", "sys.boot_completed"],
                            timeout=5,
                            check=False
                        )

                        if output.strip() == "1":
                            logger.info(f"Emulator booted: {device_id}")
                            # Disable ANR dialogs to prevent "System UI isn't responding"
                            await self._disable_anr_dialogs(device_id)
                            return device_id
                    except Exception:
                        pass

            await asyncio.sleep(2)

        raise RuntimeError(f"Emulator failed to boot within {timeout} seconds")

    async def _disable_anr_dialogs(self, device_id: str) -> None:
        """Disable ANR (Application Not Responding) dialogs on device.

        This prevents 'System UI isn't responding' popups in headless mode.
        """
        try:
            logger.info(f"Disabling ANR dialogs on {device_id}")

            # Wait a bit for settings service to be ready
            await asyncio.sleep(2)

            # Disable ANR dialog for background apps
            await self._run_command(
                [self.adb_path, "-s", device_id, "shell",
                 "settings", "put", "secure", "anr_show_background", "0"],
                timeout=5, check=False
            )

            # Disable wait for debugger dialog
            await self._run_command(
                [self.adb_path, "-s", device_id, "shell",
                 "settings", "put", "global", "always_finish_activities", "0"],
                timeout=5, check=False
            )

            # Dismiss any existing ANR dialog by pressing Enter
            await self._run_command(
                [self.adb_path, "-s", device_id, "shell", "input", "keyevent", "KEYCODE_ENTER"],
                timeout=5, check=False
            )

            logger.info("ANR dialogs disabled")
        except Exception as e:
            logger.warning(f"Failed to disable ANR dialogs: {e}")

    async def dismiss_dialogs(self, device_id: Optional[str] = None) -> Dict[str, Any]:
        """Dismiss system dialogs and disable ANR popups.

        Args:
            device_id: Device ID (default: first device)

        Returns:
            Status dictionary
        """
        try:
            device_id = await self._ensure_device(device_id)

            # Disable ANR dialogs
            await self._disable_anr_dialogs(device_id)

            # Also try to dismiss any current dialog with multiple key events
            for keycode in ["KEYCODE_ENTER", "KEYCODE_ESCAPE", "KEYCODE_BACK"]:
                await self._run_command(
                    [self.adb_path, "-s", device_id, "shell", "input", "keyevent", keycode],
                    timeout=5, check=False
                )
                await asyncio.sleep(0.5)

            return {
                "status": "success",
                "device_id": device_id,
                "message": "ANR dialogs disabled and current dialogs dismissed"
            }

        except Exception as e:
            logger.error(f"Failed to dismiss dialogs: {e}")
            raise RuntimeError(f"ADB error: {str(e)}")

    async def stop_emulator(self, device_id: Optional[str] = None) -> Dict[str, str]:
        """Stop emulator.

        Args:
            device_id: Device ID to stop (default: first emulator)

        Returns:
            Status dictionary
        """
        try:
            # If no device_id, find first emulator
            if not device_id:
                devices = await self.list_devices()
                for device in devices:
                    if "emulator" in device.get("id", ""):
                        device_id = device["id"]
                        break

                if not device_id:
                    raise RuntimeError("No running emulator found")

            # Kill emulator
            await self._run_command(
                [self.adb_path, "-s", device_id, "emu", "kill"],
                timeout=10
            )

            return {
                "status": "stopped",
                "device_id": device_id
            }

        except Exception as e:
            logger.error(f"Failed to stop emulator: {e}")
            raise RuntimeError(f"ADB error: {str(e)}")

    async def _get_package_from_apk(self, apk_path: str) -> Optional[str]:
        """Extract package name from APK using aapt.

        Args:
            apk_path: Path to APK file

        Returns:
            Package name or None if extraction failed
        """
        try:
            # Try aapt from Android SDK
            aapt_path = "aapt"
            if self.android_home:
                build_tools = Path(self.android_home) / "build-tools"
                if build_tools.exists():
                    # Find latest build-tools version
                    versions = sorted(build_tools.iterdir(), reverse=True)
                    if versions:
                        aapt_path = str(versions[0] / "aapt")

            _, output, _ = await self._run_command(
                [aapt_path, "dump", "badging", apk_path],
                timeout=30,
                check=False
            )

            # Parse: package: name='com.example.app' versionCode='1' ...
            for line in output.split('\n'):
                if line.startswith("package:"):
                    # Extract name='...'
                    import re
                    match = re.search(r"name='([^']+)'", line)
                    if match:
                        return match.group(1)

            return None
        except Exception as e:
            logger.warning(f"Failed to extract package name from APK: {e}")
            return None

    async def install_apk(
        self,
        apk_path: str,
        device_id: Optional[str] = None,
        replace: bool = True
    ) -> Dict[str, str]:
        """Install APK on device.

        Args:
            apk_path: Path to APK file
            device_id: Device ID (default: first device)
            replace: Replace existing app

        Returns:
            Installation result with package name for launching
        """
        try:
            # Check if APK exists
            if not Path(apk_path).exists():
                raise RuntimeError(f"APK not found: {apk_path}")

            # Extract package name from APK BEFORE installing
            package_name = await self._get_package_from_apk(apk_path)

            # Ensure device is available (auto-detect if needed)
            device_id = await self._ensure_device(device_id)

            # Build command
            cmd = [self.adb_path, "-s", device_id, "install"]

            if replace:
                cmd.append("-r")

            cmd.append(apk_path)

            logger.info(f"Installing APK: {apk_path}")
            _, output, _ = await self._run_command(cmd, timeout=120)

            if "Success" in output:
                result = {
                    "status": "installed",
                    "apk": os.path.basename(apk_path),
                    "device_id": device_id
                }
                # Include package name for easy launching
                if package_name:
                    result["package"] = package_name
                    result["launch_hint"] = f"Use launch_app with package='{package_name}' to start the app"
                return result
            else:
                raise RuntimeError(f"Installation failed: {output}")

        except Exception as e:
            logger.error(f"Failed to install APK: {e}")
            raise RuntimeError(f"ADB error: {str(e)}")

    async def launch_app(
        self,
        package: str,
        activity: Optional[str] = None,
        device_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """Launch application on device.

        Args:
            package: Package name (e.g., 'ru.chtcholeg.app')
            activity: Activity name (optional, auto-detects launcher activity if not specified)
            device_id: Device ID (default: first device)

        Returns:
            Launch result
        """
        try:
            device_id = await self._ensure_device(device_id)

            if activity:
                # Use specified activity
                component = f"{package}/{activity}"
            else:
                # Auto-detect launcher activity using monkey
                logger.info(f"Auto-detecting launcher activity for {package}")
                _, output, _ = await self._run_command(
                    [self.adb_path, "-s", device_id, "shell",
                     "cmd", "package", "resolve-activity", "--brief", package],
                    timeout=10, check=False
                )

                # Parse output to get activity name
                lines = output.strip().split('\n')
                if len(lines) >= 2:
                    component = lines[-1].strip()
                else:
                    # Fallback: try common activity names
                    component = f"{package}/.MainActivity"

            logger.info(f"Launching: {component}")

            # Launch with am start
            # -S: force stop before start (kill existing instance)
            # -W: wait for launch to complete
            # --activity-clear-top: bring to front if already running
            _, output, stderr = await self._run_command(
                [self.adb_path, "-s", device_id, "shell",
                 "am", "start", "-S", "-W", "--activity-clear-top", "-n", component],
                timeout=30, check=False
            )

            launch_method = "am_start"

            if "Error" in output or "Exception" in output:
                # Try alternative: launch via monkey (simulates launcher tap)
                logger.info("Trying monkey launch...")
                _, output2, _ = await self._run_command(
                    [self.adb_path, "-s", device_id, "shell",
                     "monkey", "-p", package, "-c", "android.intent.category.LAUNCHER", "1"],
                    timeout=15, check=False
                )

                if "No activities found" in output2:
                    raise RuntimeError(f"Cannot launch {package}: {output}")

                output = output2
                launch_method = "monkey"

            # Wait a moment for app to stabilize
            await asyncio.sleep(1.5)

            # Check if app is actually running
            _, pid_output, _ = await self._run_command(
                [self.adb_path, "-s", device_id, "shell", "pidof", package],
                timeout=5, check=False
            )
            pid = pid_output.strip() if pid_output.strip() else None
            app_running = pid is not None

            result = {
                "status": "launched" if app_running else "crashed",
                "package": package,
                "component": component,
                "method": launch_method,
                "device_id": device_id,
                "pid": pid,
                "app_running": app_running,
                "output": output
            }

            # If app crashed or not running, fetch error logs automatically
            if not app_running:
                logger.warning(f"App {package} is not running after launch, fetching crash logs...")

                # Get recent crash logs
                _, crash_logs, _ = await self._run_command(
                    [self.adb_path, "-s", device_id, "logcat",
                     "-d", "-t", "100", "AndroidRuntime:E", "*:S"],
                    timeout=10, check=False
                )

                # Get recent app-specific logs
                _, app_logs, _ = await self._run_command(
                    [self.adb_path, "-s", device_id, "logcat",
                     "-d", "-t", "50", "*:E"],
                    timeout=10, check=False
                )

                # Filter logs for this package
                filtered_crash = []
                if crash_logs:
                    in_crash = False
                    for line in crash_logs.split('\n'):
                        if package in line or 'FATAL EXCEPTION' in line:
                            in_crash = True
                        if in_crash:
                            filtered_crash.append(line)
                        if in_crash and line.strip() == '':
                            in_crash = False

                filtered_app = []
                if app_logs:
                    for line in app_logs.split('\n'):
                        if package in line or 'AndroidRuntime' in line:
                            filtered_app.append(line)

                result["crash_logs"] = '\n'.join(filtered_crash) if filtered_crash else None
                result["error_logs"] = '\n'.join(filtered_app[-30:]) if filtered_app else None
                result["diagnosis"] = "App crashed or failed to start. Check crash_logs and error_logs for details."

            return result

        except Exception as e:
            logger.error(f"Failed to launch app: {e}")
            raise RuntimeError(f"Launch error: {str(e)}")

    async def _get_first_device(self) -> Optional[str]:
        """Get the first available device/emulator ID.

        Returns:
            Device ID or None if no devices are connected
        """
        try:
            devices = await self.list_devices()
            for device in devices:
                if device.get("status") == "device":
                    return device.get("id")
            return None
        except Exception:
            return None

    async def _get_avd_name_for_emulator(self, device_id: str) -> Optional[str]:
        """Get AVD name for a running emulator.

        Args:
            device_id: Emulator device ID (e.g., 'emulator-5554')

        Returns:
            AVD name or None if not an emulator or failed to get name
        """
        if "emulator" not in device_id:
            return None

        try:
            # Get AVD name from system property
            _, output, _ = await self._run_command(
                [self.adb_path, "-s", device_id, "shell", "getprop", "ro.boot.qemu.avd_name"],
                timeout=5,
                check=False
            )
            if output and output.strip():
                return output.strip()
            return None
        except Exception as e:
            logger.debug(f"Failed to get AVD name for {device_id}: {e}")
            return None

    async def _ensure_device(self, device_id: Optional[str] = None) -> str:
        """Ensure a device is available, auto-detecting if needed.

        Args:
            device_id: Specific device ID, AVD name, or None to auto-detect

        Returns:
            Valid device ID

        Raises:
            RuntimeError: If no devices are connected
        """
        if device_id:
            devices = await self.list_devices()

            # 1. Try exact match by serial number
            for device in devices:
                if device.get("id") == device_id and device.get("status") == "device":
                    return device_id

            # 2. Try to find emulator by AVD name
            for device in devices:
                dev_id = device.get("id", "")
                if "emulator" in dev_id and device.get("status") == "device":
                    avd_name = await self._get_avd_name_for_emulator(dev_id)
                    if avd_name and avd_name.lower() == device_id.lower():
                        logger.info(f"Resolved AVD name '{device_id}' to device '{dev_id}'")
                        return dev_id

            raise RuntimeError(
                f"Device '{device_id}' not found or not ready. "
                f"Run 'adb devices' to check connected devices. "
                f"You can use either serial number (e.g., 'emulator-5554') or AVD name."
            )

        # Auto-detect first available device
        detected = await self._get_first_device()
        if not detected:
            # Try restarting ADB server once
            logger.info("No devices found, attempting to restart ADB server...")
            try:
                await self._run_command([self.adb_path, "kill-server"], timeout=5, check=False)
                await asyncio.sleep(1)
                await self._run_command([self.adb_path, "start-server"], timeout=10, check=False)
                await asyncio.sleep(2)
                detected = await self._get_first_device()
            except Exception as e:
                logger.warning(f"Failed to restart ADB server: {e}")

        if not detected:
            raise RuntimeError(
                "No devices/emulators connected. "
                "Please connect a device via USB or start an emulator. "
                "Use 'list_devices' to check connection status."
            )

        return detected

    async def restart_adb_server(self) -> Dict[str, str]:
        """Restart ADB server to fix connection issues.

        Returns:
            Status dictionary
        """
        try:
            logger.info("Killing ADB server...")
            await self._run_command([self.adb_path, "kill-server"], timeout=10, check=False)
            await asyncio.sleep(1)

            logger.info("Starting ADB server...")
            await self._run_command([self.adb_path, "start-server"], timeout=15, check=False)
            await asyncio.sleep(2)

            # Check if devices are now visible
            devices = await self.list_devices()
            device_count = len([d for d in devices if d.get("status") == "device"])

            return {
                "status": "restarted",
                "message": f"ADB server restarted. Found {device_count} connected device(s).",
                "devices_found": device_count
            }

        except Exception as e:
            logger.error(f"Failed to restart ADB server: {e}")
            raise RuntimeError(f"ADB error: {str(e)}")

    async def screenshot(
        self,
        device_id: Optional[str] = None,
        output_path: Optional[str] = None
    ) -> Dict[str, str]:
        """Take screenshot of device screen.

        Args:
            device_id: Device ID (default: first device)
            output_path: Local path to save screenshot (default: temp file)

        Returns:
            Screenshot info with base64 data
        """
        try:
            # Ensure device is available (auto-detect if needed)
            device_id = await self._ensure_device(device_id)

            # Create temp file if no output path
            if not output_path:
                import tempfile
                fd, output_path = tempfile.mkstemp(suffix=".png")
                os.close(fd)

            # Take screenshot on device
            device_path = "/sdcard/screenshot.png"

            cmd = [self.adb_path]
            cmd.extend(["-s", device_id])

            cmd.extend(["shell", "screencap", "-p", device_path])
            await self._run_command(cmd, timeout=60)  # Increased timeout for slow emulators

            # Pull screenshot
            cmd = [self.adb_path, "-s", device_id, "pull", device_path, output_path]
            await self._run_command(cmd, timeout=30)

            # Clean up device
            cmd = [self.adb_path, "-s", device_id, "shell", "rm", device_path]
            await self._run_command(cmd, timeout=5, check=False)

            # Read screenshot and encode base64
            with open(output_path, "rb") as f:
                image_data = base64.b64encode(f.read()).decode('utf-8')

            return {
                "status": "captured",
                "device_id": device_id,
                "path": output_path,
                "base64": image_data,
                "format": "png"
            }

        except Exception as e:
            logger.error(f"Failed to take screenshot: {e}")
            raise RuntimeError(f"ADB error: {str(e)}")

    async def get_app_logs(
        self,
        package: str,
        lines: int = 100,
        level: str = "D",
        device_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """Get application logs filtered by package.

        Args:
            package: Package name to filter
            lines: Number of lines to return
            level: Minimum log level (V, D, I, W, E, F)
            device_id: Device ID (default: first device)

        Returns:
            Logs dictionary
        """
        try:
            device_id = await self._ensure_device(device_id)

            # Get PID of the app
            _, pid_output, _ = await self._run_command(
                [self.adb_path, "-s", device_id, "shell", "pidof", package],
                timeout=5, check=False
            )
            pid = pid_output.strip() if pid_output.strip() else None

            # Build logcat command
            # Use --pid if available, otherwise filter by tag pattern
            if pid:
                cmd = [
                    self.adb_path, "-s", device_id, "logcat",
                    "-d",  # dump and exit
                    "--pid", pid,
                    f"-t", str(lines),
                    f"*:{level}"
                ]
            else:
                # Fallback: filter by package name in log messages
                cmd = [
                    self.adb_path, "-s", device_id, "logcat",
                    "-d",
                    f"-t", str(lines),
                    f"*:{level}"
                ]

            _, output, _ = await self._run_command(cmd, timeout=30, check=False)

            # If no PID, filter output by package name
            if not pid and output:
                filtered_lines = []
                for line in output.split('\n'):
                    if package in line or 'AndroidRuntime' in line or 'FATAL' in line:
                        filtered_lines.append(line)
                output = '\n'.join(filtered_lines[-lines:])

            # Also get crash logs
            _, crash_output, _ = await self._run_command(
                [self.adb_path, "-s", device_id, "logcat",
                 "-d", "-t", "50", "AndroidRuntime:E", "*:S"],
                timeout=10, check=False
            )

            # Filter crash logs for this package
            crash_lines = []
            if crash_output:
                in_crash = False
                for line in crash_output.split('\n'):
                    if package in line:
                        in_crash = True
                    if in_crash:
                        crash_lines.append(line)
                    if in_crash and line.strip() == '':
                        in_crash = False

            return {
                "package": package,
                "pid": pid,
                "device_id": device_id,
                "log_level": level,
                "logs": output,
                "crash_logs": '\n'.join(crash_lines) if crash_lines else None,
                "app_running": pid is not None
            }

        except Exception as e:
            logger.error(f"Failed to get app logs: {e}")
            raise RuntimeError(f"ADB error: {str(e)}")

    async def execute_adb(
        self,
        command: str,
        device_id: Optional[str] = None,
        timeout: int = 30
    ) -> str:
        """Execute arbitrary ADB command.

        Args:
            command: ADB command (without 'adb' prefix)
            device_id: Device ID (optional, auto-detected for device-specific commands)
            timeout: Command timeout

        Returns:
            Command output
        """
        try:
            # Commands that require a device
            device_commands = (
                "shell", "logcat", "push", "pull", "install", "uninstall",
                "forward", "reverse", "reboot", "bugreport", "emu", "sync",
                "root", "unroot", "remount", "jdwp", "backup", "restore"
            )

            # Check if command requires a device (auto-detect if needed)
            command_parts = command.split()
            needs_device = command_parts and command_parts[0] in device_commands

            cmd = [self.adb_path]

            if needs_device and not device_id:
                device_id = await self._ensure_device(None)

            if device_id:
                cmd.extend(["-s", device_id])

            # Split command into parts
            cmd.extend(command_parts)

            _, output, stderr = await self._run_command(cmd, timeout=timeout, check=False)

            return output or stderr

        except Exception as e:
            logger.error(f"Failed to execute ADB command: {e}")
            raise RuntimeError(f"ADB error: {str(e)}")

    async def get_device_info(self, device_id: Optional[str] = None) -> Dict[str, Any]:
        """Get detailed device information.

        Args:
            device_id: Device ID (default: first device)

        Returns:
            Device info dictionary
        """
        try:
            # Ensure device is available (auto-detect if needed)
            device_id = await self._ensure_device(device_id)

            cmd_base = [self.adb_path, "-s", device_id]

            # Get various properties
            properties = {
                "manufacturer": "ro.product.manufacturer",
                "model": "ro.product.model",
                "version": "ro.build.version.release",
                "sdk": "ro.build.version.sdk",
                "device": "ro.product.device",
                "abi": "ro.product.cpu.abi"
            }

            info = {}
            for key, prop in properties.items():
                cmd = cmd_base + ["shell", "getprop", prop]
                _, value, _ = await self._run_command(cmd, timeout=5, check=False)
                info[key] = value.strip()

            # Get screen resolution
            cmd = cmd_base + ["shell", "wm", "size"]
            _, output, _ = await self._run_command(cmd, timeout=5, check=False)
            if "Physical size:" in output:
                resolution = output.split("Physical size:")[-1].strip()
                info["resolution"] = resolution

            # Get battery level
            cmd = cmd_base + ["shell", "dumpsys", "battery"]
            _, output, _ = await self._run_command(cmd, timeout=5, check=False)
            for line in output.split('\n'):
                if "level:" in line:
                    info["battery_level"] = line.split(":")[-1].strip()

            info["device_id"] = device_id

            return info

        except Exception as e:
            logger.error(f"Failed to get device info: {e}")
            raise RuntimeError(f"ADB error: {str(e)}")

    async def build_apk(
        self,
        project_path: str,
        build_type: str = "debug",
        module: Optional[str] = None,
        clean: bool = False,
        timeout: int = 600
    ) -> Dict[str, Any]:
        """Build APK using Gradle.

        Args:
            project_path: Path to Android project root (containing gradlew)
            build_type: Build type - 'debug' or 'release' (default: debug)
            module: Module name to build (auto-detect if not specified)
            clean: Run clean before build (default: false)
            timeout: Build timeout in seconds (default: 600)

        Returns:
            Build result with APK path
        """
        try:
            project_dir = Path(project_path).expanduser().resolve()

            # Check if project directory exists
            if not project_dir.exists():
                raise RuntimeError(f"Project directory not found: {project_path}")

            # Find gradlew
            gradlew = project_dir / "gradlew"
            if not gradlew.exists():
                raise RuntimeError(f"gradlew not found in {project_path}")

            # Ensure gradlew is executable
            if not os.access(gradlew, os.X_OK):
                os.chmod(gradlew, 0o755)

            # Auto-detect module if not specified
            if not module:
                # Check common module names
                for candidate in ["composeApp", "app", "androidApp"]:
                    if (project_dir / candidate).exists():
                        module = candidate
                        break
                if not module:
                    module = "app"  # Default fallback

            # Build task name
            task = f":{module}:assemble{build_type.capitalize()}"

            # Build command - use ./gradlew from project directory
            cmd = ["./gradlew"]

            if clean:
                cmd.append("clean")

            cmd.append(task)

            logger.info(f"Building APK: {' '.join(cmd)} in {project_dir}")

            # Run gradle build from project directory
            returncode, stdout, stderr = await self._run_command(
                cmd,
                timeout=timeout,
                check=False,
                cwd=str(project_dir)
            )

            # Check build result
            if returncode != 0:
                error_msg = stderr or stdout or "Build failed"
                raise RuntimeError(f"Gradle build failed: {error_msg}")

            # Find APK file
            apk_dir = project_dir / module / "build" / "outputs" / "apk" / build_type
            apk_files = list(apk_dir.glob("*.apk")) if apk_dir.exists() else []

            if not apk_files:
                # Try alternative path for Compose Multiplatform
                apk_dir = project_dir / module / "build" / "outputs" / "apk" / "debug"
                apk_files = list(apk_dir.glob("*.apk")) if apk_dir.exists() else []

            apk_path = str(apk_files[0]) if apk_files else None

            return {
                "status": "success",
                "build_type": build_type,
                "module": module,
                "apk_path": apk_path,
                "output": stdout[-2000:] if len(stdout) > 2000 else stdout  # Last 2000 chars
            }

        except Exception as e:
            logger.error(f"Failed to build APK: {e}")
            raise RuntimeError(f"Build error: {str(e)}")
