plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "com.browntowndev.enclave"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.23.8")
    testImplementation("io.gitlab.arturbosch.detekt:detekt-api:1.23.8")
    testImplementation("io.gitlab.arturbosch.detekt:detekt-parser:1.23.8")
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
