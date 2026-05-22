package com.browntowndev.pocketcrew.feature.chat

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@DisplayName("ChatViewModel ObserveKeyCompute logging")
class ChatViewModelObserveKeyLoggingTest {
    @Test
    @DisplayName("SC-PERF-15 — ObserveKeyCompute logging guarded by BuildConfig.DEBUG")
    fun scPerf15_observeKeyComputeLogGuardedByBuildConfigDebug() {
        val source = readChatViewModelSource()
        val debugGuardIndex = source.indexOf("if (BuildConfig.DEBUG)")
        val logIndex = source.indexOf("Log.d(\n                    \"ObserveKeyCompute\"")
        val logIndexAlt = source.indexOf("Log.d(\"ObserveKeyCompute\"")
        val observeLogIndex =
            when {
                logIndex >= 0 -> logIndex
                logIndexAlt >= 0 -> logIndexAlt
                else -> source.indexOf("\"ObserveKeyCompute\"")
            }

        assertTrue(debugGuardIndex >= 0, "Expected if (BuildConfig.DEBUG) guard in ChatViewModel")
        assertTrue(observeLogIndex >= 0, "Expected ObserveKeyCompute Log.d in ChatViewModel")
        assertTrue(
            debugGuardIndex < observeLogIndex,
            "ObserveKeyCompute Log.d must appear inside BuildConfig.DEBUG block",
        )
    }

    private fun readChatViewModelSource(): String {
        val candidates =
            listOf(
                Paths.get("feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatViewModel.kt"),
                Paths.get("src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatViewModel.kt"),
            )
        val projectRoot = findProjectRoot()
        for (candidate in candidates) {
            val resolved = projectRoot.resolve(candidate)
            if (Files.exists(resolved)) {
                return Files.readString(resolved)
            }
        }
        error("ChatViewModel.kt source not found for SC-PERF-15")
    }

    private fun findProjectRoot(): Path {
        var dir = Paths.get("").toAbsolutePath()
        repeat(6) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) return dir
            dir = dir.parent ?: return Paths.get("").toAbsolutePath()
        }
        return Paths.get("").toAbsolutePath()
    }
}
