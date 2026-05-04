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

    tasks.withType<Test> {
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
            
            val javaClasses = fileTree("$buildDir/intermediates/javac/$variantName/classes") {
                exclude(fileFilter)
            }
            val kotlinClasses = fileTree("$buildDir/tmp/kotlin-classes/$variantName") {
                exclude(fileFilter)
            }
            val builtInKotlinClasses = fileTree("$buildDir/intermediates/built_in_kotlinc/$variantName/compile${variantName.capitalize()}Kotlin/classes") {
                exclude(fileFilter)
            }

            classDirectories.setFrom(files(javaClasses, kotlinClasses, builtInKotlinClasses))

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



