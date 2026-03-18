package com.browntowndev.pocketcrew.presentation.screen.chat

import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for ChatMessage and ContentUi data structures used in UI rendering.
 * These verify the data model provides what the UI needs for rendering decisions.
 */
class ChatMessageRenderingDataTest {

    // ========================================================================
    // Test: ChatMessage has required fields for indicator rendering
    // Evidence: MessageList checks message.indicatorState for User messages
    // ========================================================================

    @Test
    fun chatMessage_hasIndicatorStateField() {
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.User,
            content = ContentUi(text = "test"),
            formattedTimestamp = "10:00",
            indicatorState = IndicatorState.Processing
        )

        assertNotNull("ChatMessage should have indicatorState field", message.indicatorState)
        assertTrue(message.indicatorState is IndicatorState.Processing)
    }

    @Test
    fun chatMessage_indicatorStateCanBeNull() {
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.User,
            content = ContentUi(text = "test"),
            formattedTimestamp = "10:00",
            indicatorState = null
        )

        assertNull("IndicatorState can be null for completed messages", message.indicatorState)
    }

    // ========================================================================
    // Test: ChatMessage has required fields for pipeline step rendering
    // Evidence: AssistantResponse checks content.pipelineStep for CompletedStepRow
    // ========================================================================

    @Test
    fun chatMessage_hasContentWithPipelineStep() {
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = ContentUi(
                text = "Draft output",
                pipelineStep = PipelineStep.DRAFT_ONE
            ),
            formattedTimestamp = "10:00"
        )

        assertNotNull("Content should have pipelineStep", message.content.pipelineStep)
        assertEquals(PipelineStep.DRAFT_ONE, message.content.pipelineStep)
    }

    @Test
    fun chatMessage_pipelineStepCanBeNull() {
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = ContentUi(
                text = "Regular response",
                pipelineStep = null
            ),
            formattedTimestamp = "10:00"
        )

        assertNull("pipelineStep should be null for regular messages", message.content.pipelineStep)
    }

    @Test
    fun chatMessage_pipelineStepFINALMeansRegularRendering() {
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = ContentUi(
                text = "Final response",
                pipelineStep = PipelineStep.FINAL
            ),
            formattedTimestamp = "10:00"
        )

        // FINAL should render as regular content, not CompletedStepRow
        assertEquals(PipelineStep.FINAL, message.content.pipelineStep)
    }

    // ========================================================================
    // Test: All PipelineStep values are handled
    // Evidence: AssistantResponse checks for DRAFT_ONE, DRAFT_TWO, SYNTHESIS
    // ========================================================================

    @Test
    fun allPipelineSteps_areValid() {
        // All steps should be usable
        assertNotNull(PipelineStep.DRAFT_ONE)
        assertNotNull(PipelineStep.DRAFT_TWO)
        assertNotNull(PipelineStep.SYNTHESIS)
        assertNotNull(PipelineStep.FINAL)
    }

    @Test
    fun pipelineStep_displayName_returnsNonEmpty() {
        // Verify displayName() doesn't crash and returns useful string
        assertTrue(PipelineStep.DRAFT_ONE.displayName().isNotEmpty())
        assertTrue(PipelineStep.DRAFT_TWO.displayName().isNotEmpty())
        assertTrue(PipelineStep.SYNTHESIS.displayName().isNotEmpty())
        assertTrue(PipelineStep.FINAL.displayName().isNotEmpty())
    }

    // ========================================================================
    // Test: IndicatorState has required properties for UI
    // Evidence: MessageList uses indicatorState.when for rendering
    // ========================================================================

    @Test
    fun indicatorState_hasThinkingVariant() {
        val state = IndicatorState.Thinking(
            thinkingSteps = listOf("Step 1", "Step 2"),
            thinkingStartTime = 1000L,
            modelDisplayName = "Test Model"
        )

        assertTrue(state is IndicatorState.Thinking)
        val thinking = state as IndicatorState.Thinking
        assertEquals(2, thinking.thinkingSteps.size)
        assertEquals("Test Model", thinking.modelDisplayName)
    }

    @Test
    fun indicatorState_hasGeneratingVariant_withThinkingData() {
        val thinkingData = ThinkingDataUi(
            thinkingDurationSeconds = 30,
            steps = listOf("Thinking..."),
            modelDisplayName = "Model"
        )
        val state = IndicatorState.Generating(thinkingData = thinkingData)

        assertTrue(state is IndicatorState.Generating)
        assertTrue(state.hasThinkingData)
    }

    @Test
    fun indicatorState_hasGeneratingVariant_withoutThinkingData() {
        val state = IndicatorState.Generating(thinkingData = null)

        assertTrue(state is IndicatorState.Generating)
        assertFalse(state.hasThinkingData)
    }

    @Test
    fun indicatorState_hasCompleteVariant_withThinkingData() {
        val thinkingData = ThinkingDataUi(
            thinkingDurationSeconds = 45,
            steps = listOf("Step 1", "Step 2"),
            modelDisplayName = "Model"
        )
        val state = IndicatorState.Complete(thinkingData = thinkingData)

        assertTrue(state is IndicatorState.Complete)
        assertTrue(state.hasThinkingData)
    }

    @Test
    fun indicatorState_hasCompleteVariant_withoutThinkingData() {
        val state = IndicatorState.Complete(thinkingData = null)

        assertTrue(state is IndicatorState.Complete)
        assertFalse(state.hasThinkingData)
    }

    @Test
    fun indicatorState_hasProcessingVariant() {
        val state = IndicatorState.Processing

        assertTrue(state is IndicatorState.Processing)
    }

    // ========================================================================
    // Test: ContentUi has text field for rendering
    // Evidence: AssistantResponse uses content.text
    // ========================================================================

    @Test
    fun contentUi_hasTextField() {
        val content = ContentUi(
            text = "Hello world",
            pipelineStep = null
        )

        assertEquals("Hello world", content.text)
    }

    @Test
    fun contentUi_textCanBeEmpty() {
        val content = ContentUi(
            text = "",
            pipelineStep = null
        )

        assertEquals("", content.text)
    }
}
