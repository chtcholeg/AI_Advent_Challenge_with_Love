package ru.chtcholeg.agent

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ru.chtcholeg.agent.di.initKoin

fun main() {
    initKoin()

    // Set macOS dock icon (not handled by Compose Window icon parameter)
    try {
        val taskbar = java.awt.Taskbar.getTaskbar()
        val iconStream = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
        if (iconStream != null) {
            taskbar.setIconImage(javax.imageio.ImageIO.read(iconStream))
        }
    } catch (_: Exception) {
        // Taskbar API not supported on this platform
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "AI Agent",
            icon = painterResource("icon.png")
        ) {
            App()
        }
    }
}
