package com.browntowndev.pocketcrew.domain.usecase.chat

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for ProcessThinkingTokensUseCase to verify whitespace preservation.
 * These tests expose bugs where whitespace is incorrectly removed.
 */
class ProcessThinkingTokensUseCaseWhitespaceTest {

    private lateinit var useCase: ProcessThinkingTokensUseCase

    @BeforeEach
    fun setup() {
        useCase = ProcessThinkingTokensUseCase()
    }

    /**
     * Test that numbers in thinking text don't lose their trailing spaces.
     * Bug: "2018 paper" was becoming "2018paper"
     */
    @Test
    fun `should preserve space after numbers in thinking`() {
        val state = useCase("", "<think>I will cite this 2018 paper and 2020 study</think>", false)

        val thinkingText = state.thinkingTextToEmit

        // Should preserve space after "2018" and "2020"
        assertFalse(
            thinkingText.contains("2018paper"),
            "Should preserve space after 2018, got: $thinkingText"
        )
        assertFalse(
            thinkingText.contains("2020study"),
            "Should preserve space after 2020, got: $thinkingText"
        )
    }

    /**
     * Test that spaces around numbered lists are preserved
     */
    @Test
    fun `should preserve space in numbered lists`() {
        val state = useCase("", "<think>1. First item\n2. Second item</think>", false)

        val thinkingText = state.thinkingTextToEmit

        // Should preserve "1. First item" with space after period
        assertTrue(
            thinkingText.contains("1. First"),
            "Should preserve list format, got: $thinkingText"
        )
        assertFalse(
            thinkingText.contains("1.First"),
            "Should preserve space after list marker, got: $thinkingText"
        )
    }

    /**
     * Test that regular text with numbers is preserved
     */
    @Test
    fun `should preserve text with numbers`() {
        val state = useCase("", "<think>The year is 2024 and counting.</think>", false)

        val thinkingText = state.thinkingTextToEmit

        assertFalse(
            thinkingText.contains("2024and"),
            "Should preserve space after year, got: $thinkingText"
        )
    }
}
