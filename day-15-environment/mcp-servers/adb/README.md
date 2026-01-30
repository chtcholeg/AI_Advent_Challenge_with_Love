# ADB MCP Server

MCP server for Android Debug Bridge (ADB) and emulator control. Enables AI agents to manage Android devices, run emulators, install apps, and take screenshots.

## Features

- **Device Management**: List connected devices and emulators
- **Emulator Control**: Start/stop AVDs, including headless mode
- **APK Build**: Build APK from source using Gradle
- **App Deployment**: Install APK files
- **Screenshots**: Capture device screen (works in headless mode!)
- **Shell Commands**: Execute arbitrary ADB commands
- **Device Info**: Get model, version, battery level, etc.

## Headless Mode (Default)

The emulator runs **without GUI by default** - perfect for servers and CI/CD:

```json
{
  "name": "start_emulator",
  "arguments": {
    "avd_name": "Pixel_6_API_34"
  }
}
```

To run with GUI window, set `no_window: false`:

```json
{
  "name": "start_emulator",
  "arguments": {
    "avd_name": "Pixel_6_API_34",
    "no_window": false
  }
}
```

Even in headless mode:
- Android system fully functional
- APK installation works
- **Screenshots work** (captures virtual screen)
- All ADB commands work

## Tools

| Tool | Description | Key Parameters |
|------|-------------|----------------|
| `list_devices` | List connected devices/emulators | - |
| `list_avds` | List available AVDs | - |
| `start_emulator` | Start AVD | `avd_name`, `no_window`, `no_audio`, `timeout` |
| `stop_emulator` | Stop emulator | `device_id` |
| `build_apk` | Build APK via Gradle (auto-detects module) | `project_path`, `build_type`, `module`, `clean`, `timeout` |
| `install_apk` | Install APK | `apk_path`, `device_id`, `replace` |
| `screenshot` | Take screenshot | `device_id`, `output_path` |
| `execute_adb` | Run ADB command | `command`, `device_id`, `timeout` |
| `get_device_info` | Get device info | `device_id` |

## Quick Start

```bash
# Start server
python -m adb.main --no-auth --port 8007

# Or via launcher
python launcher.py adb --no-auth
```

## Prerequisites

1. **Android SDK** with platform-tools and emulator
2. **AVD created** via Android Studio or avdmanager
3. **ADB in PATH** or set ANDROID_HOME

### Check Setup

```bash
# Verify ADB
adb version

# List AVDs
emulator -list-avds

# List devices
adb devices
```

## Usage Examples

### List Available Emulators

```
User: Какие эмуляторы доступны?

AI calls: list_avds()

Result:
[
  {"name": "Pixel_6_API_34", "path": "/Users/.../.android/avd/Pixel_6_API_34.avd"},
  {"name": "Nexus_5X_API_30", "path": "/Users/.../.android/avd/Nexus_5X_API_30.avd"}
]
```

### Start Emulator (Headless by Default)

```
User: Запусти эмулятор Pixel_6

AI calls: start_emulator(avd_name="Pixel_6_API_34")

Result:
{
  "status": "started",
  "device_id": "emulator-5554",
  "avd_name": "Pixel_6_API_34"
}
```

### Start Emulator with GUI Window

```
User: Запусти эмулятор Pixel_6 с окном

AI calls: start_emulator(avd_name="Pixel_6_API_34", no_window=false)

Result:
{
  "status": "started",
  "device_id": "emulator-5554",
  "avd_name": "Pixel_6_API_34"
}
```

### Build APK

```
User: Собери debug APK из проекта /path/to/project

AI calls: build_apk(project_path="/path/to/project")

Result:
{
  "status": "success",
  "build_type": "debug",
  "module": "app",  // auto-detected
  "apk_path": "/path/to/project/app/build/outputs/apk/debug/app-debug.apk",
  "output": "BUILD SUCCESSFUL in 45s..."
}
```

Module is auto-detected from: `composeApp`, `app`, or `androidApp`.

### Build Release APK with Clean

```
User: Сделай clean build release APK

AI calls: build_apk(project_path="/path/to/project", build_type="release", clean=true)

Result:
{
  "status": "success",
  "build_type": "release",
  "apk_path": "/path/to/project/composeApp/build/outputs/apk/release/composeApp-release.apk"
}
```

### Install and Launch App

