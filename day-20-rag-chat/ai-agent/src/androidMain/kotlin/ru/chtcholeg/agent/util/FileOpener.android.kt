package ru.chtcholeg.agent.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

actual object FileOpener : KoinComponent {
    private val context: Context by inject()

    actual fun openFile(path: String) {
        try {
            // Check if it's a URL
            if (path.startsWith("http://") || path.startsWith("https://")) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(path)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                // It's a file path
                val file = File(path)
                if (!file.exists()) return

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "text/plain")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
