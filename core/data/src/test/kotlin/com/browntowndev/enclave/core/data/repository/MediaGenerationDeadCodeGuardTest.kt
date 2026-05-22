package com.browntowndev.enclave.core.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MediaGenerationDeadCodeGuardTest {
    private val sourceText: String by lazy {
        resolveProductionSource(
            "src/main/kotlin/com/browntowndev/enclave/core/data/repository/MediaToolExecutor.kt",
        ).readText()
    }

    @Test
    fun `CLEAN-01 MediaToolExecutor source contains zero occurrences of cacheEphemeralMedia`() {
        assertEquals(0, sourceText.split("cacheEphemeralMedia").size - 1)
    }

    @Test
    fun `CLEAN-02 MediaToolExecutor source contains zero occurrences of addEphemeralMedia`() {
        assertEquals(0, sourceText.split("addEphemeralMedia").size - 1)
    }

    @Test
    fun `CLEAN-03 MediaToolExecutor source contains zero occurrences of executeImage(`() {
        assertEquals(0, sourceText.split("executeImage(").size - 1)
    }

    @Test
    fun `CLEAN-04 MediaToolExecutor source contains zero occurrences of GenerateImageUseCase`() {
        assertEquals(0, sourceText.split("GenerateImageUseCase").size - 1)
    }

    @Test
    fun `M-CLEAN-01 Guard fails if legacy executeImage private method reappears`() {
        val count = sourceText.split("executeImage(").size - 1
        assertEquals(0, count, "executeImage( must not appear in MediaToolExecutor.kt")
    }

    private fun resolveProductionSource(relativePath: String): File {
        var dir = File(System.getProperty("user.dir"))
        for (unused in 0 until 6) {
            val candidate = dir.resolve(relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: break
        }
        error("Could not resolve production source at $relativePath")
    }
}
