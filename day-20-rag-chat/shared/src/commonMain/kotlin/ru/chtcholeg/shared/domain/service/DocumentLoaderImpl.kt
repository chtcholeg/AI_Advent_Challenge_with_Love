package ru.chtcholeg.shared.domain.service

/**
 * Implementation of DocumentLoader using FileSystem
 * Supports text files (md, txt, etc.) and PDF files
 */
class DocumentLoaderImpl(
    private val fileSystem: FileSystem,
    private val pdfParser: PdfParser = PdfParser()
) : DocumentLoader {

    companion object {
        private val PDF_EXTENSION = "pdf"
    }

    override suspend fun loadDocument(filePath: String): DocumentContent {
        if (!fileSystem.fileExists(filePath)) {
            throw IllegalArgumentException("File not found: $filePath")
        }

        val fileName = filePath.substringAfterLast('/')
        val fileSize = fileSystem.getFileSize(filePath)
        val lastModified = fileSystem.getLastModified(filePath)

        // Extract content based on file type
        val content = if (isPdfFile(filePath)) {
            extractPdfContent(filePath)
        } else {
            fileSystem.readFile(filePath)
        }

        return DocumentContent(
            filePath = filePath,
            content = content,
            fileName = fileName,
            fileSize = fileSize,
            lastModified = lastModified
        )
    }

    override suspend fun loadDocumentsFromDirectory(
        directoryPath: String,
        extensions: List<String>
    ): List<DocumentContent> {
        val filePaths = fileSystem.listFiles(directoryPath, extensions)

        return filePaths.map { filePath ->
            loadDocument(filePath)
        }
    }

    private fun isPdfFile(filePath: String): Boolean {
        return filePath.lowercase().endsWith(".$PDF_EXTENSION")
    }

    private suspend fun extractPdfContent(filePath: String): String {
        if (!pdfParser.isSupported()) {
            throw UnsupportedOperationException(
                "PDF parsing is not supported on this platform. " +
                "Please use the desktop CLI for PDF document indexing."
            )
        }

        val bytes = fileSystem.readFileBytes(filePath)
        return pdfParser.extractText(bytes)
    }
}
