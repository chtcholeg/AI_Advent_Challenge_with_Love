package ru.chtcholeg.shared.domain.service

/**
 * Cross-platform PDF parser interface
 * Desktop implementation uses Apache PDFBox
 */
expect class PdfParser() {
    /**
     * Extract text content from PDF file bytes
     * @param bytes PDF file content as byte array
     * @return Extracted text content
     */
    suspend fun extractText(bytes: ByteArray): String

    /**
     * Check if PDF parsing is supported on this platform
     */
    fun isSupported(): Boolean
}
