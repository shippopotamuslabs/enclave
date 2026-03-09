package com.browntowndev.pocketcrew.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for ModelType enum.
 */
class ModelTypeTest {

    @Test
    fun `ModelType has correct names`() {
        assertEquals("VISION", ModelType.VISION.name)
        assertEquals("DRAFT", ModelType.DRAFT_ONE.name)
        assertEquals("MAIN", ModelType.MAIN.name)
    }

    @Test
    fun `ModelType has exactly 3 values`() {
        assertEquals(3, ModelType.entries.size)
    }
}