```
User: Установи приложение и запусти его

AI calls: install_apk(apk_path="./app-debug.apk")

Result: {"status": "installed", "apk": "app-debug.apk", "device_id": "emulator-5554"}

AI calls: execute_adb(command="shell am start -n com.example.app/.MainActivity")

Result: Starting: Intent { cmp=com.example.app/.MainActivity }
```

### Take Screenshot

```
User: Сделай скриншот экрана

AI calls: screenshot()

Result:
{
  "status": "captured",
  "device_id": "emulator-5554",
  "path": "/tmp/screenshot_123.png",
  "base64": "iVBORw0KGgo...",
  "format": "png"
}
```

### Get Device Information

```
User: Покажи информацию об устройстве

AI calls: get_device_info()

Result:
{
  "manufacturer": "Google",
  "model": "sdk_gphone64_arm64",
  "version": "14",
  "sdk": "34",
  "resolution": "1080x2400",
  "battery_level": "100",
  "device_id": "emulator-5554"
}
```

### Execute Shell Commands

```
User: Покажи список установленных приложений

AI calls: execute_adb(command="shell pm list packages -3")

Result:
package:com.example.app
package:com.android.chrome
package:org.telegram.messenger
```

### Stop Emulator

```
User: Останови эмулятор

AI calls: stop_emulator()

Result: {"status": "stopped", "device_id": "emulator-5554"}
```

## End-to-End Testing Scenario

```
User: Собери и протестируй приложение на эмуляторе

AI:
1. [list_avds] Checking available emulators...
   Found: Pixel_6_API_34

2. [start_emulator] Starting in headless mode...
   Emulator booted: emulator-5554

3. [build_apk] Building debug APK...
   BUILD SUCCESSFUL in 45s
   APK: composeApp/build/outputs/apk/debug/composeApp-debug.apk

4. [install_apk] Installing APK...
   Installation successful

5. [execute_adb] Launching app...
   MainActivity started

6. [screenshot] Taking verification screenshot...
   Screenshot saved

7. [execute_adb] Running UI tests...
   adb shell am instrument -w com.example.test/androidx.test.runner.AndroidJUnitRunner
   Tests passed: 15/15

Result: Build and tests passed! Screenshot attached.
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ANDROID_HOME` | Android SDK path | auto-detect |
| `ANDROID_SDK_ROOT` | Alternative SDK path | auto-detect |
| `MCP_API_KEY` | API key for auth | None |

## Configuration Options

```bash
python -m adb.main \
  --port 8007 \
  --host 0.0.0.0 \
  --adb-path /path/to/adb \
  --android-home /path/to/sdk \
  --no-auth
```

## Common Issues

### Emulator Won't Start

```
Error: Emulator failed to boot within 180 seconds
```

**Solutions**:
- Increase timeout: `timeout=300`
- Check AVD exists: `emulator -list-avds`
- Check disk space
- Try without GPU: `no_window=true`

### ADB Not Found

```
Error: Command not found: adb
```

**Solutions**:
- Set `ANDROID_HOME` environment variable
- Add platform-tools to PATH
- Use `--adb-path` argument

### No Devices

```
Error: No running emulator found
```

**Solutions**:
- Start emulator first: `start_emulator`
- Check USB debugging enabled (for physical devices)
- Run `adb devices` to verify

### Screenshot Fails

```
Error: screencap failed
```

**Solutions**:
- Wait for emulator to fully boot
- Check device is online: `adb devices`
- Try: `adb shell screencap -p /sdcard/test.png`

## Integration with Docker

For CI/CD, combine with Docker MCP server:

```
1. [build_apk] Build APK from source
2. [start_emulator] Start headless emulator
3. [install_apk] Deploy APK
4. [screenshot] Verify
5. [stop_emulator] Cleanup
```

## Security Notes

- Server can execute any ADB command
- Has full device access via ADB
- Can install any APK
- Use API key authentication in production
- Consider network isolation

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /` | Server info |
| `GET /health` | Health check with ADB path |
| `GET /sse` | SSE connection for MCP |
| `POST /message` | Send MCP message |
| `GET /tools` | List available tools |

## Related Documentation

- [MCP Servers README](../README.md)
- [Docker MCP Server](../docker/README.md)
- [Android ADB Docs](https://developer.android.com/tools/adb)
- [Emulator CLI](https://developer.android.com/studio/run/emulator-commandline)
