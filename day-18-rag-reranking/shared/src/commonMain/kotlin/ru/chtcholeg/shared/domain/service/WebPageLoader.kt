package ru.chtcholeg.shared.domain.service

/**
 * Service for loading and parsing web pages
 */
interface WebPageLoader {
    /**
     * Load a web page from URL and extract plain text
     * @param url URL of the web page
     * @return DocumentContent containing extracted text
     */
    suspend fun loadWebPage(url: String): DocumentContent

    /**
     * Load multiple web pages
     * @param urls List of URLs to load
     * @return List of DocumentContent objects
     */
    suspend fun loadWebPages(urls: List<String>): List<DocumentContent>
}

/**
 * Exception thrown when web page loading fails
 */
class WebPageLoadException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
