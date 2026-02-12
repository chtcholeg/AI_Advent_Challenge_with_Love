package ru.chtcholeg.agent.domain.service

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.config.AgentConfig
import ru.chtcholeg.agent.domain.model.AgentMessage
import ru.chtcholeg.agent.domain.model.MessageType
import ru.chtcholeg.shared.data.model.Message

/**
 * Handles detection and sanitization of base64 image data in tool results.
 * Prevents sending large image payloads to the AI model which causes context overflow.
 */
class ImageProcessor(private val json: Json = Json { ignoreUnknownKeys = true }) {

    companion object {
        private val BASE64_FIELD_NAMES = listOf("base64", "image", "image_data", "data", "screenshot")
        private const val BASE64_SAMPLE_SIZE = 200
        private const val BASE64_MIN_SAMPLE_SIZE = 100
        private const val BASE64_VALIDITY_THRESHOLD = 0.95f
    }

    /**
     * Checks if content contains base64 image data and returns a placeholder if so.
     * Returns null if no image data detected.
     */
    fun createPlaceholderForImageContent(content: String): String? {
        if (content.length < AgentConfig.MIN_BASE64_LENGTH) return null

        return try {
            val jsonElement = json.parseToJsonElement(content).jsonObject

            for (fieldName in BASE64_FIELD_NAMES) {
                val field = jsonElement[fieldName]?.jsonPrimitive?.contentOrNull
                if (field != null && field.length > AgentConfig.MIN_BASE64_LENGTH && looksLikeBase64(field)) {
                    val otherFields = jsonElement.filterKeys { it != fieldName }
                        .map { (key, value) -> "$key: ${value.toString().take(100)}" }
                        .joinToString(", ")

                    val compressionRatio = field.length / content.length

                    return "[Image data received (compression: ${compressionRatio}x). ${if (otherFields.isNotEmpty()) "Other fields: $otherFields" else "The image is displayed to the user."}]"
                }
            }
            null
        } catch (e: Exception) {
            if (content.length > 5000 && looksLikeBase64(content)) {
                "[Image data received and displayed to the user.]"
            } else {
                null
            }
        }
    }

    /**
     * Extract image from tool result if present.
     * Returns an AgentMessage with SCREENSHOT type and imageBase64 if found.
     */
    fun extractScreenshotFromResult(toolName: String, content: String): AgentMessage? {
        if (content.length < AgentConfig.MIN_BASE64_LENGTH) return null

        return try {
            val jsonElement = json.parseToJsonElement(content).jsonObject

            for (fieldName in BASE64_FIELD_NAMES) {
                val base64 = jsonElement[fieldName]?.jsonPrimitive?.contentOrNull
                if (base64 != null && base64.length > AgentConfig.MIN_BASE64_LENGTH && looksLikeBase64(base64)) {
                    val status = jsonElement["status"]?.jsonPrimitive?.contentOrNull
                    val deviceId = jsonElement["device_id"]?.jsonPrimitive?.contentOrNull

                    val description = when {
                        toolName == "screenshot" && deviceId != null -> "Screenshot captured from $deviceId"
                        status != null -> "Image from $toolName (status: $status)"
                        else -> "Image from $toolName"
                    }

                    return AgentMessage(
                        content = description,
                        type = MessageType.SCREENSHOT,
                        imageBase64 = base64
                    )
                }
            }
            null
        } catch (e: Exception) {
            if (content.length > 5000 && looksLikeBase64(content)) {
                AgentMessage(
                    content = "Image from $toolName",
                    type = MessageType.SCREENSHOT,
                    imageBase64 = content
                )
            } else {
                null
            }
        }
    }

    /**
     * Sanitize message content to remove large base64 image data.
     * Prevents context overflow when sending to the AI model.
     */
    fun sanitizeMessageForApi(message: Message): Message {
        val content = message.content ?: return message

        if (content.length > AgentConfig.MAX_MESSAGE_LENGTH) {
            return message.copy(content = buildJsonObject {
                put("result", "[Content truncated - data displayed to user]")
                put("is_error", false)
            }.toString())
        }

        val sanitizedContent = try {
            val jsonElement = json.parseToJsonElement(content).jsonObject

            var hasLargeField = false
            for (fieldName in BASE64_FIELD_NAMES) {
                val field = jsonElement[fieldName]?.jsonPrimitive?.contentOrNull
                if (field != null && field.length > AgentConfig.MIN_BASE64_LENGTH && looksLikeBase64(field)) {
                    hasLargeField = true
                    break
                }
            }

            if (!hasLargeField) {
                val resultField = jsonElement["result"]?.jsonPrimitive?.contentOrNull
                if (resultField != null && resultField.length > 2000) {
                    try {
                        val nestedJson = json.parseToJsonElement(resultField).jsonObject
                        for (fieldName in BASE64_FIELD_NAMES) {
                            val field = nestedJson[fieldName]?.jsonPrimitive?.contentOrNull
                            if (field != null && field.length > AgentConfig.MIN_BASE64_LENGTH && looksLikeBase64(field)) {
                                hasLargeField = true
                                break
                            }
                        }
                    } catch (e: Exception) {
                        if (resultField.length > 5000 && looksLikeBase64(resultField)) {
                            hasLargeField = true
                        }
                    }
                }
            }

            if (hasLargeField) {
                buildJsonObject {
                    put("result", "[Image data - displayed to user]")
                    jsonElement["is_error"]?.let { put("is_error", it) }
                }.toString()
            } else {
                content
            }
        } catch (e: Exception) {
            if (content.length > 5000 && looksLikeBase64(content)) {
                "[Image data - displayed to user]"
            } else {
                content
            }
        }

        return message.copy(content = sanitizedContent)
    }

    /**
     * Simple heuristic to check if a string looks like base64-encoded data.
     */
    fun looksLikeBase64(str: String): Boolean {
        if (str.length < BASE64_MIN_SAMPLE_SIZE) return false
        val sample = str.take(BASE64_SAMPLE_SIZE)
        val validChars = sample.count { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        return validChars.toFloat() / sample.length > BASE64_VALIDITY_THRESHOLD
    }
}
