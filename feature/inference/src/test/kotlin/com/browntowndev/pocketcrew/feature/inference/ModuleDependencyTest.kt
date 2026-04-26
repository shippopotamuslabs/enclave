package com.browntowndev.pocketcrew.feature.inference

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleDependencyTest {

    private val moduleRoot = File("src")
    private val buildFile = File("build.gradle.kts")

    @Test
    @DisplayName("feature:inference build file must not declare :core:data dependency")
    fun buildFileDoesNotDeclareCoreDataDependency() {
        assertTrue(buildFile.exists(), "build.gradle.kts should exist")
        val content = buildFile.readText()
        assertFalse(
            content.contains("""project(":core:data")"""),
            "build.gradle.kts must not declare project(\":core:data\") dependency",
        )
    }

    @Test
    @DisplayName("feature:inference source files must not import from core.data")
    fun sourceFilesDoNotImportCoreData() {
        val violations = mutableListOf<String>()

        moduleRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (line.trimStart().startsWith("import com.browntowndev.pocketcrew.core.data")) {
                        violations.add("${file.path}:${index + 1}: $line")
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "Found ${violations.size} import(s) from core.data in :feature:inference:\n${violations.joinToString("\n")}",
        )
    }
}
