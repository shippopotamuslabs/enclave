import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
}

// 1. Load properties
val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            load(FileInputStream(localPropertiesFile))
        }
    }

android {
    namespace = "com.browntowndev.pocketcrew.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 34

        buildConfigField("String", "HUGGING_FACE_API_KEY", "\"${localProperties.getProperty("HUGGING_FACE_API_KEY") ?: ""}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Security — EncryptedSharedPreferences for BYOK API key storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Test dependencies - JUnit 5 (Jupiter)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation(testFixtures(project(":core:domain")))
    testImplementation(project(":core:testing"))
    testImplementation("org.json:json:20240303")

    // JUnit 5 Engine for test discovery
    testRuntimeOnly("org.junit.platform:junit-platform-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
