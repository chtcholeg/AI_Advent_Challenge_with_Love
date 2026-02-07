package ru.chtcholeg.agent.util

import android.content.ClipData
import android.content.Context
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import android.content.ClipboardManager as AndroidClipboardManager

actual object ClipboardManager : KoinComponent {
    private val context: Context by inject()

    actual fun copyToClipboard(text: String) {
        try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager
            val clip = ClipData.newPlainText("error", text)
            clipboardManager.setPrimaryClip(clip)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
