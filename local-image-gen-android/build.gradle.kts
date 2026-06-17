plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.browntowndev.enclave.localimagegen"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
                val cmakeArgs = mutableListOf<String>()
                val localProps = rootProject.file("local.properties")
                if (localProps.exists()) {
                    val qnnSdkDir =
                        localProps
                            .readLines()
                            .firstOrNull { it.startsWith("qnn.sdk.dir=") }
                            ?.substringAfter("=")
                            ?.trim()
                    if (!qnnSdkDir.isNullOrEmpty()) {
                        cmakeArgs += "-DQNN_SDK_ROOT=$qnnSdkDir"
                    }
                }
                if (cmakeArgs.isNotEmpty()) {
                    arguments(*cmakeArgs.toTypedArray())
                }
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}