pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.mozilla.org/maven2/") }
    }
}

rootProject.name = "Enclave"
include(":app")
include(":llama-android")
include(":whisper-android")
include(":local-image-gen-android")

// Core modules
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":core:testing")
include(":detekt-rules")
include(":core:docgen")
include(":core:agent")

// Feature modules
include(":feature:chat")
include(":feature:download")
include(":feature:history")
include(":feature:settings")
include(":feature:moa-pipeline-worker")
include(":feature:inference")
include(":feature:chat-inference-service")
include(":feature:studio")
include(":feature:studio-generation-service")
include(":feature:onboarding")
