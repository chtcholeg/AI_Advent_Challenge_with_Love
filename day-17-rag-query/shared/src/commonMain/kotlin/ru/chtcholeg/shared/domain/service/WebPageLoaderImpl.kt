package ru.chtcholeg.shared.domain.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Implementation of WebPageLoader using Ktor HTTP client
 */
class WebPageLoaderImpl(
    private val httpClient: HttpClient
) : WebPageLoader {

    override suspend fun loadWebPage(url: String): DocumentContent {
        try {
            // Validate URL
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw WebPageLoadException("Invalid URL: $url. URL must start with http:// or https://")
            }

            // Fetch web page
            val response: HttpResponse = httpClient.get(url)

            // Check response status
            if (!response.status.isSuccess()) {
                throw WebPageLoadException("Failed to load URL: $url. Status: ${response.status}")
            }

            // Get content type
            val contentType = response.contentType()?.contentType
            if (contentType != "text" && contentType != "application") {
                throw WebPageLoadException("Unsupported content type: $contentType for URL: $url")
            }

            // Read HTML content
            val htmlContent = response.bodyAsText()

            // Extract plain text from HTML
            val plainText = extractTextFromHtml(htmlContent)

            // Extract page title from URL
            val pageTitle = extractPageTitle(url)

            return DocumentContent(
                filePath = url,
                content = plainText,
                fileName = pageTitle,
                fileSize = htmlContent.length.toLong(),
                lastModified = System.currentTimeMillis()
            )
        } catch (e: WebPageLoadException) {
            throw e
        } catch (e: Exception) {
            throw WebPageLoadException("Error loading URL: $url", e)
        }
    }

    override suspend fun loadWebPages(urls: List<String>): List<DocumentContent> {
        return urls.mapNotNull { url ->
            try {
                loadWebPage(url)
            } catch (e: Exception) {
                println("Warning: Failed to load $url: ${e.message}")
                null
            }
        }
    }

    /**
     * Extract plain text from HTML by removing tags and decoding entities
     */
    private fun extractTextFromHtml(html: String): String {
        var text = html

        // Remove script and style tags with their content
        text = text.replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")

        // Remove HTML comments
        text = text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

        // Replace block-level tags with newlines
        val blockTags = listOf("p", "div", "br", "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr")
        for (tag in blockTags) {
            text = text.replace(Regex("</?$tag[^>]*>", RegexOption.IGNORE_CASE), "\n")
        }

        // Remove all remaining HTML tags
        text = text.replace(Regex("<[^>]+>"), "")

        // Decode common HTML entities
        text = decodeHtmlEntities(text)

        // Clean up whitespace
        text = text.replace(Regex("&nbsp;"), " ")
        text = text.replace(Regex("\\s+"), " ")
        text = text.replace(Regex("\\n\\s*\\n+"), "\n\n")
        text = text.trim()

        return text
    }

    /**
     * Decode common HTML entities
     */
    private fun decodeHtmlEntities(text: String): String {
        val entities = mapOf(
            "&amp;" to "&",
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&apos;" to "'",
            "&#39;" to "'",
            "&nbsp;" to " ",
            "&ndash;" to "–",
            "&mdash;" to "—",
            "&hellip;" to "…"
        )

        var result = text
        for ((entity, replacement) in entities) {
            result = result.replace(entity, replacement)
        }

        // Decode numeric entities (&#123; or &#x7B;)
        result = result.replace(Regex("&#(\\d+);")) { match ->
            val code = match.groupValues[1].toIntOrNull()
            code?.toChar()?.toString() ?: match.value
        }
        result = result.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            code?.toChar()?.toString() ?: match.value
        }

        return result
    }

    /**
     * Extract page title from URL (last path segment)
     */
    private fun extractPageTitle(url: String): String {
        return try {
            val path = url.substringAfter("://").substringAfter("/")
            if (path.isEmpty()) {
                url.substringAfter("://").substringBefore("/")
            } else {
                path.split("/").lastOrNull { it.isNotEmpty() }
                    ?: url.substringAfter("://").substringBefore("/")
            }
        } catch (e: Exception) {
            url
        }
    }
}
