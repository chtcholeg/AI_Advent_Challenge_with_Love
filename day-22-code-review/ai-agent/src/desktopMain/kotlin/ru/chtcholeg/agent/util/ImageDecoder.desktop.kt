package ru.chtcholeg.agent.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.util.Base64

actual object ImageDecoder {
    actual fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        return try {
            val bytes = Base64.getDecoder().decode(base64)
            val skiaImage = Image.makeFromEncoded(bytes)
            skiaImage.toComposeImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
