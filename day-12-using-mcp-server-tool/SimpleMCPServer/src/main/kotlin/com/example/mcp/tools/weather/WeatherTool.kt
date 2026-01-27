package com.example.mcp.tools.weather

import com.example.mcp.plugins.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * MCP Tool for getting weather data from Open-Meteo API
 */
class WeatherTool(
    private val client: OpenMeteoClient = OpenMeteoClient()
) : McpTool {

    private val logger = LoggerFactory.getLogger(WeatherTool::class.java)

    override val name: String = "weather_forecast"

    override val description: String = """
        Получить информацию о погоде: текущую погоду, прогноз до 16 дней или исторические данные.
        Можно указать город по имени или координаты (широта/долгота).
    """.trimIndent()

    override val fewShotExamples: List<FewShotExample> = listOf(
        FewShotExample(
            request = "Какая сейчас погода в Москве?",
            params = buildJsonObject {
                put("city", JsonPrimitive("Moscow"))
                put("type", JsonPrimitive("current"))
            }
        ),
        FewShotExample(
            request = "Прогноз погоды на 5 дней для Санкт-Петербурга",
            params = buildJsonObject {
                put("city", JsonPrimitive("Saint Petersburg"))
                put("type", JsonPrimitive("forecast"))
                put("forecast_days", JsonPrimitive(5))
            }
        ),
        FewShotExample(
            request = "Погода в Нью-Йорке на неделю",
            params = buildJsonObject {
                put("city", JsonPrimitive("New York"))
                put("type", JsonPrimitive("forecast"))
                put("forecast_days", JsonPrimitive(7))
            }
        ),
        FewShotExample(
            request = "Какая была погода в Токио с 1 по 7 января 2025?",
            params = buildJsonObject {
                put("city", JsonPrimitive("Tokyo"))
                put("type", JsonPrimitive("historical"))
                put("start_date", JsonPrimitive("2025-01-01"))
                put("end_date", JsonPrimitive("2025-01-07"))
            }
        ),
        FewShotExample(
            request = "Покажи всю информацию о погоде в Берлине",
            params = buildJsonObject {
                put("city", JsonPrimitive("Berlin"))
                put("type", JsonPrimitive("all"))
                put("forecast_days", JsonPrimitive(7))
            }
        )
    )

    override val inputSchema: JsonSchema = JsonSchema(
        type = "object",
        properties = mapOf(
            "city" to PropertySchema(
                type = "string",
                description = "City name for geocoding (e.g., 'Moscow', 'New York', 'Tokyo')"
            ),
            "latitude" to PropertySchema(
                type = "number",
                description = "Latitude coordinate (-90 to 90)"
            ),
            "longitude" to PropertySchema(
                type = "number",
                description = "Longitude coordinate (-180 to 180)"
            ),
            "type" to PropertySchema(
                type = "string",
                description = "Type of weather data to retrieve",
                enum = listOf("current", "forecast", "historical", "all"),
                default = JsonPrimitive("current")
            ),
            "forecast_days" to PropertySchema(
                type = "integer",
                description = "Number of forecast days (1-16, default: 7)"
            ),
            "start_date" to PropertySchema(
                type = "string",
                description = "Start date for historical data (YYYY-MM-DD format)"
            ),
            "end_date" to PropertySchema(
                type = "string",
                description = "End date for historical data (YYYY-MM-DD format)"
            )
        ),
        description = "Either 'city' or both 'latitude' and 'longitude' must be provided"
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        logger.info("Executing weather tool with arguments: $arguments")

        // Extract parameters
        val city = arguments["city"]?.jsonPrimitive?.contentOrNull
        val latitude = arguments["latitude"]?.jsonPrimitive?.doubleOrNull
        val longitude = arguments["longitude"]?.jsonPrimitive?.doubleOrNull
        val type = arguments["type"]?.jsonPrimitive?.contentOrNull ?: "current"
        val forecastDays = arguments["forecast_days"]?.jsonPrimitive?.intOrNull ?: 7
        val startDate = arguments["start_date"]?.jsonPrimitive?.contentOrNull
        val endDate = arguments["end_date"]?.jsonPrimitive?.contentOrNull

        // Resolve coordinates
        val (lat, lon, locationName) = when {
            city != null -> {
                val geocoded = client.geocode(city)
                    ?: return ToolResult.Error("Could not find location: $city")
                Triple(
                    geocoded.latitude,
                    geocoded.longitude,
                    "${geocoded.name}, ${geocoded.country ?: ""}"
                )
            }
            latitude != null && longitude != null -> {
                Triple(latitude, longitude, "($latitude, $longitude)")
            }
            else -> {
                return ToolResult.Error("Either 'city' or both 'latitude' and 'longitude' must be provided")
            }
        }

        // Build result based on type
        return try {
            val resultText = StringBuilder()
            resultText.appendLine("Weather for $locationName")
            resultText.appendLine("=" .repeat(40))

            when (type) {
                "current" -> {
                    appendCurrentWeather(resultText, lat, lon)
                }
                "forecast" -> {
                    appendForecast(resultText, lat, lon, forecastDays)
                }
                "historical" -> {
                    if (startDate == null || endDate == null) {
                        return ToolResult.Error("'start_date' and 'end_date' are required for historical data")
                    }
                    appendHistorical(resultText, lat, lon, startDate, endDate)
                }
                "all" -> {
                    appendCurrentWeather(resultText, lat, lon)
                    resultText.appendLine()
                    appendForecast(resultText, lat, lon, forecastDays)
                    if (startDate != null && endDate != null) {
                        resultText.appendLine()
                        appendHistorical(resultText, lat, lon, startDate, endDate)
                    }
                }
                else -> {
                    return ToolResult.Error("Unknown type: $type. Use 'current', 'forecast', 'historical', or 'all'")
                }
            }

            ToolResult.Success(listOf(ToolContent.Text(resultText.toString())))
        } catch (e: Exception) {
            logger.error("Weather tool error: ${e.message}", e)
            ToolResult.Error("Failed to get weather data: ${e.message}")
        }
    }

    private suspend fun appendCurrentWeather(sb: StringBuilder, lat: Double, lon: Double) {
        val weather = client.getCurrentWeather(lat, lon)
        if (weather?.current == null) {
            sb.appendLine("Current weather: unavailable")
            return
        }

        val current = weather.current
        val units = weather.current_units

        sb.appendLine("Current Weather (${current.time}):")
        sb.appendLine("-".repeat(30))
        current.temperature_2m?.let {
            sb.appendLine("  Temperature: $it${units?.temperature_2m ?: "°C"}")
        }
        current.apparent_temperature?.let {
            sb.appendLine("  Feels like: $it${units?.temperature_2m ?: "°C"}")
        }
        current.relative_humidity_2m?.let {
            sb.appendLine("  Humidity: $it${units?.relative_humidity_2m ?: "%"}")
        }
        current.precipitation?.let {
            sb.appendLine("  Precipitation: $it${units?.precipitation ?: "mm"}")
        }
        current.wind_speed_10m?.let {
            val direction = current.wind_direction_10m?.let { d -> " (${windDirectionToString(d)})" } ?: ""
            sb.appendLine("  Wind: $it${units?.wind_speed_10m ?: "km/h"}$direction")
        }
        current.weather_code?.let {
            sb.appendLine("  Conditions: ${client.getWeatherDescription(it)}")
        }
    }

    private suspend fun appendForecast(sb: StringBuilder, lat: Double, lon: Double, days: Int) {
        val forecast = client.getForecast(lat, lon, days)
        if (forecast?.daily == null) {
            sb.appendLine("Forecast: unavailable")
            return
        }

        val daily = forecast.daily
        val units = forecast.daily_units

        sb.appendLine("${days}-Day Forecast:")
        sb.appendLine("-".repeat(30))

        for (i in daily.time.indices) {
            val date = daily.time[i]
            val maxTemp = daily.temperature_2m_max?.getOrNull(i)
            val minTemp = daily.temperature_2m_min?.getOrNull(i)
            val precip = daily.precipitation_sum?.getOrNull(i)
            val code = daily.weather_code?.getOrNull(i)
            val wind = daily.wind_speed_10m_max?.getOrNull(i)

            sb.appendLine("  $date:")
            if (maxTemp != null && minTemp != null) {
                sb.appendLine("    Temp: $minTemp - $maxTemp${units?.temperature_2m_max ?: "°C"}")
            }
            precip?.let { sb.appendLine("    Precipitation: $it${units?.precipitation_sum ?: "mm"}") }
            wind?.let { sb.appendLine("    Max wind: $it${units?.wind_speed_10m_max ?: "km/h"}") }
            code?.let { sb.appendLine("    Conditions: ${client.getWeatherDescription(it)}") }
        }
    }

    private suspend fun appendHistorical(
        sb: StringBuilder,
        lat: Double,
        lon: Double,
        startDate: String,
        endDate: String
    ) {
        val historical = client.getHistoricalWeather(lat, lon, startDate, endDate)
        if (historical?.daily == null) {
            sb.appendLine("Historical data: unavailable")
            return
        }

        val daily = historical.daily
        val units = historical.daily_units

        sb.appendLine("Historical Weather ($startDate to $endDate):")
        sb.appendLine("-".repeat(30))

        for (i in daily.time.indices) {
            val date = daily.time[i]
            val maxTemp = daily.temperature_2m_max?.getOrNull(i)
            val minTemp = daily.temperature_2m_min?.getOrNull(i)
            val precip = daily.precipitation_sum?.getOrNull(i)
            val code = daily.weather_code?.getOrNull(i)

            sb.appendLine("  $date:")
            if (maxTemp != null && minTemp != null) {
                sb.appendLine("    Temp: $minTemp - $maxTemp${units?.temperature_2m_max ?: "°C"}")
            }
            precip?.let { sb.appendLine("    Precipitation: $it${units?.precipitation_sum ?: "mm"}") }
            code?.let { sb.appendLine("    Conditions: ${client.getWeatherDescription(it)}") }
        }
    }

    private fun windDirectionToString(degrees: Int): String {
        return when ((degrees + 22) / 45 % 8) {
            0 -> "N"
            1 -> "NE"
            2 -> "E"
            3 -> "SE"
            4 -> "S"
            5 -> "SW"
            6 -> "W"
            7 -> "NW"
            else -> "N"
        }
    }
}

/**
 * Built-in Weather Plugin that provides the weather tool
 */
class WeatherPlugin : McpPlugin {
    private val client = OpenMeteoClient()

    override val name: String = "weather"
    override val version: String = "1.0.0"
    override val description: String = "Weather data from Open-Meteo API"

    override fun getTools(): List<McpTool> = listOf(WeatherTool(client))

    override fun shutdown() {
        client.close()
    }
}
