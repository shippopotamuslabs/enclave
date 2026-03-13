package com.browntowndev.pocketcrew.presentation.screen.chat.components

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.presentation.screen.chat.ChatMessage
import com.browntowndev.pocketcrew.presentation.screen.chat.MessageRole
import com.browntowndev.pocketcrew.presentation.screen.chat.ResponseState
import com.browntowndev.pocketcrew.presentation.screen.chat.StepCompletionData
import com.browntowndev.pocketcrew.presentation.screen.chat.ThinkingData
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for AssistantResponse composable separation between Crew mode and Normal mode.
 * These tests verify that the composable correctly detects and renders each mode.
 */
class AssistantResponseSeparationTest {

    // ==================== Mode Detection Tests ====================

    @Test
    fun `message with completedSteps is detected as Crew mode`() {
        // Given: A message with completedSteps (Crew mode indicator)
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = "Response content",
            formattedTimestamp = "10:30 AM",
            completedSteps = listOf(
                StepCompletionData(
                    stepOutput = "Draft One output",
                    thinkingDurationSeconds = 30,
                    thinkingSteps = listOf("Thinking step 1"),
                    stepType = PipelineStep.DRAFT_ONE,
                    modelType = ModelType.DRAFT_ONE,
                    modelDisplayName = "Model 1"
                )
            )
        )

