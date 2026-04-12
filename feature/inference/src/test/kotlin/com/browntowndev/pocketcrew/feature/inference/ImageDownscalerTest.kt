package com.browntowndev.pocketcrew.feature.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImageDownscalerTest {

    @Test
    fun `calculateTargetDimensions keeps aspect ratio within 2048 ceiling`() {
        val (width, height) = ImageDownscaler.calculateTargetDimensions(
            width = 4032,
            height = 3024,
            reqWidth = 2048,
            reqHeight = 2048,
        )

        assertEquals(2048, width)
        assertEquals(1536, height)
    }

    @Test
    fun `calculateTargetDimensions leaves already-small images unchanged`() {
        val (width, height) = ImageDownscaler.calculateTargetDimensions(
            width = 1280,
            height = 720,
            reqWidth = 2048,
            reqHeight = 2048,
        )

        assertEquals(1280, width)
        assertEquals(720, height)
    }

    @Test
    fun `calculateInSampleSize uses a coarse but safe decode prefilter`() {
        assertEquals(2, ImageDownscaler.calculateInSampleSize(4032, 3024, 2048, 2048))
        assertEquals(1, ImageDownscaler.calculateInSampleSize(1280, 720, 2048, 2048))
    }
}
