"""Weather API client using Open-Meteo (free, no API key required).

Open-Meteo provides:
- Current weather data
- Weather forecasts up to 16 days
- Geocoding for city name to coordinates

API docs: https://open-meteo.com/en/docs
"""

import logging
from typing import Optional
import httpx

logger = logging.getLogger(__name__)

GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
WEATHER_URL = "https://api.open-meteo.com/v1/forecast"

# WMO Weather interpretation codes
WMO_CODES = {
    0: "Clear sky",
    1: "Mainly clear",
    2: "Partly cloudy",
    3: "Overcast",
    45: "Fog",
    48: "Depositing rime fog",
    51: "Light drizzle",
    53: "Moderate drizzle",
    55: "Dense drizzle",
    56: "Light freezing drizzle",
    57: "Dense freezing drizzle",
    61: "Slight rain",
    63: "Moderate rain",
    65: "Heavy rain",
    66: "Light freezing rain",
    67: "Heavy freezing rain",
    71: "Slight snow fall",
    73: "Moderate snow fall",
    75: "Heavy snow fall",
    77: "Snow grains",
    80: "Slight rain showers",
    81: "Moderate rain showers",
    82: "Violent rain showers",
    85: "Slight snow showers",
    86: "Heavy snow showers",
    95: "Thunderstorm",
    96: "Thunderstorm with slight hail",
    99: "Thunderstorm with heavy hail",
}


class WeatherClient:
    """Client for Open-Meteo weather API."""

    def __init__(self):
        self.http_client = httpx.AsyncClient(timeout=30.0)

    async def geocode(self, city: str) -> Optional[dict]:
        """Convert city name to coordinates.

        Returns:
            dict with keys: name, latitude, longitude, country, timezone
            None if city not found
        """
        try:
            response = await self.http_client.get(
                GEOCODING_URL,
                params={"name": city, "count": 1, "language": "en", "format": "json"},
            )
            response.raise_for_status()
            data = response.json()

            if not data.get("results"):
                return None

            result = data["results"][0]
            return {
                "name": result.get("name", city),
                "latitude": result["latitude"],
                "longitude": result["longitude"],
                "country": result.get("country", "Unknown"),
                "admin1": result.get("admin1", ""),  # State/region
                "timezone": result.get("timezone", "UTC"),
            }
        except Exception as e:
            logger.error(f"Geocoding error for '{city}': {e}")
            return None

    async def get_current_weather(self, latitude: float, longitude: float) -> dict:
        """Get current weather for coordinates.

        Returns:
            dict with current weather data
        """
        response = await self.http_client.get(
            WEATHER_URL,
            params={
                "latitude": latitude,
                "longitude": longitude,
                "current": "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m,pressure_msl",
                "timezone": "auto",
            },
        )
        response.raise_for_status()
        data = response.json()

        current = data.get("current", {})
        weather_code = current.get("weather_code", 0)

        return {
            "temperature": current.get("temperature_2m"),
            "feels_like": current.get("apparent_temperature"),
            "humidity": current.get("relative_humidity_2m"),
            "precipitation": current.get("precipitation"),
            "pressure": current.get("pressure_msl"),
            "wind_speed": current.get("wind_speed_10m"),
            "wind_direction": current.get("wind_direction_10m"),
            "weather_code": weather_code,
            "weather_description": WMO_CODES.get(weather_code, "Unknown"),
            "timezone": data.get("timezone", "UTC"),
            "time": current.get("time"),
        }

    async def get_forecast(
        self, latitude: float, longitude: float, days: int = 7
    ) -> dict:
        """Get weather forecast for coordinates.

        Args:
            latitude: Location latitude
            longitude: Location longitude
            days: Number of forecast days (1-16)

        Returns:
            dict with daily forecast data
        """
        days = max(1, min(16, days))

        response = await self.http_client.get(
            WEATHER_URL,
            params={
                "latitude": latitude,
                "longitude": longitude,
                "daily": "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,precipitation_sum,precipitation_probability_max,wind_speed_10m_max",
                "timezone": "auto",
                "forecast_days": days,
            },
        )
        response.raise_for_status()
        data = response.json()

        daily = data.get("daily", {})
        dates = daily.get("time", [])

        forecast = []
        for i, date in enumerate(dates):
            weather_code = daily.get("weather_code", [0])[i] if daily.get("weather_code") else 0
            forecast.append({
                "date": date,
                "temp_max": daily.get("temperature_2m_max", [None])[i],
                "temp_min": daily.get("temperature_2m_min", [None])[i],
                "feels_like_max": daily.get("apparent_temperature_max", [None])[i],
                "feels_like_min": daily.get("apparent_temperature_min", [None])[i],
                "precipitation": daily.get("precipitation_sum", [None])[i],
                "precipitation_probability": daily.get("precipitation_probability_max", [None])[i],
                "wind_speed_max": daily.get("wind_speed_10m_max", [None])[i],
                "weather_code": weather_code,
                "weather_description": WMO_CODES.get(weather_code, "Unknown"),
            })

        return {
            "timezone": data.get("timezone", "UTC"),
            "days": forecast,
        }

    async def close(self):
        """Close HTTP client."""
        await self.http_client.aclose()
