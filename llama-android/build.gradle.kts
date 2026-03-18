plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.browntowndev.pocketcrew.llama"
    compileSdk = 36
    ndkVersion = "27.0.11718014"

    defaultConfig {
        minSdk = 34

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    // Point to local KleidiAI sources to avoid FetchContent download
                    "-DFETCHCONTENT_SOURCE_DIR_KLEIDIAI_DOWNLOAD=/home/sean/Code/pocket-crew/third_party/kleidiai-1.22.0"
                )
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
