import com.android.build.gradle.internal.tasks.MergeNativeLibsTask
import java.io.File
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.browntowndev.enclave.llama"
    compileSdk = 37
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
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.test.junit5)
}

tasks.register("verifyLlamaJniPackaging") {
    group = "verification"
    description =
        "Ensures both llama-jni NEON (CMake) and SVE (prebuilt) libraries are packaged."

    val mergedNativeLibsRoot =
        layout.buildDirectory
            .dir("intermediates/merged_native_libs")
            .get()
            .asFile
            .absolutePath
    val prebuiltSve =
        layout.projectDirectory
            .file("src/main/jniLibs/arm64-v8a/libllama-jni-sve.so")
            .asFile
            .absolutePath

    doLast {
        val mergedRoot = File(mergedNativeLibsRoot)
        val neonPresent =
            mergedRoot.exists() &&
                mergedRoot.walkTopDown().any { it.isFile && it.name == "libllama-jni-neon.so" }
        val svePresentInMerge =
            mergedRoot.exists() &&
                mergedRoot.walkTopDown().any { it.isFile && it.name == "libllama-jni-sve.so" }
        val svePresentInJniLibs = File(prebuiltSve).isFile
        val svePresent = svePresentInMerge || svePresentInJniLibs

        if (!neonPresent || !svePresent) {
            val missing =
                buildList {
                    if (!neonPresent) add("libllama-jni-neon.so (built by CMake)")
                    if (!svePresent) {
                        add(
                            "libllama-jni-sve.so (merged native libs or prebuilt at $prebuiltSve)",
                        )
                    }
                }
            throw GradleException(
                "llama-android packaging is missing ${missing.joinToString(", ")}.",
            )
        }

        logger.lifecycle("Verified llama-jni dual packaging: NEON + SVE present")
    }
}

afterEvaluate {
    tasks.withType<MergeNativeLibsTask>().configureEach {
        finalizedBy("verifyLlamaJniPackaging")
    }
}
