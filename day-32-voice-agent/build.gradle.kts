import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "ru.chtcholeg.app"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(compose.materialIconsExtended)

    // Ktor HTTP client
    implementation("io.ktor:ktor-client-core:3.0.2")
    implementation("io.ktor:ktor-client-okhttp:3.0.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")
    implementation("io.ktor:ktor-client-logging:3.0.2")

    // Kotlinx
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // Koin DI
    implementation("io.insert-koin:koin-core:4.0.0")
    implementation("io.insert-koin:koin-compose:4.0.0")

    // Vosk Speech-to-Text (offline, bundles native libvosk for all platforms)
    implementation("com.alphacephei:vosk:0.3.45")
    // JNA — explicit compile dep for our custom VoskLib binding that bypasses
    // the broken LibVosk static initializer (vosk_recognizer_set_grm missing on macOS)
    implementation("net.java.dev.jna:jna:5.8.0")
}

compose.desktop {
    application {
        mainClass = "ru.chtcholeg.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "AI Chat"
            packageVersion = "1.0.0"

            macOS {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}
