package ru.chtcholeg.agent

import android.app.Application
import org.koin.android.ext.koin.androidContext
import ru.chtcholeg.agent.di.initKoin

class AgentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@AgentApplication)
        }
    }
}
