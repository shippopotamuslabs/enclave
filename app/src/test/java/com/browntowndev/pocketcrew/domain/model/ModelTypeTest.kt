package com.browntowndev.pocketcrew.domain.model.inference

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for ModelType enum.
 */
class ModelTypeTest {

    @Test
    fun `ModelType has correct names`() {
        assertEquals("VISION", ModelType.VISION.name)
        assertEquals("DRAFT_ONE", ModelType.DRAFT_ONE.name)
        assertEquals("MAIN", ModelType.MAIN.name)
        assertEquals("THINKING", ModelType.THINKING.name)
    }

    @Test
    fun `ModelType has exactly 6 values`() {
        assertEquals(6, ModelType.entries.size)
    }

    @Test
    fun `ModelType fromApiValue returns correct type`() {
        assertEquals(ModelType.VISION, ModelType.fromApiValue("vision"))
        assertEquals(ModelType.DRAFT_ONE, ModelType.fromApiValue("draft_one"))
        assertEquals(ModelType.DRAFT_TWO, ModelType.fromApiValue("draft_two"))
        assertEquals(ModelType.MAIN, ModelType.fromApiValue("main"))
        assertEquals(ModelType.FAST, ModelType.fromApiValue("fast"))
        assertEquals(ModelType.THINKING, ModelType.fromApiValue("thinking"))
    }
}

