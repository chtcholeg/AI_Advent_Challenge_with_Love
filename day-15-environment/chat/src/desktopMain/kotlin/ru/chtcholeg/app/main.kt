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
