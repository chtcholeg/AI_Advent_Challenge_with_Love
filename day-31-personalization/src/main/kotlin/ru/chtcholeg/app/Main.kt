package ru.chtcholeg.app

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.skia.Image as SkiaImage
import ru.chtcholeg.app.di.initKoin
import java.awt.RenderingHints
import java.awt.Taskbar
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun main() {
    val iconBytes = loadIconBytes()

    // Build a circular icon once; reuse for both Dock and Window title bar.
    val circularAwtIcon = iconBytes?.let { runCatching { makeCircularBufferedImage(it) }.getOrNull() }

    // On macOS, Window(icon = ...) only sets the title bar icon.
    // To set the Dock icon we must use java.awt.Taskbar before application{} starts.
    if (circularAwtIcon != null) {
        runCatching {
            if (Taskbar.isTaskbarSupported()) {
                val taskbar = Taskbar.getTaskbar()
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.iconImage = circularAwtIcon
                }
            }
        }
    }

    initKoin()

    application {
        val windowState = rememberWindowState(width = 1100.dp, height = 720.dp)

        // Re-encode circular AWT image → PNG bytes → Skia → Compose Painter
        val icon = circularAwtIcon?.let {
            runCatching {
                val baos = ByteArrayOutputStream()
                ImageIO.write(it, "PNG", baos)
                BitmapPainter(SkiaImage.makeFromEncoded(baos.toByteArray()).toComposeImageBitmap())
            }.getOrNull()
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "AI Chat",
            state = windowState,
            icon = icon
        ) {
            App()
        }
    }
}

private fun loadIconBytes(): ByteArray? =
    Thread.currentThread().contextClassLoader?.run {
        getResourceAsStream("icon.webp")?.readBytes()
            ?: getResourceAsStream("icon.png")?.readBytes()
    }

// Decode image bytes with Skia (supports webp/png/jpeg), then apply a circular
// alpha mask so the icon looks round in the macOS Dock and window title bar.
private fun makeCircularBufferedImage(bytes: ByteArray): BufferedImage {
    val skBitmap = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    val w = skBitmap.width
    val h = skBitmap.height

    val pixels = IntArray(w * h)
    skBitmap.readPixels(pixels, 0, 0, w, h)

    val src = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also {
        it.setRGB(0, 0, w, h, pixels, 0, w)
    }

    val result = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g2 = result.createGraphics()
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.clip = Ellipse2D.Float(0f, 0f, w.toFloat(), h.toFloat())
    g2.drawImage(src, 0, 0, null)
    g2.dispose()

    return result
}
