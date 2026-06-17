package com.browntowndev.enclave.domain.model.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalImageGenerationModelPolicyMemoryTest {
    @Test
    fun `SDXL defaults to LOW_RAM`() {
        assertEquals(
            LocalImageGenerationMemoryPolicy.LOW_RAM,
            LocalImageGenerationModelPolicy.memoryPolicyFor(
                LocalImageGenerationModelId.SDXL_QNN,
                memoryClassMb = 512,
            ),
        )
    }

    @Test
    fun `SD15 uses LOW_RAM on constrained devices`() {
        assertEquals(
            LocalImageGenerationMemoryPolicy.LOW_RAM,
            LocalImageGenerationModelPolicy.memoryPolicyFor(
                LocalImageGenerationModelId.SD15_QNN,
                memoryClassMb = 256,
            ),
        )
    }

    @Test
    fun `img2img denoise default is 0_75`() {
        assertEquals(0.75f, LocalImageGenerationModelPolicy.DEFAULT_IMG2IMG_DENOISE_STRENGTH)
    }
}
