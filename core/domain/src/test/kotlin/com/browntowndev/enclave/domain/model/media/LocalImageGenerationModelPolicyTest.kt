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
    fun `SD15 CPU policy mirrors SD15 QNN generation defaults`() {
        val normalized =
            LocalImageGenerationModelPolicy.normalizeRequest(
                LocalImageGenerationModelId.SD15_CPU,
                ImageGenerationSettings(imageResolution = "512x512", diffusionSteps = 8),
            )

        assertEquals(20, normalized.diffusionSteps)
        assertEquals("dpm", normalized.scheduler)
        assertEquals(7.5f, normalized.cfgScale)
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

    @Test
    fun `STU-009 Juggernaut provider capabilities expose exactly 1024x1024`() {
        val caps = LocalImageGenerationModelPolicy.providerCapabilitiesFor(LocalImageGenerationModelId.JUGGERNAUT_XL_QNN)

        assertEquals(listOf("1024x1024"), caps.supportedImageResolutions)
    }

    @Test
    fun `STU-010 Juggernaut provider capabilities expose exact step ladder with 24 default`() {
        val caps = LocalImageGenerationModelPolicy.providerCapabilitiesFor(LocalImageGenerationModelId.JUGGERNAUT_XL_QNN)

        assertEquals(listOf(20, 22, 24, 26, 28, 30), caps.supportedImageSteps)
        assertEquals(24, caps.defaultImageStep)
    }

    @Test
    fun `INF-007 stale Juggernaut step 23 clamps to 24`() {
        val normalized =
            LocalImageGenerationModelPolicy.normalizeRequest(
                LocalImageGenerationModelId.JUGGERNAUT_XL_QNN,
                ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 23),
            )

        assertEquals(24, normalized.diffusionSteps)
        assertEquals("dpm", normalized.scheduler)
        assertEquals(4.0f, normalized.cfgScale)
    }

    @Test
    fun `Juggernaut img2img uses lower default denoise strength than SDXL`() {
        val settings = ImageGenerationSettings(referenceImageUri = "content://image")
        val denoise =
            LocalImageGenerationModelPolicy.resolveImg2ImgDenoiseStrength(
                LocalImageGenerationModelId.JUGGERNAUT_XL_QNN,
                settings,
            )

        assertEquals(0.35f, denoise)
    }
}
