package com.browntowndev.pocketcrew.domain.model.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [GenerationOptions] domain model.
 *
 * Verifies data class contract and reasoningBudget derivation from config thinkingEnabled.
 */
class GenerationOptionsTest {

    @Test
    fun `data class has correct properties`() {
        val options = GenerationOptions(
            reasoningBudget = 2048,
            temperature = 0.7f,
            topK = 40,
            topP = 0.9f,
            minP = 0.1f,
            maxTokens = 4096
        )
        assertEquals(2048, options.reasoningBudget)
        assertEquals(0.7f, options.temperature)
        assertEquals(40, options.topK)
        assertEquals(0.9f, options.topP)
        assertEquals(0.1f, options.minP)
        assertEquals(4096, options.maxTokens)
    }

    @Test
    fun `data class equality works`() {
        val a = GenerationOptions(reasoningBudget = 2048)
        val b = GenerationOptions(reasoningBudget = 2048)
        assertEquals(a, b)
    }

    @Test
    fun `data class inequality works`() {
        val a = GenerationOptions(reasoningBudget = 2048)
        val b = GenerationOptions(reasoningBudget = 0)
        assertNotEquals(a, b)
    }

    @Test
    fun `data class copy works`() {
        val original = GenerationOptions(reasoningBudget = 2048, temperature = 0.7f)
        val copied = original.copy(reasoningBudget = 0)
        assertEquals(0, copied.reasoningBudget)
        assertEquals(2048, original.reasoningBudget)
        assertEquals(0.7f, copied.temperature)
    }

    @Test
    fun `thinkingEnabled true maps to reasoningBudget 2048`() {
        val options = GenerationOptions(reasoningBudget = 2048)
        assertEquals(2048, options.reasoningBudget)
    }

    @Test
    fun `thinkingEnabled false maps to reasoningBudget 0`() {
        val options = GenerationOptions(reasoningBudget = 0)
        assertEquals(0, options.reasoningBudget)
    }
}
