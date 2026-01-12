package ru.chtcholeg.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import ru.chtcholeg.app.di.initKoin
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Initialize Koin
    initKoin()

    val rootElement = document.getElementById("root") ?: error("Root element not found")
    ComposeViewport(rootElement) {
        App()
    }
}
