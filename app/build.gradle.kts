import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// 1. Load properties
val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            load(FileInputStream(localPropertiesFile))
        }
    }

ktlint {
    android.set(true)
    ignoreFailures.set(false)
}

detekt {
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
    ignoreFailures = true
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.browntowndev.pocketcrew"
    compileSdk = 36

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    defaultConfig {
        applicationId = "com.browntowndev.pocketcrew"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "ALLOW_CANCEL_DOWNLOAD", "false")
    }

    signingConfigs {
        create("release") {
            // Reference the path relative to the rootProject directory
            storeFile = rootProject.file(".keystore/pocket_crew_keystore.jks")

            // 2. Assign secrets from the properties file
            storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")

            // Required for modern AVDs and Play Store compatibility
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            enableUnitTestCoverage = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Disable connectedAndroidTest for unit test coverage
tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    tasks.getByName("collectDebugCoverage").dependsOn(this)
    enabled = false
}

dependencies {
    // Using custom llama-android (llama.cpp with KleidiAI)
    // Native libraries are built automatically via build-kleidiai.sh
    implementation(project(":llama-android"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:history"))
    implementation(project(":feature:download"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:moa-pipeline-worker"))
    implementation(project(":feature:inference"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.litert)
    implementation(libs.litertlm.android)
    implementation(libs.tasks.genai)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.hilt.android)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.animation)
    implementation(libs.identity.doctypes.jvm)
    implementation(libs.androidx.compose.foundation)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(project(":core:data"))
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// =============================================================================
// KleidiAI Fat APK Build Check
// =============================================================================
// NOTE: Due to configuration cache issues, we rely on the script being run manually.
// Run: ./build-kleidiai.sh before building the app.
//
// The check is done at packaging time - if libraries are missing, the build will fail.
// We use lazy evaluation to avoid configuration cache issues.
