package com.browntowndev.enclave.domain.model.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalImageGenerationModelPolicyTest {
    @Test
    fun `SDXL provider capabilities expose LCM step ladder with 8 default`() {
        val caps = LocalImageGenerationModelPolicy.providerCapabilitiesFor(LocalImageGenerationModelId.SDXL_QNN)

        assertEquals(emptyList<GenerationQuality>(), caps.supportedImageQualities)
        assertEquals(listOf("1024x1024"), caps.supportedImageResolutions)
        assertEquals(listOf(8, 12, 16, 20, 24, 30), caps.supportedImageSteps)
        assertEquals(8, caps.defaultImageStep)
    }

    @Test
    fun `SD15 provider capabilities expose resolution only without quality picker`() {
        val caps = LocalImageGenerationModelPolicy.providerCapabilitiesFor(LocalImageGenerationModelId.SD15_QNN)

        assertEquals(emptyList<GenerationQuality>(), caps.supportedImageQualities)
        assertEquals(listOf("512x512"), caps.supportedImageResolutions)
    }

    @Test
    fun `SDXL normalizeRequest defaults missing steps to 8`() {
        val normalized =
            LocalImageGenerationModelPolicy.normalizeRequest(
                LocalImageGenerationModelId.SDXL_QNN,
                ImageGenerationSettings(imageResolution = "1024x1024"),
            )

        assertEquals(8, normalized.diffusionSteps)
        assertEquals("lcm", normalized.scheduler)
    }

    @Test
    fun `SD15 normalizeRequest always uses fixed 20 steps`() {
        val normalized =
            LocalImageGenerationModelPolicy.normalizeRequest(
                LocalImageGenerationModelId.SD15_QNN,
                ImageGenerationSettings(imageResolution = "512x512", diffusionSteps = 8),
            )

        assertEquals(20, normalized.diffusionSteps)
    }
}
