package ru.chtcholeg.app

import android.app.Application
import ru.chtcholeg.app.di.initKoin

class AiChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Koin once for the entire app lifecycle
        initKoin()
    }
}
