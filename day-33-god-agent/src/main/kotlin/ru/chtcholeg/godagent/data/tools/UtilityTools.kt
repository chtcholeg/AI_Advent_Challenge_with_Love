package ru.chtcholeg.godagent.data.tools

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class GetTimeTool : AgentTool {
    override val name = "get_time"
    override val description = "Get the current date and time"
    override val parametersDescription = "{}"

    override suspend fun execute(argsJson: String): String {
        val now = ZonedDateTime.now()
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))
    }
}

class GetWeatherTool(private val httpClient: HttpClient) : AgentTool {
    override val name = "get_weather"
    override val description = "Get current weather for a city"
    override val parametersDescription = """{"city": string}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            return@withContext "Error: invalid args"
        }
        val city = args["city"]?.jsonPrimitive?.contentOrNull ?: return@withContext "Error: city required"
        try {
            // Geocode city first using Open-Meteo geocoding API
            val geoResponse: String = httpClient.get(
                "https://geocoding-api.open-meteo.com/v1/search?name=${city}&count=1&language=en&format=json"
            ).body()
            val geoJson = Json.parseToJsonElement(geoResponse).jsonObject
            val results = geoJson["results"]?.jsonArray ?: return@withContext "City not found: $city"
            val location = results.first().jsonObject
            val lat = location["latitude"]?.jsonPrimitive?.double ?: return@withContext "Geocoding failed"
            val lon = location["longitude"]?.jsonPrimitive?.double ?: return@withContext "Geocoding failed"
            val name = location["name"]?.jsonPrimitive?.content ?: city

            val weatherResponse: String = httpClient.get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code&wind_speed_unit=kmh"
            ).body()
            val weatherJson = Json.parseToJsonElement(weatherResponse).jsonObject
            val current = weatherJson["current"]?.jsonObject ?: return@withContext "No weather data"
            val temp = current["temperature_2m"]?.jsonPrimitive?.double ?: 0.0
            val humidity = current["relative_humidity_2m"]?.jsonPrimitive?.int ?: 0
            val wind = current["wind_speed_10m"]?.jsonPrimitive?.double ?: 0.0
            "$name: ${temp}C, humidity ${humidity}%, wind ${wind} km/h"
        } catch (e: Exception) {
            "Error fetching weather: ${e.message}"
        }
    }
}

class GetCurrencyTool(private val httpClient: HttpClient) : AgentTool {
    override val name = "get_currency"
    override val description = "Get exchange rate between two currencies"
    override val parametersDescription = """{"from": string (e.g. USD), "to": string (e.g. RUB)}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            return@withContext "Error: invalid args"
        }
        val from = args["from"]?.jsonPrimitive?.contentOrNull?.uppercase()
            ?: return@withContext "Error: from currency required"
        val to = args["to"]?.jsonPrimitive?.contentOrNull?.uppercase()
            ?: return@withContext "Error: to currency required"
        try {
            val response: String = httpClient.get("https://api.frankfurter.app/latest?from=$from&to=$to").body()
            val json = Json.parseToJsonElement(response).jsonObject
            val rate = json["rates"]?.jsonObject?.get(to)?.jsonPrimitive?.double
                ?: return@withContext "Currency pair not found: $from/$to"
            "1 $from = $rate $to"
        } catch (e: Exception) {
            "Error fetching exchange rate: ${e.message}"
        }
    }
}
