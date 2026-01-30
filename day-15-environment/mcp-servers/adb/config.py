"""ADB MCP Server configuration."""

SERVER_NAME = "adb"
SERVER_VERSION = "1.0.0"
DESCRIPTION = "Android Debug Bridge (ADB) operations via MCP protocol"

# Default ADB path (will try system PATH first)
DEFAULT_ADB_PATH = "adb"

# Default AVD directory
DEFAULT_AVD_HOME = None  # Will use $HOME/.android/avd

# Default emulator timeouts
DEFAULT_EMULATOR_START_TIMEOUT = 60  # seconds
DEFAULT_ADB_TIMEOUT = 30  # seconds
