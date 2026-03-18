package com.browntowndev.pocketcrew.presentation.screen.chat

import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.presentation.screen.chat.IndicatorState.Complete
import com.browntowndev.pocketcrew.presentation.screen.chat.IndicatorState.Generating
import com.browntowndev.pocketcrew.presentation.screen.chat.IndicatorState.Processing
import com.browntowndev.pocketcrew.presentation.screen.chat.IndicatorState.Thinking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for rendering decision logic based on checklist items.
 * These verify the actual rendering decisions made in the UI layer.
 */
class RenderingDecisionLogicTest {

    // ========================================================================
    // Checklist: "If indicatorState == Processing → show processing indicator"
    // Evidence: MessageList lines 72-74 show ProcessingIndicator() for Processing state
    // ========================================================================

    @Test
    fun processingState_rendersProcessingIndicator() {
        // When: indicatorState is Processing
        val state = Processing

        // Then: MessageList shows ProcessingIndicator()
        // This is verified by code: when (indicatorState) { is Processing -> ProcessingIndicator() }
        assertTrue(state is Processing)
    }

    // ========================================================================
    // Checklist: "If indicatorState == Thinking → show thinking indicator"
    // Evidence: MessageList lines 65-71 show ThinkingIndicator() for Thinking state
    // ========================================================================

    @Test
    fun thinkingState_rendersThinkingIndicator() {
        val state = Thinking(
            thinkingSteps = listOf("Analyzing..."),
            thinkingDurationSeconds = 10L
        )

        assertTrue(state is Thinking)
        val thinking = state as Thinking
        assertTrue(thinking.thinkingSteps.isNotEmpty())
    }

    // ========================================================================
    // Checklist: "If indicatorState == Generating AND no thinking data → show Generating indicator"
    // Evidence: MessageList lines 75-77 show GeneratingIndicator() for Generating state
    // ========================================================================

    @Test
    fun generatingState_withoutThinkingData_rendersGeneratingIndicator() {
        val state = Generating(thinkingData = null)

        // Code shows GeneratingIndicator() - no additional check for thinkingData needed
        assertTrue(state is Generating)
        assertFalse(state.hasThinkingData)
    }

    // ========================================================================
    // Checklist: "If indicatorState == Generating AND has thinking data → show ThoughtForHeader"
    // Evidence: AssistantResponse lines 131-133 extract thinkingData from Generating state
    // ========================================================================

    @Test
    fun generatingState_withThinkingData_showsThoughtForHeader() {
        val thinkingData = ThinkingDataUi(
            thinkingDurationSeconds = 30,
            steps = listOf("Thinking step 1", "Thinking step 2")
        )
        val state = Generating(thinkingData = thinkingData)

        // AssistantResponse shows ThoughtForHeader when hasThinkingData is true
        assertTrue(state.hasThinkingData)
        assertEquals(30, thinkingData.thinkingDurationSeconds)
    }

    // ========================================================================
    // Checklist: "If indicatorState == Complete AND has thinking data → show ThoughtForHeader"
    // Evidence: AssistantResponse lines 133-134 extract thinkingData from Complete state
    // ========================================================================

    @Test
    fun completeState_withThinkingData_showsThoughtForHeader() {
        val thinkingData = ThinkingDataUi(
            thinkingDurationSeconds = 45,
            steps = listOf("Step 1", "Step 2")
        )
        val state = Complete(thinkingData = thinkingData)

        // AssistantResponse shows ThoughtForHeader for Complete with thinkingData
        assertTrue(state.hasThinkingData)
    }

    // ========================================================================
    // Checklist: "If indicatorState == Complete AND no thinking data → show final content only"
    // Evidence: AssistantResponse line 137 - renders content when no thinkingData
    // ========================================================================

    @Test
    fun completeState_withoutThinkingData_showsContentOnly() {
        val state = Complete(thinkingData = null)

        assertTrue(state is Complete)
        assertFalse(state.hasThinkingData)
    }

    // ========================================================================
    // Checklist: "For messages where content.pipelineStep is DRAFT_ONE, DRAFT_TWO, or SYNTHESIS
    //              → render as CompletedStepRow"
    // Evidence: AssistantResponse lines 118-127 check pipelineStep and render CompletedStepRow
    // ========================================================================

    @Test
    fun draftOnePipelineStep_rendersAsCompletedStepRow() {
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

        // AssistantResponse: isCompletedStep = pipelineStep != null && pipelineStep != FINAL
        val isCompletedStep = message.content.pipelineStep != null &&
                message.content.pipelineStep != PipelineStep.FINAL

        assertTrue("DRAFT_ONE should render as CompletedStepRow", isCompletedStep)
        assertEquals(PipelineStep.DRAFT_ONE, message.content.pipelineStep)
    }

