rootProject.name = "AI_Advent_Challenge_6"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":shared")
include(":chat")
include(":ai-agent")
include(":indexer")
