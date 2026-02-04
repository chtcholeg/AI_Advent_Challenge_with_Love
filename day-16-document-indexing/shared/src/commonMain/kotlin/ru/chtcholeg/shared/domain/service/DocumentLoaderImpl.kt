package ru.chtcholeg.shared.domain.service

/**
 * Implementation of DocumentLoader using FileSystem
 */
class DocumentLoaderImpl(
    private val fileSystem: FileSystem
) : DocumentLoader {

    override suspend fun loadDocument(filePath: String): DocumentContent {
        if (!fileSystem.fileExists(filePath)) {
            throw IllegalArgumentException("File not found: $filePath")
        }

        val content = fileSystem.readFile(filePath)
        val fileName = filePath.substringAfterLast('/')
        val fileSize = fileSystem.getFileSize(filePath)
        val lastModified = fileSystem.getLastModified(filePath)

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
}