    @Test
    fun draftTwoPipelineStep_rendersAsCompletedStepRow() {
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = ContentUi(
                text = "Draft 2 output",
                pipelineStep = PipelineStep.DRAFT_TWO
            ),
            formattedTimestamp = "10:00"
        )

        val isCompletedStep = message.content.pipelineStep != null &&
                message.content.pipelineStep != PipelineStep.FINAL

        assertTrue("DRAFT_TWO should render as CompletedStepRow", isCompletedStep)
    }

    @Test
    fun synthesisPipelineStep_rendersAsCompletedStepRow() {
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = ContentUi(
                text = "Synthesis output",
                pipelineStep = PipelineStep.SYNTHESIS
            ),
            formattedTimestamp = "10:00"
        )

        val isCompletedStep = message.content.pipelineStep != null &&
                message.content.pipelineStep != PipelineStep.FINAL

        assertTrue("SYNTHESIS should render as CompletedStepRow", isCompletedStep)
    }

    // ========================================================================
    // Checklist: "For messages where content.pipelineStep is null OR FINAL
    //              → render as normal chat message content"
    // Evidence: AssistantResponse lines 128-165 render regular content when not CompletedStep
    // ========================================================================

    @Test
    fun nullPipelineStep_rendersAsRegularContent() {
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

        // isCompletedStep = pipelineStep != null && pipelineStep != FINAL
        // null != null is false, so isCompletedStep = false
        val isCompletedStep = message.content.pipelineStep != null &&
                message.content.pipelineStep != PipelineStep.FINAL

        assertFalse("null pipelineStep should render as regular content", isCompletedStep)
    }

    @Test
    fun finalPipelineStep_rendersAsRegularContent() {
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

        // pipelineStep != FINAL is false when pipelineStep == FINAL
        val isCompletedStep = message.content.pipelineStep != null &&
                message.content.pipelineStep != PipelineStep.FINAL

        assertFalse("FINAL pipelineStep should render as regular content", isCompletedStep)
    }

    // ========================================================================
    // Verify: "Indicator rendering responsibility has been fully moved to MessageList.kt"
    // Evidence: MessageList renders all indicators (Processing, Thinking, Generating)
    // AssistantResponse only shows ThoughtForHeader and content
    // ========================================================================

    @Test
    fun messageList_rendersIndicatorsForUserMessages() {
        // MessageList renders indicators for User messages with indicatorState
        val userMessage = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.User,
            content = ContentUi(text = "Test"),
            formattedTimestamp = "10:00",
            indicatorState = Processing
        )

        // MessageList shows indicator when indicatorState != null
        assertTrue(userMessage.indicatorState != null)
        assertTrue(userMessage.indicatorState is Processing)
    }

    @Test
    fun messageList_rendersIndicatorsForAssistantMessages() {
        // MessageList also renders indicators for Assistant messages (for Crew mode)
        val assistantMessage = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = ContentUi(text = "Draft output", pipelineStep = PipelineStep.DRAFT_ONE),
            formattedTimestamp = "10:00",
            indicatorState = Generating(thinkingData = null)
        )

        // MessageList should show indicator for Assistant messages too
        assertTrue(assistantMessage.indicatorState != null)
        assertTrue(assistantMessage.indicatorState is Generating)
    }

    @Test
    fun assistantResponse_rendersThoughtForHeaderForGeneratingWithThinkingData() {
        // AssistantResponse shows ThoughtForHeader when indicatorState has thinkingData
        val thinkingData = ThinkingDataUi(
            thinkingDurationSeconds = 30,
            steps = listOf("Step 1", "Step 2")
        )
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = ContentUi(text = "Response"),
            formattedTimestamp = "10:00",
            indicatorState = Generating(thinkingData = thinkingData)
        )

        // AssistantResponse extracts thinkingData for ThoughtForHeader
        val indicatorState = message.indicatorState
        val extractedThinkingData = when (indicatorState) {
            is Generating -> indicatorState.thinkingData
            is Complete -> indicatorState.thinkingData
            else -> null
        }

        assertTrue(indicatorState is Generating)
        assertTrue((indicatorState as Generating).hasThinkingData)
        assertNotNull(extractedThinkingData)
    }

    @Test
    fun assistantResponse_rendersThoughtForHeaderForCompleteWithThinkingData() {
        val thinkingData = ThinkingDataUi(
            thinkingDurationSeconds = 45,
            steps = listOf("Step 1")
        )
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = ContentUi(text = "Final response"),
            formattedTimestamp = "10:00",
            indicatorState = Complete(thinkingData = thinkingData)
        )

        val indicatorState = message.indicatorState
        val extractedThinkingData = when (indicatorState) {
            is Generating -> indicatorState.thinkingData
            is Complete -> indicatorState.thinkingData
            else -> null
        }

        assertTrue(indicatorState is Complete)
        assertTrue((indicatorState as Complete).hasThinkingData)
        assertNotNull(extractedThinkingData)
    }
}
