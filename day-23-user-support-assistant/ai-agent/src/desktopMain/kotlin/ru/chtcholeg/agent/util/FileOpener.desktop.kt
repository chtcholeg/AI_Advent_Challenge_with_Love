package ru.chtcholeg.agent.util

import java.awt.Desktop
import java.io.File
import java.net.URI

actual object FileOpener {
    actual fun openFile(path: String) {
        try {
            // Check if it's a URL
            if (path.startsWith("http://") || path.startsWith("https://")) {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI(path))
                }
            } else {
                // It's a file path
                val file = File(path)
                val resolved = if (file.isAbsolute) file else File(System.getProperty("user.dir"), path)
                if (resolved.exists() && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(resolved)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
