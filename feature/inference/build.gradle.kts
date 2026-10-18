plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.apter.robolectric.junit5)
}

android {
    namespace = "com.browntowndev.enclave.feature.inference"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.kotlin.reflect)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    // Inference native bindings
    implementation(project(":llama-android"))
    implementation(project(":local-image-gen-android"))

    // LiteRT
    implementation(libs.litertlm.android)
    implementation(libs.onnxruntime.android)
    implementation(libs.firebase.crashlytics)

    // OpenAI
    implementation(libs.openai.java)
    implementation(libs.anthropic.java)
    implementation(libs.google.genai) {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockk)
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.apter.junit5.robolectric.extension)
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.26.0")
    testImplementation(testFixtures(project(":core:domain")))
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
