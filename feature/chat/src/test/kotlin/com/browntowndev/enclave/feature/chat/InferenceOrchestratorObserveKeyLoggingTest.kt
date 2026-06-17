package com.browntowndev.enclave.feature.chat

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

@DisplayName("InferenceOrchestrator debug logging")
class InferenceOrchestratorObserveKeyLoggingTest {
    @Test
    @DisplayName("SC-PERF-16 — InfOrch ackUpdate logging guarded by BuildConfig.DEBUG")
    fun scPerf16_infOrchAckUpdateLogGuardedByBuildConfigDebug() {
        val source = readInferenceOrchestratorSource()
        val debugGuardIndex = source.indexOf("if (BuildConfig.DEBUG)")
        val logIndex = source.indexOf("\"InfOrch\"")

        assertTrue(debugGuardIndex >= 0, "Expected if (BuildConfig.DEBUG) guard in InferenceOrchestrator")
        assertTrue(logIndex >= 0, "Expected InfOrch Log.d in InferenceOrchestrator")
        assertTrue(
            debugGuardIndex < logIndex,
            "InfOrch Log.d must appear inside BuildConfig.DEBUG block",
        )
    }

    private fun readInferenceOrchestratorSource(): String {
        val candidates =
            listOf(
                Paths.get(
                    "feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/delegates/InferenceOrchestrator.kt",
                ),
            )
        val projectRoot = findProjectRoot()
        for (candidate in candidates) {
            val resolved = projectRoot.resolve(candidate)
            if (Files.exists(resolved)) {
                return Files.readString(resolved)
            }
        }
        error("InferenceOrchestrator.kt source not found for SC-PERF-16")
    }

    private fun findProjectRoot(): java.nio.file.Path {
        var dir = Paths.get("").toAbsolutePath()
        repeat(6) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) return dir
            dir = dir.parent ?: return Paths.get("").toAbsolutePath()
        }
        return Paths.get("").toAbsolutePath()
    }
}