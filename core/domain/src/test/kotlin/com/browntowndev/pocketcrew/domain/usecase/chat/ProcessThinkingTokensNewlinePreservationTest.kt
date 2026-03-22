package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for ProcessThinkingTokensUseCase - newline preservation behavior.
 *
 * These tests verify that all newlines are preserved exactly as output by the LLM,
 * with no trimming on text transitions. The implementation should trust the LLM's
 * formatting without applying any conditional logic for markdown detection.
 *
 * Gherkin scenarios from: plans/2026-03-22-markdown_output_testing-v2.md
 */
class ProcessThinkingTokensNewlinePreservationTest {

    private lateinit var useCase: ProcessThinkingTokensUseCase

    @BeforeEach
    fun setup() {
        useCase = ProcessThinkingTokensUseCase()
    }

    // =========================================================================
    // Markdown Header After Thinking - Gherkin Scenario
    // =========================================================================

    @Nested
    @DisplayName("Feature: Preserve All Newlines in Visible Text")
    inner class MarkdownHeaderTests {

        @Test
        @DisplayName("Scenario: Preserve newlines before markdown headers")
        fun shouldPreserveNewlinesBeforeMarkdownHeaderAfterThinking() {
            val input = "<think>A</think>\n## Introduction"
            val state = useCase("", input, false)
            assertEquals("\n## Introduction", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("Scenario: Preserve newlines between paragraphs")
        fun shouldPreserveNewlinesBetweenParagraphs() {
            val input = "First paragraph.\n\nSecond paragraph."
            val state = useCase("", input, false)
            assertEquals("First paragraph.\n\nSecond paragraph.", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("Scenario: Preserve newlines before bullet lists")
        fun shouldPreserveNewlineBeforeBulletListAfterThinking() {
            val input = "<think>reasoning\n</think>\n- First item"
            val state = useCase("", input, false)
            assertEquals("\n- First item", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("Scenario: Preserve newlines before numbered lists")
        fun shouldPreserveNewlineBeforeNumberedListAfterThinking() {
            val input = "<think>reasoning\n</think>\n1. First step"
            val state = useCase("", input, false)
            assertEquals("\n1. First step", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("Scenario: Preserve newlines before code blocks")
        fun shouldPreserveNewlineBeforeCodeBlockAfterThinking() {
            val input = "<think>reasoning\n</think>\n```kotlin"
            val state = useCase("", input, false)
            assertEquals("\n```kotlin", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("Scenario: Preserve newlines in inline text transitions")
        fun shouldPreserveNewlineInPlainTextTransition() {
            val input = "<think>reasoning\n</think>\nHello world"
            val state = useCase("", input, false)
            assertEquals("\nHello world", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("Scenario: Handle thinking block with no trailing newline")
        fun shouldHandleNoNewlineBetweenThinkingAndVisible() {
            val input = "<think>reasoning</think>Hello"
            val state = useCase("", input, false)
            assertEquals("Hello", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("Scenario: Handle consecutive thinking-visible transitions")
        fun shouldPreserveNewlinesInConsecutiveTransitions() {
            // First <think> emits "A" as thinking, visible "\nText1"
            // Second <think> emits "B" as thinking, visible "Text2"
            val input = "<think>A</think>\nText1<think>B</think>Text2"
            val state = useCase("", input, false)
            // No newline between Text1 and Text2
            assertEquals("\nText1Text2", state.visibleTextToEmit)
        }
    }

    // =========================================================================
    // Test Data Matrix - Comprehensive Coverage
    // =========================================================================

    @Nested
    @DisplayName("Test Data Matrix Coverage")
    inner class TestDataMatrixTests {

        @Test
        @DisplayName("<think>A</think>\\## Header -> \\n## Header (Markdown header after thinking)")
        fun markdownHeaderAfterThinkingPreservesNewline() {
            val input = "<think>A</think>\n## Header"
            val state = useCase("", input, false)
            assertEquals("\n## Header", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("<think>A</think>\\n- Item -> \\n- Item (Bullet list after thinking)")
        fun bulletListAfterThinkingPreservesNewline() {
            val input = "<think>A</think>\n- Item"
            val state = useCase("", input, false)
            assertEquals("\n- Item", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("<think>A</think>\\n1. Item -> \\n1. Item (Numbered list after thinking)")
        fun numberedListAfterThinkingPreservesNewline() {
            val input = "<think>A</think>\n1. Item"
            val state = useCase("", input, false)
            assertEquals("\n1. Item", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("<think>A</think>\\n```code``` -> \\n```code``` (Code block after thinking)")
        fun codeBlockAfterThinkingPreservesNewline() {
            val input = "<think>A</think>\n```code```"
            val state = useCase("", input, false)
            assertEquals("\n```code```", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("<think>A</think>\\nPlain -> \\nPlain (Plain text after thinking)")
        fun plainTextAfterThinkingPreservesNewline() {
            val input = "<think>A</think>\nPlain"
            val state = useCase("", input, false)
            assertEquals("\nPlain", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("<think>A</think>Plain -> Plain (No newline as-is)")
        fun noNewlineBetweenThinkingAndVisibleOutputsAsIs() {
            val input = "<think>A</think>Plain"
            val state = useCase("", input, false)
            assertEquals("Plain", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("<think>A</think>\\n\\nText -> \\n\\nText (Multiple newlines preserved)")
        fun multipleNewlinesAfterThinkingArePreserved() {
            val input = "<think>A</think>\n\nText"
            val state = useCase("", input, false)
            assertEquals("\n\nText", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("<think>A</think>A</think>\\nText -> A\\nText (Consecutive thinking blocks)")
        fun consecutiveThinkingBlocksPreserveNewlines() {
            // First <think> emits "A" as thinking, "A" after first </think> is visible
            // Second </think> emits "A\nText" as visible
            val input = "<think>A</think>A</think>\nText"
            val state = useCase("", input, false)
            assertEquals("A\nText", state.visibleTextToEmit)
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("Empty thinking block <think></think> emits nothing")
        fun emptyThinkingBlockEmitsNothing() {
            val input = "<think></think>"
            val state = useCase("", input, false)
            assertEquals("", state.thinkingTextToEmit)
            assertEquals("", state.visibleTextToEmit)
            assertEquals(false, state.isThinking)
        }

        @Test
        @DisplayName("Whitespace-only thinking preserved as-is")
        fun whitespaceOnlyThinkingIsPreserved() {
            val input = "<think>   \n  </think>"
            val state = useCase("", input, false)
            assertEquals("   \n  ", state.thinkingTextToEmit)
        }

        @Test
        @DisplayName("Multiple markdown headers with newlines preserved")
        fun multipleMarkdownHeadersPreserveNewlines() {
            val input = "<think>think</think>\n## Header1\n\n## Header2"
            val state = useCase("", input, false)
            assertEquals("\n## Header1\n\n## Header2", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("Thinking block ending with newline then visible content")
        fun thinkingEndingWithNewlinePreservesIt() {
            val input = "<think>think\n</think>\nvisible"
            val state = useCase("", input, false)
            assertEquals("think\n", state.thinkingTextToEmit)
            assertEquals("\nvisible", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("Multiple thinking blocks with different newline patterns")
        fun multipleThinkingBlocksWithVariedNewlines() {
            val input = "<think>A</think>\nText1<think>B</think>Text2\nText3<think>C</think>\nText4"
            val state = useCase("", input, false)
            assertEquals("\nText1Text2\nText3\nText4", state.visibleTextToEmit)
        }
    }

    // =========================================================================
    // [THINK] / [/THINK] Variant Tests
    // =========================================================================

    @Nested
    @DisplayName("[THINK] Variant Tests - Newline Preservation")
    inner class BracketVariantNewlineTests {

        @Test
        @DisplayName("Newline before markdown header with [THINK] variant")
        fun newlineBeforeHeaderWithBracketVariant() {
            val input = "[THINK]A[/THINK]\n## Header"
            val state = useCase("", input, false)
            assertEquals("\n## Header", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("Newline before bullet list with [THINK] variant")
        fun newlineBeforeBulletListWithBracketVariant() {
            val input = "[THINK]thinking[/THINK]\n- Item"
            val state = useCase("", input, false)
            assertEquals("\n- Item", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("No newline with [THINK] variant outputs as-is")
        fun noNewlineWithBracketVariantOutputsAsIs() {
            val input = "[THINK]thinking[/THINK]visible"
            val state = useCase("", input, false)
            assertEquals("visible", state.visibleTextToEmit)
        }

        @Test
        @DisplayName("Consecutive [THINK] blocks with newlines")
        fun consecutiveBracketThinkingBlocksPreserveNewlines() {
            val input = "[THINK]A[/THINK]\nText1[THINK]B[/THINK]Text2"
            val state = useCase("", input, false)
            assertEquals("\nText1Text2", state.visibleTextToEmit)
        }
    }
}
