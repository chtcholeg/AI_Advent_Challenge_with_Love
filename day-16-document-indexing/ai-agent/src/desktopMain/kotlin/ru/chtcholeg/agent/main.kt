package ru.chtcholeg.agent

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ru.chtcholeg.agent.di.initKoin

fun main() {
    initKoin()
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
