package ru.chtcholeg.shared.domain.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

actual class PdfParser {

    actual suspend fun extractText(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        Loader.loadPDF(bytes).use { document ->
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }
            stripper.getText(document)
        }
    }

    actual fun isSupported(): Boolean = true
}
