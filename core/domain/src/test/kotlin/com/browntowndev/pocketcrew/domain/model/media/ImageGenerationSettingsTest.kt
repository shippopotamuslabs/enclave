package com.browntowndev.pocketcrew.domain.model.media

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
}
