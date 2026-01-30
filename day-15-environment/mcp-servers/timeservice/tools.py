"""TimeService MCP tool definitions.

Tools for getting time information:
- get_current_time: Current UTC time
- get_time_in_timezone: Time in a specific IANA timezone
- get_time_in_city: Time in a city (auto-detects timezone)
"""

import logging
from shared import ToolResult, BaseTool
from .time_client import TimeClient

logger = logging.getLogger(__name__)


class TimeTool(BaseTool):
    """Base class for TimeService MCP tools."""

    def __init__(self, client: TimeClient):
        self.client = client

    async def execute(self, arguments: dict) -> ToolResult:
        raise NotImplementedError


# ---------------------------------------------------------------------------
# Tool: get_current_time
# ---------------------------------------------------------------------------

class GetCurrentTimeTool(TimeTool):
    name = "get_current_time"
    description = (
        "Get current UTC time with detailed information including date, time components, "
        "weekday, and Unix timestamp. Returns time in ISO 8601 format."
    )
    input_schema = {
        "type": "object",
        "properties": {},
        "required": [],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            time_info = self.client.get_current_utc()

            lines = [
                "Current UTC Time",
                "=" * 50,
                "",
                f"🕐 Time: {time_info['formatted']}",
                f"📅 Date: {time_info['year']}-{time_info['month']:02d}-{time_info['day']:02d}",
                f"🔢 Components: {time_info['hour']:02d}:{time_info['minute']:02d}:{time_info['second']:02d}",
                f"📆 Weekday: {time_info['weekday']}",
                f"⏱️  Unix Timestamp: {time_info['timestamp']}",
                f"🌐 ISO 8601: {time_info['datetime']}",
            ]

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"GetCurrentTimeTool error: {e}")
            return ToolResult(f"Failed to get current time: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: get_time_in_timezone
# ---------------------------------------------------------------------------

class GetTimeInTimezoneTool(TimeTool):
    name = "get_time_in_timezone"
    description = (
        "Get current time in a specific IANA timezone. "
        "Supports all standard timezone names like 'America/New_York', 'Europe/Moscow', "
        "'Asia/Tokyo', etc. Returns local time with UTC offset."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "timezone": {
                "type": "string",
                "description": "IANA timezone name (e.g., 'America/New_York', 'Europe/London', 'Asia/Tokyo')",
            },
        },
        "required": ["timezone"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        timezone = arguments.get("timezone", "").strip()
        if not timezone:
            return ToolResult("Missing required parameter: timezone", is_error=True)

        try:
            time_info = self.client.get_time_in_timezone(timezone)

            if not time_info:
                return ToolResult(
                    f"Invalid timezone: '{timezone}'. "
                    f"Use IANA timezone names like 'America/New_York', 'Europe/Moscow', etc.",
                    is_error=True,
                )

            lines = [
                f"Current Time in {timezone}",
                "=" * 50,
                "",
                f"🕐 Local Time: {time_info['formatted']}",
                f"📅 Date: {time_info['year']}-{time_info['month']:02d}-{time_info['day']:02d}",
                f"🔢 Components: {time_info['hour']:02d}:{time_info['minute']:02d}:{time_info['second']:02d}",
                f"📆 Weekday: {time_info['weekday']}",
                f"🌐 Timezone: {time_info['timezone']}",
                f"⏰ UTC Offset: {time_info['utc_offset']}",
                f"⏱️  Unix Timestamp: {time_info['timestamp']}",
                f"🌐 ISO 8601: {time_info['datetime']}",
            ]

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"GetTimeInTimezoneTool error: {e}")
            return ToolResult(f"Failed to get time for timezone '{timezone}': {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: get_time_in_city
# ---------------------------------------------------------------------------

class GetTimeInCityTool(TimeTool):
    name = "get_time_in_city"
    description = (
        "Get current local time for a city anywhere in the world. "
        "Automatically detects the city's timezone using geocoding. "
        "Returns time, location details, and coordinates. Supports worldwide cities."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "city": {
                "type": "string",
                "description": "City name (e.g., 'Moscow', 'New York', 'Tokyo', 'London')",
            },
        },
        "required": ["city"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        city = arguments.get("city", "").strip()
        if not city:
            return ToolResult("Missing required parameter: city", is_error=True)

        try:
            time_info = await self.client.get_time_in_city(city)

            if not time_info:
                return ToolResult(
                    f"City not found: '{city}'. Try using English city name.",
                    is_error=True,
                )

            # Format location name
            location_name = time_info["city"]
            if time_info.get("region"):
                location_name += f", {time_info['region']}"
            location_name += f", {time_info['country']}"

            coords = time_info["coordinates"]

            lines = [
                f"Current Time in {location_name}",
                "=" * 50,
                "",
                f"🕐 Local Time: {time_info['formatted']}",
                f"📅 Date: {time_info['year']}-{time_info['month']:02d}-{time_info['day']:02d}",
                f"🔢 Components: {time_info['hour']:02d}:{time_info['minute']:02d}:{time_info['second']:02d}",
                f"📆 Weekday: {time_info['weekday']}",
                "",
                f"🌐 Timezone: {time_info['timezone']}",
                f"⏰ UTC Offset: {time_info['utc_offset']}",
                f"📍 Coordinates: {coords['latitude']:.2f}°, {coords['longitude']:.2f}°",
                "",
                f"⏱️  Unix Timestamp: {time_info['timestamp']}",
                f"🌐 ISO 8601: {time_info['datetime']}",
            ]

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"GetTimeInCityTool error: {e}")
            return ToolResult(f"Failed to get time for city '{city}': {e}", is_error=True)


# ---------------------------------------------------------------------------
# Registry
# ---------------------------------------------------------------------------

def get_all_tools(client: TimeClient) -> list[TimeTool]:
    """Return all registered TimeService MCP tools."""
    return [
        GetCurrentTimeTool(client),
        GetTimeInTimezoneTool(client),
        GetTimeInCityTool(client),
    ]
