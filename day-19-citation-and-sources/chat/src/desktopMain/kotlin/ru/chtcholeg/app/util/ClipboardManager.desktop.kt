package ru.chtcholeg.app.util

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual object ClipboardManager {
    actual fun copyToClipboard(text: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = StringSelection(text)
        clipboard.setContents(selection, selection)
    }
}
