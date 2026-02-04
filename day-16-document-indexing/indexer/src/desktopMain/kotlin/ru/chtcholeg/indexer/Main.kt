package ru.chtcholeg.indexer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin
import ru.chtcholeg.indexer.di.indexerCommonModule
import ru.chtcholeg.indexer.di.indexerPlatformModule
import ru.chtcholeg.indexer.presentation.IndexerScreen
import ru.chtcholeg.indexer.presentation.IndexerStore
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

// Brand colors from logo
private val BrandOrange = Color(0xFFF7941D)
private val BrandCyan = Color(0xFF7FBCD5)
private val BrandOrangeDark = Color(0xFFE67E00)
private val BrandCyanDark = Color(0xFF5BA4BD)

fun main() = application {
    // Set system look and feel for file chooser
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (_: Exception) {
        // Ignore
    }

    // Initialize Koin
    startKoin {
        modules(indexerPlatformModule, indexerCommonModule)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Document Indexer - Ollama"
    ) {
        val store = getKoin().get<IndexerStore>()

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = BrandOrange,
                onPrimary = Color.White,
                primaryContainer = BrandOrangeDark,
                onPrimaryContainer = Color.White,
                secondary = BrandCyan,
                onSecondary = Color.White,
                secondaryContainer = BrandCyanDark,
                onSecondaryContainer = Color.White,
                tertiary = BrandCyan.copy(alpha = 0.7f),
                error = Color(0xFFCF6679),
                errorContainer = Color(0xFF93000A),
                onError = Color.White,
                onErrorContainer = Color(0xFFFFDAD6),
                surface = Color(0xFF1C1B1F),
                onSurface = Color(0xFFE6E1E5),
                surfaceVariant = Color(0xFF49454F),
                onSurfaceVariant = Color(0xFFCAC4D0)
            )
        ) {
            IndexerScreen(
                store = store,
                onPickDirectory = { pickDirectory() }
            )
        }
    }
}

/**
 * Opens a directory picker dialog
 * @return Selected directory path or null if cancelled
 */
private fun pickDirectory(): String? {
    val fileChooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        dialogTitle = "Select File or Directory to Index"
        isAcceptAllFileFilterUsed = true

        // Start in user's home directory
        currentDirectory = File(System.getProperty("user.home"))
    }

    return if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        fileChooser.selectedFile.absolutePath
    } else {
        null
    }
}
