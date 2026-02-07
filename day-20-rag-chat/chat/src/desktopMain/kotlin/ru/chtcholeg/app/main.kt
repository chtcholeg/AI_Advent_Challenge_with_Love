package ru.chtcholeg.app

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import ru.chtcholeg.app.di.initKoin

fun main() {
    // Initialize Koin
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
        val windowState = rememberWindowState(
            width = 800.dp,
            height = 600.dp
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = "AI Chat",
            state = windowState,
            icon = painterResource("icon.png")
        ) {
            App()
        }
    }
}
