"""Time service client for getting time in different timezones.

Uses Python's built-in datetime and zoneinfo modules.
For city-to-timezone mapping, uses timezonefinder with geocoding.
"""

import logging
from datetime import datetime
from zoneinfo import ZoneInfo, available_timezones
from typing import Optional
import httpx

logger = logging.getLogger(__name__)

GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"


class TimeClient:
    """Client for time operations with timezone support."""

    def __init__(self):
        self.http_client = httpx.AsyncClient(timeout=30.0)
        self._available_timezones = available_timezones()

    def get_current_utc(self) -> dict:
        """Get current UTC time.

        Returns:
            dict with formatted time information
        """
        now = datetime.now(ZoneInfo("UTC"))
        return {
            "datetime": now.isoformat(),
            "timezone": "UTC",
            "timestamp": int(now.timestamp()),
            "year": now.year,
            "month": now.month,
            "day": now.day,
            "hour": now.hour,
            "minute": now.minute,
            "second": now.second,
            "weekday": now.strftime("%A"),
            "formatted": now.strftime("%Y-%m-%d %H:%M:%S %Z"),
        }

    def get_time_in_timezone(self, timezone: str) -> Optional[dict]:
        """Get current time in specified timezone.

        Args:
            timezone: IANA timezone name (e.g., "America/New_York", "Europe/Moscow")

        Returns:
            dict with time information or None if timezone is invalid
        """
        try:
            tz = ZoneInfo(timezone)
            now = datetime.now(tz)

            return {
                "datetime": now.isoformat(),
                "timezone": timezone,
                "timestamp": int(now.timestamp()),
                "year": now.year,
                "month": now.month,
                "day": now.day,
                "hour": now.hour,
                "minute": now.minute,
                "second": now.second,
                "weekday": now.strftime("%A"),
                "formatted": now.strftime("%Y-%m-%d %H:%M:%S %Z"),
                "utc_offset": now.strftime("%z"),
            }
        except Exception as e:
            logger.error(f"Invalid timezone '{timezone}': {e}")
            return None

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
                "admin1": result.get("admin1", ""),
                "timezone": result.get("timezone", "UTC"),
            }
        except Exception as e:
            logger.error(f"Geocoding error for '{city}': {e}")
            return None

    async def get_time_in_city(self, city: str) -> Optional[dict]:
        """Get current time for a city using geocoding.

        Args:
            city: City name (e.g., "Moscow", "New York", "Tokyo")

        Returns:
            dict with time and location information or None if city not found
        """
        location = await self.geocode(city)
        if not location:
            return None

        timezone = location["timezone"]
        time_info = self.get_time_in_timezone(timezone)

        if not time_info:
            return None

        # Add location information
        time_info["city"] = location["name"]
        time_info["country"] = location["country"]
        if location.get("admin1"):
            time_info["region"] = location["admin1"]
        time_info["coordinates"] = {
            "latitude": location["latitude"],
            "longitude": location["longitude"],
        }

        return time_info

    def list_timezones(self, filter_prefix: str = None) -> list[str]:
        """List available IANA timezones.

        Args:
            filter_prefix: Optional prefix to filter timezones (e.g., "America/", "Europe/")

        Returns:
            Sorted list of timezone names
        """
        if filter_prefix:
            filtered = [tz for tz in self._available_timezones if tz.startswith(filter_prefix)]
            return sorted(filtered)
        return sorted(self._available_timezones)

    async def close(self):
        """Close HTTP client."""
        await self.http_client.aclose()
