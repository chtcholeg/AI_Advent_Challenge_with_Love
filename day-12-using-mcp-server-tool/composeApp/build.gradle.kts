import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.sqldelight)
}

// Load credentials from local.properties
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val gigachatClientId: String = localProperties.getProperty("gigachat.clientId") ?: ""
val gigachatClientSecret: String = localProperties.getProperty("gigachat.clientSecret") ?: ""
val huggingfaceApiToken: String = localProperties.getProperty("huggingface.apiToken") ?: ""

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)

                // Ktor
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)

                // Kotlinx
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)

                // Koin
                implementation(libs.koin.core)
                implementation(libs.koin.compose)

                // SQLDelight
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)

                // MCP - using stub implementation until official SDK is available
                // TODO: Uncomment when io.modelcontextprotocol:kotlin-sdk is published to Maven Central
                // implementation(libs.mcp.kotlin.sdk)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(compose.preview)
                implementation(libs.activity.compose)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.koin.android)
                implementation(libs.sqldelight.android.driver)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.sqldelight.jvm.driver)
            }
        }
    }
}

android {
    namespace = "ru.chtcholeg.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.chtcholeg.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "ru.chtcholeg.app.MainKt"

        // Enable remote debugging when running with -Pdebug or -Pdebug.suspend
        // Usage:
        //   ./gradlew :composeApp:runDesktop -Pdebug           # Start without waiting for debugger
        //   ./gradlew :composeApp:runDesktop -Pdebug.suspend   # Wait for debugger to attach before starting
        // Then attach debugger to localhost:5005
        val debugPort = 5005
        if (project.hasProperty("debug")) {
            jvmArgs += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$debugPort"
        } else if (project.hasProperty("debug.suspend")) {
            jvmArgs += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:$debugPort"
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "AI Chat"
            packageVersion = "1.0.0"

            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
            }
        }
    }
}

buildkonfig {
    packageName = "ru.chtcholeg.app"

    defaultConfigs {
        buildConfigField(STRING, "GIGACHAT_CLIENT_ID", gigachatClientId)
        buildConfigField(STRING, "GIGACHAT_CLIENT_SECRET", gigachatClientSecret)
        buildConfigField(STRING, "HUGGINGFACE_API_TOKEN", huggingfaceApiToken)
    }
}

sqldelight {
    databases {
        create("ChatDatabase") {
            packageName.set("ru.chtcholeg.app.data.local")
            srcDirs.setFrom("src/commonMain/sqldelight")
        }
        create("McpDatabase") {
            packageName.set("ru.chtcholeg.app.data.local")
            srcDirs.setFrom("src/commonMain/mcp_sqldelight")
        }
    }
}
