plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.browntowndev.pocketcrew.llama"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 34

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                
                // Base arguments
                val cmakeArgs = mutableListOf(
                    "-DGGML_CPU_ARM_ENABLE_SVE=OFF",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
                
                // Support overriding via -PCMAKE_ARGS (used by build-kleidiai.sh)
                if (project.hasProperty("CMAKE_ARGS")) {
                    val extraArgs = project.property("CMAKE_ARGS").toString().split(";")
                    cmakeArgs.addAll(extraArgs)
                }
                
                arguments(*cmakeArgs.toTypedArray())
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val hasLlamaCpp = file("../third_party/llama.cpp/CMakeLists.txt").exists()
    if (hasLlamaCpp) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    } else {
        println("Skipping externalNativeBuild: third_party/llama.cpp not found. (Expected in CI environments)")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
