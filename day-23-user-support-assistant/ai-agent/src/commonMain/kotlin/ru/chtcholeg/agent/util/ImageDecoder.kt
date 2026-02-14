package ru.chtcholeg.agent.util

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Utility for decoding base64-encoded images to ImageBitmap.
 * Platform-specific implementations handle the actual decoding.
 */
expect object ImageDecoder {
    /**
     * Decode a base64-encoded image string to ImageBitmap.
     * @param base64 The base64-encoded image data (without data URI prefix)
     * @return ImageBitmap or null if decoding fails
     */
    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap?
}
