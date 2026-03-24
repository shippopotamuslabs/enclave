plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.browntowndev.pocketcrew.core.testing"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Production dependencies (needed by utilities)
    implementation(project(":core:domain"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    api(libs.junit.jupiter)

    // Test dependencies for MainDispatcherRule
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
