package com.example.mcp.tools.weather

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Geocoding response from Open-Meteo
 */
@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult>? = null
)

@Serializable
data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null
)

/**
 * Current weather response
 */
@Serializable
data class CurrentWeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: CurrentWeather? = null,
    val current_units: CurrentUnits? = null
)

@Serializable
data class CurrentWeather(
    val time: String,
    val temperature_2m: Double? = null,
    val relative_humidity_2m: Int? = null,
    val apparent_temperature: Double? = null,
    val precipitation: Double? = null,
    val weather_code: Int? = null,
    val wind_speed_10m: Double? = null,
    val wind_direction_10m: Int? = null
)

@Serializable
data class CurrentUnits(
    val temperature_2m: String? = null,
    val relative_humidity_2m: String? = null,
    val precipitation: String? = null,
    val wind_speed_10m: String? = null
)

/**
 * Forecast response
 */
@Serializable
data class ForecastResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val daily: DailyForecast? = null,
    val daily_units: DailyUnits? = null
)

@Serializable
data class DailyForecast(
    val time: List<String>,
    val temperature_2m_max: List<Double?>? = null,
    val temperature_2m_min: List<Double?>? = null,
    val precipitation_sum: List<Double?>? = null,
    val weather_code: List<Int?>? = null,
    val wind_speed_10m_max: List<Double?>? = null
)

@Serializable
data class DailyUnits(
    val temperature_2m_max: String? = null,
    val temperature_2m_min: String? = null,
    val precipitation_sum: String? = null,
    val wind_speed_10m_max: String? = null
)

/**
 * Historical weather response
 */
@Serializable
data class HistoricalResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val daily: DailyForecast? = null,
    val daily_units: DailyUnits? = null
)

/**
 * Client for Open-Meteo API
 */
class OpenMeteoClient {
    private val logger = LoggerFactory.getLogger(OpenMeteoClient::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    companion object {
        private const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
        private const val WEATHER_URL = "https://api.open-meteo.com/v1/forecast"
        private const val HISTORICAL_URL = "https://archive-api.open-meteo.com/v1/archive"
    }

    /**
     * Geocode a city name to coordinates
     */
    suspend fun geocode(city: String): GeocodingResult? {
        logger.debug("Geocoding city: $city")
        return try {
            val response: GeocodingResponse = httpClient.get(GEOCODING_URL) {
                parameter("name", city)
                parameter("count", 1)
                parameter("language", "en")
                parameter("format", "json")
            }.body()

            response.results?.firstOrNull()
        } catch (e: Exception) {
            logger.error("Geocoding failed for '$city': ${e.message}")
            null
        }
    }

    /**
     * Get current weather for coordinates
     */
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): CurrentWeatherResponse? {
        logger.debug("Getting current weather for ($latitude, $longitude)")
        return try {
            httpClient.get(WEATHER_URL) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("current", "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m")
                parameter("timezone", "auto")
            }.body()
        } catch (e: Exception) {
            logger.error("Failed to get current weather: ${e.message}")
            null
        }
    }

    /**
     * Get weather forecast
     */
    suspend fun getForecast(latitude: Double, longitude: Double, days: Int = 7): ForecastResponse? {
        logger.debug("Getting $days-day forecast for ($latitude, $longitude)")
        return try {
            httpClient.get(WEATHER_URL) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code,wind_speed_10m_max")
                parameter("timezone", "auto")
                parameter("forecast_days", days.coerceIn(1, 16))
            }.body()
        } catch (e: Exception) {
            logger.error("Failed to get forecast: ${e.message}")
            null
        }
    }

    /**
     * Get historical weather data
     */
    suspend fun getHistoricalWeather(
        latitude: Double,
        longitude: Double,
        startDate: String,
        endDate: String
    ): HistoricalResponse? {
        logger.debug("Getting historical weather for ($latitude, $longitude) from $startDate to $endDate")
        return try {
            httpClient.get(HISTORICAL_URL) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("start_date", startDate)
                parameter("end_date", endDate)
                parameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code,wind_speed_10m_max")
                parameter("timezone", "auto")
            }.body()
        } catch (e: Exception) {
            logger.error("Failed to get historical weather: ${e.message}")
            null
        }
    }

    /**
     * Get weather code description
     */
    fun getWeatherDescription(code: Int?): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Partly cloudy"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing drizzle"
            61, 63, 65 -> "Rain"
            66, 67 -> "Freezing rain"
            71, 73, 75 -> "Snowfall"
            77 -> "Snow grains"
            80, 81, 82 -> "Rain showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with hail"
            else -> "Unknown"
        }
    }

    fun close() {
        httpClient.close()
    }
}
