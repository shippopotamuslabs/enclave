plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.browntowndev.pocketcrew.core.common"
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
    // Kotlin stdlib
    implementation(libs.kotlin.stdlib)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
}
