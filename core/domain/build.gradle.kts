plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.apter.robolectric.junit5)
}

android {
    namespace = "com.browntowndev.enclave.core.domain"
    compileSdk = 37

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

    testFixtures {
        enable = true
    }

    packaging {
        resources {
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    // Kotlin stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation(libs.kotlin.reflect)

    // Serialization
    api(libs.kotlinx.serialization.json)

    // Token Estimation
    implementation(libs.jtokkit)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Dependency Injection
    implementation(libs.javax.inject)

    // Test dependencies
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.apter.junit5.robolectric.extension)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.turbine)
    testImplementation(project(":core:testing"))

    // TestFixtures dependencies
    testFixturesApi(libs.junit.jupiter)
    testFixturesImplementation("org.jetbrains.kotlin:kotlin-stdlib")
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
