package com.browntowndev.enclave.domain.usecase.profile

import com.browntowndev.enclave.domain.model.profile.ConversationScoringPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConversationSelectorTest {
    // Simple unit vector embeddings for testing
    private fun vecA() = floatArrayOf(1f, 0f, 0f)

    private fun vecB() = floatArrayOf(0f, 1f, 0f)

    private fun vecC() = floatArrayOf(0f, 0f, 1f)

    private fun vecNearA() = floatArrayOf(0.98f, 0.14f, 0f).normalise()

    private fun FloatArray.normalise(): FloatArray {
        val len = kotlin.math.sqrt(sumOf { it * it.toDouble() }).toFloat()
        return FloatArray(size) { this[it] / len }
    }

    private fun candidate(
        id: String,
        score: Float,
        tokens: Int,
        embedding: FloatArray,
    ) = ConversationSelector.Candidate(id, score, tokens, embedding)

    @Test
    fun `PP-80 empty candidates returns empty selection`() {
        val result = ConversationSelector.select(emptyList(), tokenBudget = 1000)
        assertEquals(emptyList<String>(), result.selectedIds)
        assertEquals(0, result.totalTokens)
    }

    @Test
    fun `PP-81 single eligible candidate within budget is selected`() {
        val c = candidate("a", score = 0.8f, tokens = 200, embedding = vecA())
        val result = ConversationSelector.select(listOf(c), tokenBudget = 500)
        assertEquals(listOf("a"), result.selectedIds)
        assertEquals(200, result.totalTokens)
    }

    @Test
    fun `PP-82 candidate below min eligible score is excluded`() {
        val lowScore = candidate("low", score = ConversationScoringPolicy.MIN_ELIGIBLE_SCORE - 0.01f, tokens = 100, embedding = vecA())
        val highScore = candidate("high", score = 0.9f, tokens = 100, embedding = vecB())
        val result = ConversationSelector.select(listOf(lowScore, highScore), tokenBudget = 500)
        assertTrue("low" !in result.selectedIds)
        assertTrue("high" in result.selectedIds)
    }

    @Test
    fun `PP-83 budget stops selection when exhausted`() {
        val candidates =
            listOf(
                candidate("a", score = 0.9f, tokens = 400, embedding = vecA()),
                candidate("b", score = 0.8f, tokens = 400, embedding = vecB()),
                candidate("c", score = 0.7f, tokens = 400, embedding = vecC()),
            )
        val result = ConversationSelector.select(candidates, tokenBudget = 800)
        assertTrue(result.totalTokens <= 800)
        assertEquals(2, result.selectedIds.size)
    }

    @Test
    fun `PP-84 MMR picks diverse candidates over near-duplicates`() {
        // a and nearA are very similar; b is orthogonal to a
        // Both a and nearA have higher raw score than b — but MMR should still pick b
        // after picking a, because nearA is too similar
        val candidates =
            listOf(
                candidate("a", score = 0.95f, tokens = 100, embedding = vecA()),
                candidate("nearA", score = 0.90f, tokens = 100, embedding = vecNearA()),
                candidate("b", score = 0.70f, tokens = 100, embedding = vecB()),
            )
        val result = ConversationSelector.select(candidates, tokenBudget = 300)
        assertTrue("a" in result.selectedIds, "a should be selected (highest score)")
        // b (diverse) should beat nearA (similar to a) in 2nd pick
        assertTrue("b" in result.selectedIds, "b should be preferred over nearA for diversity")
    }

    @Test
    fun `PP-85 deterministic selection — same inputs same result`() {
        val candidates =
            listOf(
                candidate("a", score = 0.9f, tokens = 200, embedding = vecA()),
                candidate("b", score = 0.7f, tokens = 200, embedding = vecB()),
                candidate("c", score = 0.5f, tokens = 200, embedding = vecC()),
            )
        val r1 = ConversationSelector.select(candidates, 400)
        val r2 = ConversationSelector.select(candidates, 400)
        assertEquals(r1.selectedIds, r2.selectedIds)
    }

    @Test
    fun `PP-86 total tokens in result does not exceed budget`() {
        val candidates =
            (1..20).map { i ->
                candidate("c$i", score = i * 0.04f, tokens = 150, embedding = vecA())
            }
        val budget = 750
        val result = ConversationSelector.select(candidates, budget)
        assertTrue(result.totalTokens <= budget, "tokens=${result.totalTokens} budget=$budget")
    }
}