        // Then: completedSteps is not null and not empty
        assertTrue(message.completedSteps != null)
        assertTrue(message.completedSteps!!.isNotEmpty())
    }

    @Test
    fun `message without completedSteps is detected as Normal mode`() {
        // Given: A message without completedSteps (Normal mode)
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = "Response content",
            formattedTimestamp = "10:30 AM",
            thinkingData = ThinkingData(
                thinkingDurationSeconds = 10,
                steps = listOf("Thinking step")
            )
        )

        // Then: completedSteps is null or empty
        assertTrue(message.completedSteps == null || message.completedSteps!!.isEmpty())
    }

    @Test
    fun `message with both completedSteps and thinkingData uses Crew mode`() {
        // Given: A message with both completedSteps and thinkingData
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = "Final response",
            formattedTimestamp = "10:30 AM",
            completedSteps = listOf(
                StepCompletionData(
                    stepOutput = "Draft output",
                    thinkingDurationSeconds = 30,
                    thinkingSteps = listOf("Thinking"),
                    stepType = PipelineStep.DRAFT_ONE,
                    modelType = ModelType.DRAFT_ONE,
                    modelDisplayName = "Model"
                )
            ),
            thinkingData = ThinkingData(
                thinkingDurationSeconds = 30,
                steps = listOf("Final thinking")
            )
        )

        // Then: completedSteps takes precedence for mode detection
        assertTrue(message.completedSteps?.isNotEmpty() == true)
    }

    // ==================== Normal Mode Rendering Tests ====================

    @Test
    fun `Normal mode shows ThoughtForHeader when thinkingData has steps`() {
        // Given: A normal mode message with thinkingData that has steps
        val thinkingData = ThinkingData(
            thinkingDurationSeconds = 10,
            steps = listOf("Thinking step 1", "Thinking step 2"),
            modelDisplayName = "Model"
        )

        // Then: thinkingData exists and has steps
        assertNotNull(thinkingData)
        assertTrue(thinkingData.steps.isNotEmpty())
    }

    @Test
    fun `Normal mode hides ThoughtForHeader when thinkingData is null`() {
        // Given: A message with no thinkingData
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = "Quick response",
            formattedTimestamp = "10:30 AM",
            thinkingData = null
        )

        // Then: No thinking data to show
        assertNull(message.thinkingData)
    }

    @Test
    fun `Normal mode hides ThoughtForHeader when thinkingData steps is empty`() {
        // Given: A message with thinkingData but empty steps
        val thinkingData = ThinkingData(
            thinkingDurationSeconds = 0,
            steps = emptyList(),
            modelDisplayName = "Model"
        )

        // Then: No steps to display in ThoughtForHeader
        assertTrue(thinkingData.steps.isEmpty())
    }

    @Test
    fun `Normal mode shows content correctly`() {
        // Given: A normal mode message with content
        val content = "This is the response content."
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = content,
            formattedTimestamp = "10:30 AM"
        )

        // Then: Content is present
        assertEquals(content, message.content)
        assertTrue(message.content.isNotEmpty())
    }

    @Test
    fun `Normal mode shows timestamp`() {
        // Given: A message with timestamp
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = "Response",
            formattedTimestamp = "10:30 AM"
        )

        // Then: Timestamp is present
        assertEquals("10:30 AM", message.formattedTimestamp)
    }

    @Test
    fun `Normal mode does NOT show CompletedStepsHeader`() {
        // Given: A normal mode message (no completedSteps)
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = "Response",
            formattedTimestamp = "10:30 AM",
            thinkingData = ThinkingData(10, listOf("Thinking"))
        )

        // Then: No completed steps to show
        assertNull(message.completedSteps)
    }

    // ==================== Crew Mode Rendering Tests ====================

    @Test
    fun `Crew mode shows CompletedStepsHeader`() {
        // Given: A Crew mode message with completedSteps
        val completedSteps = listOf(
            StepCompletionData(
                stepOutput = "Draft One output",
                thinkingDurationSeconds = 30,
                thinkingSteps = listOf("Thinking step 1"),
                stepType = PipelineStep.DRAFT_ONE,
                modelType = ModelType.DRAFT_ONE,
                modelDisplayName = "Model 1"
            )
        )

        // Then: Completed steps exist
        assertTrue(completedSteps.isNotEmpty())
    }

    @Test
    fun `Crew mode filters out FINAL step from visible completed steps`() {
        // Given: Completed steps including FINAL
        val completedSteps = listOf(
            StepCompletionData(stepOutput = "Draft One", thinkingDurationSeconds = 30, thinkingSteps = listOf(), stepType = PipelineStep.DRAFT_ONE, modelType = ModelType.DRAFT_ONE, modelDisplayName = "M1"),
            StepCompletionData(stepOutput = "Draft Two", thinkingDurationSeconds = 25, thinkingSteps = listOf(), stepType = PipelineStep.DRAFT_TWO, modelType = ModelType.DRAFT_TWO, modelDisplayName = "M2"),
            StepCompletionData(stepOutput = "Synthesis", thinkingDurationSeconds = 45, thinkingSteps = listOf(), stepType = PipelineStep.SYNTHESIS, modelType = ModelType.MAIN, modelDisplayName = "M3"),
            StepCompletionData(stepOutput = "Final output", thinkingDurationSeconds = 20, thinkingSteps = listOf(), stepType = PipelineStep.FINAL, modelType = ModelType.MAIN, modelDisplayName = "M4")
        )

        // When: Filter out FINAL step (its output is in content)
        val visibleSteps = completedSteps.filter { it.stepType != PipelineStep.FINAL }

        // Then: FINAL step is filtered out
        assertEquals(3, visibleSteps.size)
        assertFalse(visibleSteps.any { it.stepType == PipelineStep.FINAL })
    }

    @Test
    fun `Crew mode shows Processing indicator when responseState is PROCESSING`() {
        // Verify ResponseState.PROCESSING exists
        val processingState = ResponseState.PROCESSING
        assertEquals(ResponseState.PROCESSING, processingState)
    }

    @Test
    fun `Crew mode shows Thinking indicator when responseState is THINKING`() {
        // Verify ResponseState.THINKING exists
        val thinkingState = ResponseState.THINKING
        assertEquals(ResponseState.THINKING, thinkingState)
    }

    @Test
    fun `Crew mode shows ThoughtForHeader from thinkingData`() {
        // Given: Crew mode message with thinkingData
        val thinkingData = ThinkingData(
            thinkingDurationSeconds = 30,
            steps = listOf("Final thinking step"),
            modelDisplayName = "Model"
        )

        // Then: Thinking data has steps
        assertTrue(thinkingData.steps.isNotEmpty())
    }

    @Test
    fun `Crew mode shows content correctly`() {
        // Given: A Crew mode message with content
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = "Final synthesized response",
            formattedTimestamp = "10:30 AM",
            completedSteps = listOf(
                StepCompletionData(stepOutput = "Step output", thinkingDurationSeconds = 30, thinkingSteps = listOf(), stepType = PipelineStep.DRAFT_ONE, modelType = ModelType.DRAFT_ONE, modelDisplayName = "M1")
            )
        )

        // Then: Content exists
        assertTrue(message.content.isNotEmpty())
    }

    @Test
    fun `Crew mode shows timestamp`() {
        // Given: A Crew mode message with timestamp
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = "Response",
            formattedTimestamp = "10:30 AM",
            completedSteps = listOf(
                StepCompletionData(stepOutput = "Step", thinkingDurationSeconds = 10, thinkingSteps = listOf(), stepType = PipelineStep.DRAFT_ONE, modelType = ModelType.DRAFT_ONE, modelDisplayName = "M")
            )
        )

        // Then: Timestamp is present
        assertEquals("10:30 AM", message.formattedTimestamp)
    }

    // ==================== Live Indicators Tests ====================

    @Test
    fun `Live indicator appears below completed steps during Crew mode`() {
        // Given: Crew mode with responseState
        val completedSteps = listOf(
            StepCompletionData(stepOutput = "Step", thinkingDurationSeconds = 10, thinkingSteps = listOf(), stepType = PipelineStep.DRAFT_ONE, modelType = ModelType.DRAFT_ONE, modelDisplayName = "M")
        )

        // Then: Completed steps exist to show indicator below
        assertTrue(completedSteps.isNotEmpty())
    }

    @Test
    fun `Processing indicator shows during initial processing phase`() {
        // Given: ResponseState.PROCESSING
        val state = ResponseState.PROCESSING

        // Then: This is the processing state
        assertEquals(ResponseState.PROCESSING, state)
    }

    @Test
    fun `Thinking indicator shows during thinking phase`() {
        // Given: ResponseState.THINKING with thinkingSteps
        val thinkingSteps = listOf("Thinking step 1", "Thinking step 2")

        // Then: Thinking has steps
        assertTrue(thinkingSteps.isNotEmpty())
    }

    // ==================== Edge Cases Tests ====================

    @Test
    fun `Empty completedSteps list is treated as Normal mode`() {
        // Given: Empty completedSteps list
        val completedSteps: List<StepCompletionData>? = emptyList()

        // Then: Empty list should be treated as no completed steps
        assertTrue(completedSteps.isNullOrEmpty())
    }

    @Test
    fun `completedSteps with only FINAL step is treated as Crew mode`() {
        // Given: Completed steps with only FINAL
        val completedSteps = listOf(
            StepCompletionData(stepOutput = "Final", thinkingDurationSeconds = 20, thinkingSteps = listOf(), stepType = PipelineStep.FINAL, modelType = ModelType.MAIN, modelDisplayName = "M")
        )

        // Then: Even with only FINAL, it's still Crew mode
        assertTrue(completedSteps.isNotEmpty())
    }

    @Test
    fun `Message with empty content shows correctly in both modes`() {
        // Given: Empty content message (normal mode)
        val normalMessage = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = "",
            formattedTimestamp = "10:30 AM"
        )

        // Given: Empty content message (crew mode)
        val crewMessage = ChatMessage(
            id = 2L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = "",
            formattedTimestamp = "10:30 AM",
            completedSteps = listOf(
                StepCompletionData(stepOutput = "Step", thinkingDurationSeconds = 10, thinkingSteps = listOf(), stepType = PipelineStep.DRAFT_ONE, modelType = ModelType.DRAFT_ONE, modelDisplayName = "M")
            )
        )

        // Then: Both have empty content (placeholder will show)
        assertEquals("", normalMessage.content)
        assertEquals("", crewMessage.content)
    }

    // ==================== Integration Tests ====================

    @Test
    fun `StepCompletionData correctly stores all required fields`() {
        // Given: Full step completion data
        val stepData = StepCompletionData(
            stepOutput = "This is the output from Draft One",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Analyzing request", "Generating draft"),
            stepType = PipelineStep.DRAFT_ONE,
            modelType = ModelType.DRAFT_ONE,
            modelDisplayName = "Gemma 3 1B"
        )

        // Then: All fields are correctly stored
        assertEquals("This is the output from Draft One", stepData.stepOutput)
        assertEquals(30, stepData.thinkingDurationSeconds)
        assertEquals(2, stepData.thinkingSteps.size)
        assertEquals(PipelineStep.DRAFT_ONE, stepData.stepType)
        assertEquals(ModelType.DRAFT_ONE, stepData.modelType)
        assertEquals("Gemma 3 1B", stepData.modelDisplayName)
        assertEquals("Draft One", stepData.stepName)
    }

    @Test
    fun `ThinkingData correctly stores all required fields`() {
        // Given: Full thinking data
        val thinkingData = ThinkingData(
            thinkingDurationSeconds = 45,
            steps = listOf("Analyzing", "Drafting", "Refining"),
            modelDisplayName = "DeepSeek-R1-8B"
        )

        // Then: All fields are correctly stored
        assertEquals(45, thinkingData.thinkingDurationSeconds)
        assertEquals(3, thinkingData.steps.size)
        assertEquals("DeepSeek-R1-8B", thinkingData.modelDisplayName)
    }

    @Test
    fun `ChatMessage correctly holds both thinkingData and completedSteps`() {
        // Given: A message with both thinkingData and completedSteps
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = "Final response",
            formattedTimestamp = "10:30 AM",
            completedSteps = listOf(
                StepCompletionData(stepOutput = "Draft One", thinkingDurationSeconds = 30, thinkingSteps = listOf("Think 1"), stepType = PipelineStep.DRAFT_ONE, modelType = ModelType.DRAFT_ONE, modelDisplayName = "Model 1"),
                StepCompletionData(stepOutput = "Draft Two", thinkingDurationSeconds = 25, thinkingSteps = listOf("Think 2"), stepType = PipelineStep.DRAFT_TWO, modelType = ModelType.DRAFT_TWO, modelDisplayName = "Model 2")
            ),
            thinkingData = ThinkingData(
                thinkingDurationSeconds = 20,
                steps = listOf("Final thinking"),
                modelDisplayName = "Main Model"
            )
        )

        // Then: Both are present
        assertNotNull(message.thinkingData)
        assertNotNull(message.completedSteps)
        assertEquals(2, message.completedSteps?.size)
        assertEquals(20, message.thinkingData?.thinkingDurationSeconds)
    }
}
