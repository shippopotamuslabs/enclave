package com.browntowndev.enclave.domain.usecase.profile

import com.browntowndev.enclave.domain.model.profile.ConversationScoringPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConversationScorerTest {
    private val now = System.currentTimeMillis()
    private val weekAgo = now - 7 * 24 * 3600 * 1000L

    private fun score(
        userText: String = "",
        title: String = "Chat",
        userTokenCount: Int = 100,
        createdAtMs: Long? = now,
        oldestMs: Long = weekAgo,
        newestMs: Long = now,
    ) = ConversationScorer.score(userText, title, userTokenCount, createdAtMs, oldestMs, newestMs)

    // --- Volume ---

    @Test
    fun `PP-60 trivially short conversation scores much lower than sweet-spot length`() {
        val shortScore = score(userTokenCount = 10)
        val sweetSpotScore = score(userTokenCount = ConversationScoringPolicy.VOLUME_SWEET_SPOT_TOKENS)
        assertTrue(sweetSpotScore > shortScore + 0.10f, "sweet=$sweetSpotScore short=$shortScore")
    }

    @Test
    fun `PP-61 conversation above max tokens gets capped penalty not zero`() {
        val cappedScore = score(userTokenCount = 5000)
        assertTrue(cappedScore > 0f)
    }

    // --- First-person density ---

    @Test
    fun `PP-62 high first-person density raises score`() {
        val highFp = score(userText = "I feel that I should decide. I believe my choice is right. I am worried.")
        val lowFp = score(userText = "The project requires migration of data from legacy system to cloud.")
        assertTrue(highFp > lowFp, "highFp=$highFp lowFp=$lowFp")
    }

    // --- Affect lexicon ---

    @Test
    fun `PP-63 affect-rich text scores higher than neutral`() {
        val affect = score(userText = "I feel so anxious and worried. I love the growth but fear the change.")
        val neutral = score(userText = "Please convert this CSV to JSON format with the following columns.")
        assertTrue(affect > neutral, "affect=$affect neutral=$neutral")
    }

    // --- Stance markers ---

    @Test
    fun `PP-64 stance markers raise score`() {
        val withStance =
            score(userText = "I think this approach is wrong. Actually, I believe there is a better way. I disagree with the premise.")
        val noStance = score(userText = "Generate a list of 10 random words in English.")
        assertTrue(withStance > noStance, "stance=$withStance noStance=$noStance")
    }

    // --- Noise penalty ---

    @Test
    fun `PP-65 code-heavy conversation scores lower than reflective conversation`() {
        val codeHeavy =
            score(
                userText =
                    """
                    ```python
                    def factorial(n):
                        if n <= 1: return 1
                        return n * factorial(n-1)
                    ```
                    Can you explain this? Also ```import sys; print(sys.version)```
                    """.trimIndent(),
                userTokenCount = 200,
            )
        val reflective =
            score(
                userText = "I've been thinking about my relationship with work. I feel like I'm always anxious and I wonder if this is healthy.",
                userTokenCount = 200,
            )
        assertTrue(reflective > codeHeavy, "reflective=$reflective codeHeavy=$codeHeavy")
    }

    // --- Title signal ---

    @Test
    fun `PP-66 reflective title scores higher than utilitarian title`() {
        val reflective = score(title = "Why do I feel anxious about my career decisions?")
        val utilitarian = score(title = "fix python regex bug")
        assertTrue(reflective > utilitarian, "reflective=$reflective utilitarian=$utilitarian")
    }

    // --- Recency ---

    @Test
    fun `PP-67 newer conversation scores higher than older one all else equal`() {
        val recent = score(createdAtMs = now, oldestMs = weekAgo, newestMs = now)
        val old = score(createdAtMs = weekAgo, oldestMs = weekAgo, newestMs = now)
        assertTrue(recent > old, "recent=$recent old=$old")
    }

    @Test
    fun `PP-68 null timestamp gets middle recency score`() {
        val withNull = score(createdAtMs = null)
        // Should not be 0 or 1 — should be ~0.5 recency contribution
        assertTrue(withNull > 0f)
    }

    // --- Output bounds ---

    @Test
    fun `PP-69 score always in 0-1 range`() {
        val extremeHigh =
            score(
                userText = "I I I I I feel worried sad anxious frustrated. I think I believe I disagree.",
                title = "why do I feel this way about my life?",
                userTokenCount = ConversationScoringPolicy.VOLUME_SWEET_SPOT_TOKENS,
                createdAtMs = now,
            )
        val extremeLow =
            score(
                userText = "```\n" + "x".repeat(2000) + "\n```",
                title = "fix sql query",
                userTokenCount = 5,
                createdAtMs = weekAgo,
            )
        assertTrue(extremeHigh <= 1f && extremeHigh >= 0f)
        assertTrue(extremeLow <= 1f && extremeLow >= 0f)
    }

    @Test
    fun `PP-70 deterministic — same inputs produce same score`() {
        val text = "I think I should change careers. I feel deeply uncertain about my future."
        val s1 = score(userText = text, userTokenCount = 150)
        val s2 = score(userText = text, userTokenCount = 150)
        assertEquals(s1, s2)
    }
}
