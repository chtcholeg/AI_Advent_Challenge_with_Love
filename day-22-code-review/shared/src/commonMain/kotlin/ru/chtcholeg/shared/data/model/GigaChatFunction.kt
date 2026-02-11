package ru.chtcholeg.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents a function that can be called by the AI (GigaChat function calling).
 * Compatible with OpenAI function calling format.
 */
@Serializable
data class GigaChatFunction(
    val name: String,
    val description: String,
    val parameters: JsonElement,  // JSON Schema for function parameters
    @SerialName("few_shot_examples")
    val fewShotExamples: List<FewShotExample>? = null,
    @SerialName("negative_few_shot_examples")
    val negativeFewShotExamples: List<NegativeFewShotExample>? = null
)

/**
 * Few-shot example for GigaChat function calling.
 * Shows the model how to use the function with specific user requests.
 */
@Serializable
data class FewShotExample(
    val request: String,  // Example user query that should trigger this function
    val params: JsonObject  // Expected function parameters for this query
)

/**
 * Negative few-shot example for GigaChat function calling.
 * Shows the model when NOT to use the function - helps avoid incorrect tool selection.
 */
@Serializable
data class NegativeFewShotExample(
    val request: String,  // Example user query that should NOT trigger this function
    val reason: String    // Explanation why this function is wrong for this request
)

/**
 * Represents a function call requested by the AI.
 */
@Serializable
data class FunctionCall(
    val name: String,
    val arguments: JsonElement  // Can be JsonObject or JsonPrimitive (string)
) {
    /**
     * Get arguments as a JSON string.
     * Handles both object format and string format from the API.
     */
    fun getArgumentsAsString(): String {
        return when {
            arguments is JsonPrimitive && arguments.isString -> {
                // Already a string, extract content
                arguments.content
            }
            else -> {
                // JSON object, convert to string
                arguments.toString()
            }
        }
    }
}
