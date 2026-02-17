package ru.chtcholeg.shared.domain.service

actual class PdfParser {

    actual suspend fun extractText(bytes: ByteArray): String {
        throw UnsupportedOperationException(
            "PDF parsing is not supported on Android. " +
            "Please use the desktop CLI for document indexing."
        )
    }

    actual fun isSupported(): Boolean = false
}
