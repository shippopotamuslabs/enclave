plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.browntowndev.pocketcrew.core.domain"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
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

    sourceSets {
        named("test") {
            kotlin.srcDirs("src/test/java")
        }
    }
}

tasks.withType<Test> {
    // Disable failOnNoDiscoveredTests since this module only has test utilities, not actual tests
    failOnNoDiscoveredTests.set(false)
}

dependencies {
    // Kotlin stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Dependency Injection
    implementation("javax.inject:javax.inject:1")

    // Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}
