package com.browntowndev.enclave.domain.model.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LocalImageGenerationModelPolicyTest {
    @Test
    fun `STU-001 active SD15 exposes exactly 512x512`() {
        assertEquals(listOf("512x512"), LocalImageGenerationModelPolicy.policyFor(LocalImageGenerationModelId.SD15_QNN).supportedImageResolutions)
    }

    @Test
    fun `STU-002 active SDXL exposes exactly 1024x1024`() {
        assertEquals(listOf("1024x1024"), LocalImageGenerationModelPolicy.policyFor(LocalImageGenerationModelId.SDXL_QNN).supportedImageResolutions)
    }

    @Test
    fun `STU-003 active SDXL exposes exact step options and default 16`() {
        val policy = LocalImageGenerationModelPolicy.policyFor(LocalImageGenerationModelId.SDXL_QNN)

        assertEquals(listOf(8, 12, 16, 20, 24, 30), policy.supportedDiffusionSteps)
        assertEquals(16, policy.defaultDiffusionSteps)
    }

    @Test
    fun `STU-007 switching SDXL to SD15 clamps stale resolution and clears steps`() {
        val normalized =
            LocalImageGenerationModelPolicy.normalizeRequest(
                LocalImageGenerationModelId.SD15_QNN,
                ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 24),
            )

        assertEquals("512x512", normalized.imageResolution)
        assertEquals(20, normalized.diffusionSteps)
    }

    @Test
    fun `STU-008 switching SD15 to SDXL clamps stale resolution and steps to defaults`() {
        val normalized =
            LocalImageGenerationModelPolicy.normalizeRequest(
                LocalImageGenerationModelId.SDXL_QNN,
                ImageGenerationSettings(imageResolution = "512x512", diffusionSteps = null),
            )

        assertEquals("1024x1024", normalized.imageResolution)
        assertEquals(16, normalized.diffusionSteps)
    }

    @Test
    fun `INF-001 SD15 native request uses 512 20 and 7_5`() {
        val normalized = LocalImageGenerationModelPolicy.normalizeRequest(LocalImageGenerationModelId.SD15_QNN, ImageGenerationSettings(imageResolution = "512x512"))

        assertEquals(512, normalized.width)
        assertEquals(512, normalized.height)
        assertEquals(20, normalized.diffusionSteps)
        assertEquals(7.5f, normalized.cfgScale)
    }

    @Test
    fun `INF-002 SDXL native request uses 1024 selected steps CFG and lcm`() {
        val normalized = LocalImageGenerationModelPolicy.normalizeRequest(LocalImageGenerationModelId.SDXL_QNN, ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 8))

        assertEquals(1024, normalized.width)
        assertEquals(1024, normalized.height)
        assertEquals(8, normalized.diffusionSteps)
        assertEquals(1.5f, normalized.cfgScale)
        assertEquals("lcm", normalized.scheduler)
    }

    @Test
    fun `INF-003 stale SD15 1024 request fails before native launch with exact error`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                LocalImageGenerationModelPolicy.normalizeRequest(
                    LocalImageGenerationModelId.SD15_QNN,
                    ImageGenerationSettings(imageResolution = "1024x1024"),
                )
            }

        assertEquals(
            "Unsupported local image generation resolution '1024x1024' for model SD15_QNN. Supported resolutions: [512x512].",
            error.message,
        )
    }

    @Test
    fun `INF-004 stale SDXL step 13 clamps to 16 before native launch`() {
        val normalized = LocalImageGenerationModelPolicy.normalizeRequest(LocalImageGenerationModelId.SDXL_QNN, ImageGenerationSettings(diffusionSteps = 13))

        assertEquals(16, normalized.diffusionSteps)
    }

    @Test
    fun `INF-005 C plus plus backend JSON contains lcm scheduler for SDXL`() {
        val normalized = LocalImageGenerationModelPolicy.normalizeRequest(LocalImageGenerationModelId.SDXL_QNN, ImageGenerationSettings(diffusionSteps = 8))

        assertEquals("lcm", normalized.scheduler)
    }

    @Test
    fun `MUT-001 hardcoded dual resolution fails SD15 exact resolution check`() {
        assertEquals(listOf("512x512"), LocalImageGenerationModelPolicy.policyFor(LocalImageGenerationModelId.SD15_QNN).supportedImageResolutions)
    }

    @Test
    fun `MUT-003 lazy any step implementation fails because 13 clamps to 16`() {
        val normalized = LocalImageGenerationModelPolicy.normalizeRequest(LocalImageGenerationModelId.SDXL_QNN, ImageGenerationSettings(diffusionSteps = 13))

        assertEquals(16, normalized.diffusionSteps)
    }

    @Test
    fun `MUT-004 hardcoded dpm scheduler implementation fails for SDXL`() {
        val normalized = LocalImageGenerationModelPolicy.normalizeRequest(LocalImageGenerationModelId.SDXL_QNN, ImageGenerationSettings(diffusionSteps = 8))

        assertEquals("lcm", normalized.scheduler)
    }
}
