package com.browntowndev.enclave.app.telemetry

import com.browntowndev.enclave.app.AppBindingModule
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import dagger.Binds
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaMethod

/**
 * TELEMETRY-001 TDD Red — app integration scenarios TEL-054, TEL-055, TEL-056.
 */
class Telemetry001AppTddRedTest {
    @Test
    fun `TEL-054 All recordException call sites migrated to error`() {
        val callSites = findRecordExceptionCallSitesInProduction()
        assertTrue(
            callSites.isEmpty(),
            "recordException call sites remain in production code: $callSites",
        )
    }

    @Test
    fun `TEL-055 App Hilt module binds TelemetryEventPort`() {
        val bindsMethod =
            AppBindingModule::class.declaredFunctions.firstOrNull { function ->
                function.findAnnotation<Binds>() != null &&
                    function.returnType.classifier == TelemetryEventPort::class
            }
        assertNotNull(bindsMethod, "App module must bind TelemetryEventPort")
        val returnType = bindsMethod!!.returnType.classifier
        assertTrue(
            returnType == TelemetryEventPort::class,
            "Expected bind return type TelemetryEventPort but was $returnType",
        )
        val bindsAnnotation = bindsMethod.findAnnotation<Binds>()
        assertNotNull(bindsAnnotation)
    }

    @Test
    fun `TEL-057 App test fakes no longer override recordException`() {
        val methodNames =
            com.browntowndev.enclave.testing.FakeLoggingPort::class
                .declaredFunctions
                .mapNotNull { it.javaMethod?.name }
                .toSet()
        assertTrue(!methodNames.contains("recordException"))
    }

    @Test
    fun `TEL-056 EnclaveApplication initializes telemetry scheduler`() {
        val source = projectRoot().resolve("app/src/main/kotlin/com/browntowndev/enclave/EnclaveApplication.kt").readText()
        assertTrue(
            source.contains("telemetryUploadScheduler.schedule()"),
            "EnclaveApplication.onCreate must call telemetryUploadScheduler.schedule() exactly once",
        )
    }

    private fun projectRoot(): Path {
        var current = Path.of(System.getProperty("user.dir"))
        while (current.parent != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.parent
        }
        return current
    }

    private fun findRecordExceptionCallSitesInProduction(): List<String> {
        val root = projectRoot()
        val productionRoots =
            listOf(
                "app/src/main",
                "core/domain/src/main",
                "core/data/src/main",
                "core/ui/src/main",
                "core/agent/src/main",
                "core/docgen/src/main",
                "feature/chat/src/main",
                "feature/inference/src/main",
                "feature/history/src/main",
                "feature/settings/src/main",
                "feature/downloads/src/main",
                "feature/studio/src/main",
                "feature/services/src/main",
            )
        return productionRoots.flatMap { relative ->
            val dir = root.resolve(relative)
            if (!Files.exists(dir)) {
                emptyList()
            } else {
                dir
                    .walk()
                    .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                    .flatMap { file ->
                        file
                            .readText()
                            .lineSequence()
                            .withIndex()
                            .filter { (_, line) ->
                                line.contains("recordException") &&
                                    !line.trimStart().startsWith("//") &&
                                    !line.contains("fun recordException") &&
                                    !line.contains("crashlytics.recordException")
                            }.map { (index, _) -> "${root.relativize(file)}:${index + 1}" }
                            .toList()
                    }.toList()
            }
        }
    }
}
