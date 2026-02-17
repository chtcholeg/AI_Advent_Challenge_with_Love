package ru.chtcholeg.shared.domain.service

/**
 * Implementation of TextChunker with support for multiple chunking strategies
 */
class TextChunkerImpl(
    private val config: ChunkConfig = ChunkConfig()
) : TextChunker {

    override fun chunk(text: String, source: String): List<TextChunk> {
        if (text.isBlank()) {
            return emptyList()
        }

        val rawChunks = when (config.strategy) {
            ChunkStrategy.BY_CHARACTERS -> chunkByCharacters(text)
            ChunkStrategy.BY_TOKENS -> chunkByTokens(text)
            ChunkStrategy.BY_SENTENCES -> chunkBySentences(text)
        }

        // Merge last chunk with previous if it's too small
        val chunks = mergeSmallLastChunk(rawChunks)

        return chunks.mapIndexed { index, chunkText ->
            TextChunk(
                text = chunkText.trim(),
                chunkIndex = index,
                totalChunks = chunks.size,
                source = source
            )
        }
    }

    /**
     * If the last chunk is smaller than minChunkSize, merge it with the previous chunk
     */
    private fun mergeSmallLastChunk(chunks: List<String>): List<String> {
        if (chunks.size < 2) {
            return chunks
        }

        val lastChunk = chunks.last()
        if (lastChunk.length >= config.minChunkSize) {
            return chunks
        }

        // Merge last chunk with previous
        val result = chunks.toMutableList()
        val previousIndex = result.size - 2
        result[previousIndex] = result[previousIndex] + " " + lastChunk
        result.removeAt(result.size - 1)
        return result
    }

    /**
     * Split text by character count with overlap
     */
    private fun chunkByCharacters(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var startIndex = 0

        while (startIndex < text.length) {
            val endIndex = minOf(startIndex + config.chunkSize, text.length)
            val chunk = text.substring(startIndex, endIndex)
            chunks.add(chunk)

            // Move forward by chunkSize - overlapSize
            startIndex += config.chunkSize - config.overlapSize
            if (startIndex >= text.length) break
        }

        return chunks
    }

    /**
     * Split text by approximate token count
     * Uses simple heuristic: 1 token ≈ 4 characters for English
     */
    private fun chunkByTokens(text: String): List<String> {
        val approximateCharactersPerToken = 4
        val chunkSizeInChars = config.chunkSize * approximateCharactersPerToken
        val overlapSizeInChars = config.overlapSize * approximateCharactersPerToken

        val tempConfig = config.copy(
            chunkSize = chunkSizeInChars,
            overlapSize = overlapSizeInChars
        )

        return TextChunkerImpl(tempConfig).chunkByCharacters(text)
    }

    /**
     * Split text by sentences while respecting chunk size
     */
    private fun chunkBySentences(text: String): List<String> {
        // Split by common sentence endings
        val sentences = text.split(Regex("[.!?]+\\s+"))
            .filter { it.isNotBlank() }

        if (sentences.isEmpty()) {
            return listOf(text)
        }

        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()

        for (sentence in sentences) {
            val sentenceWithPunctuation = sentence.trim()

            if (currentChunk.isEmpty()) {
                currentChunk.append(sentenceWithPunctuation)
            } else {
                val potentialLength = currentChunk.length + sentenceWithPunctuation.length + 1

                if (potentialLength <= config.chunkSize) {
                    currentChunk.append(" ").append(sentenceWithPunctuation)
                } else {
                    // Save current chunk and start new one
                    chunks.add(currentChunk.toString())
                    currentChunk.clear()
                    currentChunk.append(sentenceWithPunctuation)
                }
            }
        }

        // Add last chunk if not empty
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }

        return chunks
    }
}
