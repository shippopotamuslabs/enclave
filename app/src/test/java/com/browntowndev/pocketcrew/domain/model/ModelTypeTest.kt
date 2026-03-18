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
        assertEquals("DRAFT_TWO", ModelType.DRAFT_TWO.name)
        assertEquals("MAIN", ModelType.MAIN.name)
        assertEquals("FAST", ModelType.FAST.name)
        assertEquals("THINKING", ModelType.THINKING.name)
        assertEquals("FINAL_SYNTHESIS", ModelType.FINAL_SYNTHESIS.name)
    }

    @Test
    fun `ModelType has exactly 7 values`() {
        assertEquals(7, ModelType.entries.size)
    }

    @Test
    fun `ModelType fromApiValue returns correct type`() {
        assertEquals(ModelType.VISION, ModelType.fromApiValue("vision"))
        assertEquals(ModelType.DRAFT_ONE, ModelType.fromApiValue("draft_one"))
        assertEquals(ModelType.DRAFT_TWO, ModelType.fromApiValue("draft_two"))
        assertEquals(ModelType.MAIN, ModelType.fromApiValue("main"))
        assertEquals(ModelType.FAST, ModelType.fromApiValue("fast"))
        assertEquals(ModelType.THINKING, ModelType.fromApiValue("thinking"))
        assertEquals(ModelType.FINAL_SYNTHESIS, ModelType.fromApiValue("final_synthesis"))
    }
}

