"""Weather MCP tool definitions.

Tools for getting weather data via Open-Meteo API:
- get_current_weather: Current weather for a city
- get_weather_forecast: Multi-day weather forecast
"""

import logging
from shared import ToolResult, BaseTool
from .weather_client import WeatherClient

logger = logging.getLogger(__name__)


class WeatherTool(BaseTool):
    """Base class for Weather MCP tools."""

    def __init__(self, client: WeatherClient):
        self.client = client

    async def execute(self, arguments: dict) -> ToolResult:
        raise NotImplementedError


def _wind_direction_to_text(degrees: float) -> str:
    """Convert wind direction degrees to compass direction."""
    if degrees is None:
        return "N/A"
    directions = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                  "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"]
    index = round(degrees / 22.5) % 16
    return directions[index]


# ---------------------------------------------------------------------------
# Tool: get_current_weather
# ---------------------------------------------------------------------------

class GetCurrentWeatherTool(WeatherTool):
    name = "get_current_weather"
    description = (
        "Get current weather conditions for a city. "
        "Returns temperature, humidity, wind, precipitation, and weather description. "
        "Supports cities worldwide. Use English city names for best results."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "city": {
                "type": "string",
                "description": "City name (e.g., 'Moscow', 'New York', 'Tokyo')",
            },
        },
        "required": ["city"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        city = arguments.get("city", "").strip()
        if not city:
            return ToolResult("Missing required parameter: city", is_error=True)

        try:
            # Geocode city to coordinates
            location = await self.client.geocode(city)
            if not location:
                return ToolResult(
                    f"City not found: '{city}'. Try using English city name.",
                    is_error=True,
                )

            # Get current weather
            weather = await self.client.get_current_weather(
                location["latitude"], location["longitude"]
            )

            # Format location name
            location_name = location["name"]
            if location.get("admin1"):
                location_name += f", {location['admin1']}"
            location_name += f", {location['country']}"

            wind_dir = _wind_direction_to_text(weather.get("wind_direction"))

            lines = [
                f"Current Weather for {location_name}",
                "=" * 50,
                "",
                f"🌡️  Temperature: {weather['temperature']}°C",
                f"🤒 Feels like: {weather['feels_like']}°C",
                f"☁️  Conditions: {weather['weather_description']}",
                f"💧 Humidity: {weather['humidity']}%",
                f"🌧️  Precipitation: {weather['precipitation']} mm",
                f"💨 Wind: {weather['wind_speed']} km/h {wind_dir}",
                f"🔽 Pressure: {weather['pressure']} hPa",
                "",
                f"📍 Coordinates: {location['latitude']:.2f}°, {location['longitude']:.2f}°",
                f"🕐 Timezone: {weather['timezone']}",
                f"📅 Updated: {weather['time']}",
            ]

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"GetCurrentWeatherTool error: {e}")
            return ToolResult(f"Failed to get weather for '{city}': {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: get_weather_forecast
# ---------------------------------------------------------------------------

class GetWeatherForecastTool(WeatherTool):
    name = "get_weather_forecast"
    description = (
        "Get weather forecast for a city for multiple days. "
        "Returns daily high/low temperatures, precipitation probability, and conditions. "
        "Supports up to 16 days forecast."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "city": {
                "type": "string",
                "description": "City name (e.g., 'Moscow', 'New York', 'Tokyo')",
            },
            "days": {
                "type": "integer",
                "description": "Number of forecast days (1-16, default: 7)",
            },
        },
        "required": ["city"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        city = arguments.get("city", "").strip()
        if not city:
            return ToolResult("Missing required parameter: city", is_error=True)

        days = arguments.get("days", 7)
        days = max(1, min(16, int(days)))

        try:
            # Geocode city to coordinates
            location = await self.client.geocode(city)
            if not location:
                return ToolResult(
                    f"City not found: '{city}'. Try using English city name.",
                    is_error=True,
                )

            # Get forecast
            forecast = await self.client.get_forecast(
                location["latitude"], location["longitude"], days
            )

            # Format location name
            location_name = location["name"]
            if location.get("admin1"):
                location_name += f", {location['admin1']}"
            location_name += f", {location['country']}"

            lines = [
                f"{days}-Day Weather Forecast for {location_name}",
                "=" * 50,
                "",
            ]

            for day in forecast["days"]:
                precip_prob = day.get("precipitation_probability")
                precip_str = f"{precip_prob}%" if precip_prob is not None else "N/A"

                lines.append(f"📅 {day['date']}")
                lines.append(f"   {day['weather_description']}")
                lines.append(f"   🌡️  {day['temp_min']}°C — {day['temp_max']}°C")
                lines.append(f"   🌧️  Precipitation: {day['precipitation']} mm ({precip_str} chance)")
                lines.append(f"   💨 Max wind: {day['wind_speed_max']} km/h")
                lines.append("")

            lines.append(f"📍 Coordinates: {location['latitude']:.2f}°, {location['longitude']:.2f}°")
            lines.append(f"🕐 Timezone: {forecast['timezone']}")

            return ToolResult("\n".join(lines))

        except Exception as e:
            logger.error(f"GetWeatherForecastTool error: {e}")
            return ToolResult(
                f"Failed to get forecast for '{city}': {e}", is_error=True
            )


# ---------------------------------------------------------------------------
# Registry
# ---------------------------------------------------------------------------

def get_all_tools(client: WeatherClient) -> list[WeatherTool]:
    """Return all registered Weather MCP tools."""
    return [
        GetCurrentWeatherTool(client),
        GetWeatherForecastTool(client),
    ]
