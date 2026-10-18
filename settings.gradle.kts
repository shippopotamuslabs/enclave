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

// whisper-android lives in its own repo (browntown.development-group/whisper-android),
// vendored as a submodule and consumed via composite build (ENC-53). The Maven
// coordinates are substituted with the included build's projects during development.
includeBuild("third_party/whisper-android") {
    dependencySubstitution {
        substitute(module("com.shippopotamuslabs:whisper-android"))
            .using(project(":whisper-android"))
        substitute(module("com.shippopotamuslabs:whisper-android-hilt"))
            .using(project(":whisper-android-hilt"))
    }
}
