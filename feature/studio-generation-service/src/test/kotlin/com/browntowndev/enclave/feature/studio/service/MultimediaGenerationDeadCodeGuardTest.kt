package com.browntowndev.enclave.feature.studio.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class MultimediaGenerationDeadCodeGuardTest {
    private val sourceText: String by lazy {
        resolveProductionSource(
            "src/main/kotlin/com/browntowndev/enclave/feature/studio/service/MultimediaGenerationService.kt",
        ).readText()
    }

    @Test
    fun `CLEAN-05 MultimediaGenerationService source contains zero occurrences of ImageGenerationPort`() {
        assertEquals(0, sourceText.split("ImageGenerationPort").size - 1)
    }

    @Test
    fun `CLEAN-06 MultimediaGenerationService source contains zero occurrences of VideoGenerationPort`() {
        assertEquals(0, sourceText.split("VideoGenerationPort").size - 1)
    }

    @Test
    fun `CLEAN-07 MultimediaGenerationService source contains zero occurrences of MediaProviderRepositoryPort`() {
        assertEquals(0, sourceText.split("MediaProviderRepositoryPort").size - 1)
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
