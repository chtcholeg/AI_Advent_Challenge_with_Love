package ru.chtcholeg.agent.domain.tool

import kotlinx.serialization.json.*

/**
 * DSL builder for JSON Schema tool input definitions.
 *
 * Usage:
 * ```kotlin
 * val schema = toolSchema {
 *     string("file_path", "Absolute path to the file", required = true)
 *     integer("offset", "Line offset", default = 0)
 *     boolean("verbose", "Enable verbose output", default = false)
 *     string("mode", "Output mode", default = "content", enum = listOf("content", "files", "count"))
 *     array("items", "List of item IDs", itemType = "string")
 *     obj("metadata", "Key-value metadata", additionalProperties = true)
 * }
 * ```
 */
class ToolSchemaBuilder {
    private val properties = mutableMapOf<String, JsonObject>()
    private val requiredFields = mutableListOf<String>()

    fun string(
        name: String,
        description: String,
        required: Boolean = false,
        default: String? = null,
        enum: List<String>? = null
    ) {
        properties[name] = buildJsonObject {
            put("type", "string")
            put("description", description)
            default?.let { put("default", it) }
            enum?.let { values ->
                putJsonArray("enum") { values.forEach { add(it) } }
            }
        }
        if (required) requiredFields.add(name)
    }

    fun integer(
        name: String,
        description: String,
        required: Boolean = false,
        default: Int? = null,
        minimum: Int? = null,
        maximum: Int? = null
    ) {
        properties[name] = buildJsonObject {
            put("type", "integer")
            put("description", description)
            default?.let { put("default", it) }
            minimum?.let { put("minimum", it) }
            maximum?.let { put("maximum", it) }
        }
        if (required) requiredFields.add(name)
    }

    fun number(
        name: String,
        description: String,
        required: Boolean = false,
        default: Double? = null
    ) {
        properties[name] = buildJsonObject {
            put("type", "number")
            put("description", description)
            default?.let { put("default", it) }
        }
        if (required) requiredFields.add(name)
    }

    fun long(
        name: String,
        description: String,
        required: Boolean = false,
        default: Long? = null
    ) {
        properties[name] = buildJsonObject {
            put("type", "integer")
            put("description", description)
            default?.let { put("default", it) }
        }
        if (required) requiredFields.add(name)
    }

    fun boolean(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null
    ) {
        properties[name] = buildJsonObject {
            put("type", "boolean")
            put("description", description)
            default?.let { put("default", it) }
        }
        if (required) requiredFields.add(name)
    }

    fun obj(
        name: String,
        description: String,
        required: Boolean = false,
        additionalProperties: Boolean = false,
        block: ToolSchemaBuilder.() -> Unit = {}
    ) {
        val nested = ToolSchemaBuilder().apply(block)
        properties[name] = buildJsonObject {
            put("type", "object")
            put("description", description)
            putJsonObject("properties") {
                nested.properties.forEach { (key, value) -> put(key, value) }
            }
            if (additionalProperties) put("additionalProperties", true)
            if (nested.requiredFields.isNotEmpty()) {
                putJsonArray("required") { nested.requiredFields.forEach { add(it) } }
            }
        }
        if (required) requiredFields.add(name)
    }

    fun array(
        name: String,
        description: String,
        required: Boolean = false,
        itemType: String = "string"
    ) {
        properties[name] = buildJsonObject {
            put("type", "array")
            put("description", description)
            putJsonObject("items") { put("type", itemType) }
        }
        if (required) requiredFields.add(name)
    }

    /**
     * Escape hatch: add a raw JsonObject property for complex/nested schemas
     * that don't fit the DSL.
     */
    fun raw(name: String, schema: JsonObject, required: Boolean = false) {
        properties[name] = schema
        if (required) requiredFields.add(name)
    }

    fun build(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            properties.forEach { (key, value) -> put(key, value) }
        }
        if (requiredFields.isNotEmpty()) {
            putJsonArray("required") { requiredFields.forEach { add(it) } }
        }
    }
}

/**
 * Build a JSON Schema for a tool's input parameters using a type-safe DSL.
 */
fun toolSchema(block: ToolSchemaBuilder.() -> Unit): JsonObject {
    return ToolSchemaBuilder().apply(block).build()
}
