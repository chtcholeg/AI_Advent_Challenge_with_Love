package ru.chtcholeg.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import ru.chtcholeg.app.di.appModule
import ru.chtcholeg.app.di.platformModule

class AiChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Koin with Android context
        startKoin {
            androidContext(this@AiChatApplication)
            modules(appModule, platformModule())
        }
    }
}
