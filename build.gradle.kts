// Top-level build file where you can add configuration options common to all sub-projects/modules.
import java.util.Locale

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    id("jacoco")
}

val jacocoVersion = libs.versions.jacocoVersion.get()

val fileFilter = mutableListOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "android/**/*.*",
    "**/Lambda$*.class",
    "**/Lambda.class",
    "**/*Lambda.class",
    "**/*Lambda*.class",
    "**/*_MembersInjector.class",
    "**/Dagger*Component*.*",
    "**/*Module_*Factory.class",
    "**/*_Factory*.*",
    "**/*_Provide*Factory*.*",
    "**/*_Impl*.*",
    "**/*_Impl.class",
    "**/*_Impl$*.class",
    "**/Hilt_*.*",
    "**/*Hilt*.*",
    "**/*_HiltModules*.*",
    "**/*Extensions*.*",
    "**/*$Result.class",
    "**/*$Result$*.class",
    // Compose
    "**/*ComposableIT*.*",
    "**/*Composable*.*",
    "**/*Screen*.*",
    "**/*Content*.*",
    "**/*Preview*.*",
    "**/*Component*.*",
    "**/*Activity*.*",
    "**/*Fragment*.*",
    "**/*Adapter*.*",
    "**/*ViewHolder*.*"
)

fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

subprojects {
    apply(plugin = "jacoco")

    jacoco {
        toolVersion = jacocoVersion
    }

    // Centralized screenshot-test wiring (ENC-19): covered modules opt in with
    // `alias(libs.plugins.screenshot)`; the experimental flag and test deps come
    // from here so new modules copy nothing.
    plugins.withId("com.android.compose.screenshot") {
        plugins.withId("com.android.library") {
            extensions.configure<com.android.build.api.dsl.LibraryExtension> {
                experimentalProperties["android.experimental.enableScreenshotTest"] = true
            }
        }
        plugins.withId("com.android.application") {
            extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
                experimentalProperties["android.experimental.enableScreenshotTest"] = true
            }
        }
        afterEvaluate {
            dependencies.add("screenshotTestImplementation", libs.screenshot.validation.api)
            dependencies.add("screenshotTestImplementation", libs.androidx.compose.ui.tooling)
            dependencies.add("screenshotTestImplementation", libs.junit)
        }
    }

    tasks.withType<Test> {
        // Screenshot plugin generates JUnit4 tests; keep its tasks on useJUnit().
        if (
            plugins.hasPlugin("com.android.compose.screenshot") &&
            name in setOf(
                "updateDebugScreenshotTest", "validateDebugScreenshotTest",
                "updateReleaseScreenshotTest", "validateReleaseScreenshotTest",
            )
        ) {
            useJUnit()
        }
        jvmArgs("-noverify")
        configure<JacocoTaskExtension> {
            isIncludeNoLocationClasses = true
            excludes = listOf("jdk.internal.*")
        }
    }

    fun Project.registerJacocoReport(variantName: String) {
        val testTaskName = "test${variantName.capitalize()}UnitTest"
        val reportTaskName = "jacoco${variantName.capitalize()}Report"

        tasks.register<JacocoReport>(reportTaskName) {
            dependsOn(testTaskName)
            group = "Reporting"
            description = "Generate Jacoco coverage reports for the $variantName variant."

            reports {
                xml.required.set(true)
                html.required.set(true)
            }

            val buildDir = layout.buildDirectory.get().asFile
            
            val asmClassesDir = "$buildDir/intermediates/classes/$variantName/transform${variantName.capitalize()}ClassesWithAsm/dirs"
            val classesDir = if (file(asmClassesDir).exists()) {
                fileTree(asmClassesDir) { exclude(fileFilter) }
            } else {
                val javaClasses = fileTree("$buildDir/intermediates/javac/$variantName/classes") {
                    exclude(fileFilter)
                }
                val kotlinClasses = fileTree("$buildDir/tmp/kotlin-classes/$variantName") {
                    exclude(fileFilter)
                }
                val builtInKotlinClasses = fileTree("$buildDir/intermediates/built_in_kotlinc/$variantName/compile${variantName.capitalize()}Kotlin/classes") {
                    exclude(fileFilter)
                }
                files(javaClasses, kotlinClasses, builtInKotlinClasses)
            }

            classDirectories.setFrom(classesDir)

            sourceDirectories.setFrom(files(
                "$projectDir/src/main/java",
                "$projectDir/src/main/kotlin"
            ))

            executionData.setFrom(files(
                file("$buildDir/jacoco/${testTaskName}.exec"),
                file("$buildDir/outputs/unit_test_code_coverage/${variantName}UnitTest/${testTaskName}.exec")
            ))
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            buildTypes {
                getByName("debug") {
                    enableUnitTestCoverage = true
                }
            }
        }
        extensions.configure<com.android.build.api.variant.LibraryAndroidComponentsExtension> {
            onVariants { variant ->
                registerJacocoReport(variant.name)
            }
        }
    }

    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            buildTypes {
                getByName("debug") {
                    enableUnitTestCoverage = true
                }
            }
        }
        extensions.configure<com.android.build.api.variant.ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                registerJacocoReport(variant.name)
            }
        }
    }

    // Centralized Linting and Static Analysis
    val excludedModules = listOf(":llama-android", ":whisper-android")
    if (path !in excludedModules) {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "io.gitlab.arturbosch.detekt")

        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            android.set(true)
            ignoreFailures.set(true)
            reporters {
                reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
                reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
            }
        }

        configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            config.setFrom("$rootDir/config/detekt/detekt.yml")
            buildUponDefaultConfig = true
            allRules = false
            ignoreFailures = false
        }

        tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
            exclude("**/docgen_core.kt")
            exclude("**/agent_core.kt")
            exclude("**/classifier_core.kt")
        }

        tasks.matching { it.name.startsWith("assemble") }.configureEach {
            dependsOn("detekt")
        }
    }

    plugins.withId("com.android.application") {
        tasks.matching { it.name.startsWith("assemble") }.configureEach {
            dependsOn(
                rootProject.subprojects
                    .filter { sub -> sub.path !in excludedModules && sub.tasks.findByName("detekt") != null }
                    .map { "${it.path}:detekt" },
            )
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        tasks.register<JacocoReport>("jacocoTestReport") {
            dependsOn("test")
            group = "Reporting"
            description = "Generate Jacoco coverage reports for the test task."

            reports {
                xml.required.set(true)
                html.required.set(true)
            }

            val buildDir = layout.buildDirectory.get().asFile

            classDirectories.setFrom(files(
                fileTree("$buildDir/classes/kotlin/main") {
                    exclude(fileFilter)
                }
            ))

            sourceDirectories.setFrom(files(
                "$projectDir/src/main/kotlin"
            ))

            executionData.setFrom(file("$buildDir/jacoco/test.exec"))
        }
    }
}



