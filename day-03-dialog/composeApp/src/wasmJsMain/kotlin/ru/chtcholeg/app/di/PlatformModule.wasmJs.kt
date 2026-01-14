package ru.chtcholeg.app.di

import io.ktor.client.engine.js.*
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { Js.create() }
}
