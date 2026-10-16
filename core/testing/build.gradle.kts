plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.browntowndev.enclave.core.testing"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
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

dependencies {
    implementation(project(":core:domain"))
    api(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    api(libs.kotlinx.coroutines.test)
}
