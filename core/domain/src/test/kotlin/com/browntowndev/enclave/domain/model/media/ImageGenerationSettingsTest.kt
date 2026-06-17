package com.browntowndev.enclave.domain.model.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImageGenerationSettingsTest {
    @Test
    fun withClampedGenerationCount_belowRange_returnsMinimum() {
        val settings = ImageGenerationSettings(generationCount = 0)

        assertEquals(1, settings.withClampedGenerationCount().generationCount)
    }

    @Test
    fun withClampedGenerationCount_aboveRange_returnsMaximum() {
        val settings = ImageGenerationSettings(generationCount = 11)

        assertEquals(10, settings.withClampedGenerationCount().generationCount)
    }

    @Test
    fun resolvedImg2ImgDenoiseStrength_clampsOutOfRangeValues() {
        val belowMin = ImageGenerationSettings(img2ImgDenoiseStrength = 0.1f)
        val aboveMax = ImageGenerationSettings(img2ImgDenoiseStrength = 1.5f)

        assertEquals(0.25f, belowMin.resolvedImg2ImgDenoiseStrength())
        assertEquals(1.0f, aboveMax.resolvedImg2ImgDenoiseStrength())
    }
}
