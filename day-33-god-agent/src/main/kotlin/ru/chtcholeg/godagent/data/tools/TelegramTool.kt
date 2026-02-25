package ru.chtcholeg.godagent.data.tools

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class TelegramSendTool(
    private val httpClient: HttpClient,
    private val botTokenProvider: () -> String,
    private val chatIdProvider: () -> String
) : AgentTool {
    override val name = "telegram_send"
    override val description = "Send a message to the configured Telegram chat"
    override val parametersDescription = """{"text": string}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val botToken = botTokenProvider()
        val chatId = chatIdProvider()
        if (botToken.isBlank()) return@withContext "Error: Telegram bot token not configured in Settings"
        if (chatId.isBlank()) return@withContext "Error: Telegram chat ID not configured in Settings"

        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            return@withContext "Error: invalid args"
        }
        val text = args["text"]?.jsonPrimitive?.contentOrNull ?: return@withContext "Error: text required"

        try {
            val response: String = httpClient.post("https://api.telegram.org/bot$botToken/sendMessage") {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("chat_id", chatId)
                            append("text", text)
                            append("parse_mode", "Markdown")
                        }
                    )
                )
            }.body()
            val json = Json.parseToJsonElement(response).jsonObject
            if (json["ok"]?.jsonPrimitive?.booleanOrNull == true) "Message sent successfully"
            else "Error: ${json["description"]?.jsonPrimitive?.content}"
        } catch (e: Exception) {
            "Error sending Telegram message: ${e.message}"
        }
    }
}
