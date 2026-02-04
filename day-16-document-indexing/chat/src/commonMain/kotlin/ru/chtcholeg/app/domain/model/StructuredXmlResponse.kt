package ru.chtcholeg.app.domain.model

/**
 * Structured XML response format for AI answers
 * Mirrors StructuredResponse but parses XML format
 */
data class StructuredXmlResponse(
    val questionShort: String,
    val answer: String,
    val responderRole: String,
    val unicodeSymbols: String
) {
    companion object {
        /**
         * Try to parse an XML string into StructuredXmlResponse
         * Returns null if parsing fails
         */
        fun tryParse(xmlString: String): StructuredXmlResponse? {
            return try {
                // Remove markdown code blocks if present
                val cleanedXml = xmlString
                    .trim()
                    .removePrefix("```xml")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                // Simple regex-based XML parsing for our specific structure
                val questionShort = extractTagContent(cleanedXml, "question_short")
                val answer = extractTagContent(cleanedXml, "answer")
                val responderRole = extractTagContent(cleanedXml, "responder_role")
                val unicodeSymbols = extractTagContent(cleanedXml, "unicode_symbols")

                if (questionShort != null && answer != null && responderRole != null && unicodeSymbols != null) {
                    StructuredXmlResponse(
                        questionShort = questionShort,
                        answer = answer,
                        responderRole = responderRole,
                        unicodeSymbols = unicodeSymbols
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Extract content between XML tags
         */
        private fun extractTagContent(xml: String, tagName: String): String? {
            val openTag = "<$tagName>"
            val closeTag = "</$tagName>"

            val startIndex = xml.indexOf(openTag)
            if (startIndex == -1) return null

            val contentStart = startIndex + openTag.length
            val endIndex = xml.indexOf(closeTag, contentStart)
            if (endIndex == -1) return null

            return xml.substring(contentStart, endIndex).trim()
        }

        /**
         * Check if a string looks like it might be a structured XML response
         */
        fun looksLikeStructuredXmlResponse(text: String): Boolean {
            val trimmed = text.trim()
            return (trimmed.contains("<?xml") || trimmed.contains("<response>")) &&
                   trimmed.contains("<question_short>") &&
                   trimmed.contains("</response>")
        }
    }
}
