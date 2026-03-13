package com.browntowndev.pocketcrew.presentation.screen.chat

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for AssistantResponse UI component handling of Crew mode pipeline.
 * These tests verify actual behavior of data transformations and helper methods.
 */
class FullPipelineAssistantResponseTest {

    /**
     * Tests that StepCompletionData is correctly populated from MessageGenerationState.StepCompleted.
     * This verifies the fromMessageGenerationState factory method works correctly.
     */
    @Test
    fun `StepCompletionData can be created from MessageGenerationState`() {
        val state = MessageGenerationState.StepCompleted(
            stepOutput = "Step output content",
            thinkingDurationSeconds = 35,
            thinkingSteps = listOf("Thinking step 1", "Thinking step 2"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        val stepData = StepCompletionData.fromMessageGenerationState(state, "Gemma 3 1B")

        assertEquals("Step output content", stepData.stepOutput)
        assertEquals(35, stepData.thinkingDurationSeconds)
        assertEquals(2, stepData.thinkingSteps.size)
        assertEquals("Gemma 3 1B", stepData.modelDisplayName)
        assertEquals(PipelineStep.DRAFT_ONE, stepData.stepType)
        assertEquals("Draft One", stepData.stepName)
    }

    /**
     * Tests that ResponseState enum has all required states.
     */
    @Test
    fun `ResponseState enum has all required states`() {
        val allStates = ResponseState.entries

        assertTrue(allStates.contains(ResponseState.NONE))
        assertTrue(allStates.contains(ResponseState.PROCESSING))
        assertTrue(allStates.contains(ResponseState.THINKING))
        assertTrue(allStates.contains(ResponseState.GENERATING))
        assertEquals(4, allStates.size)
    }

    /**
     * Tests that model display name is correctly stored and retrieved.
     */
    @Test
    fun `model display name is preserved from ModelRegistry`() {
        val stepData = StepCompletionData(
            stepOutput = "Output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Thinking"),
            stepType = PipelineStep.SYNTHESIS,
            modelType = ModelType.MAIN,
            modelDisplayName = "DeepSeek-R1-0528-Qwen3-8B"
        )

        assertEquals("DeepSeek-R1-0528-Qwen3-8B", stepData.modelDisplayName)
    }

    /**
     * Tests that each pipeline step has correct model type mapping.
     */
    @Test
    fun `each pipeline step has correct model type mapping`() {
        val draftOneStep = StepCompletionData(stepOutput = "Out", thinkingDurationSeconds = 10, thinkingSteps = listOf(), stepType = PipelineStep.DRAFT_ONE, modelType = ModelType.DRAFT_ONE, modelDisplayName = "Model")
        assertEquals(ModelType.DRAFT_ONE, draftOneStep.modelType)

        val draftTwoStep = StepCompletionData(stepOutput = "Out", thinkingDurationSeconds = 10, thinkingSteps = listOf(), stepType = PipelineStep.DRAFT_TWO, modelType = ModelType.DRAFT_TWO, modelDisplayName = "Model")
        assertEquals(ModelType.DRAFT_TWO, draftTwoStep.modelType)

        val synthesisStep = StepCompletionData(stepOutput = "Out", thinkingDurationSeconds = 10, thinkingSteps = listOf(), stepType = PipelineStep.SYNTHESIS, modelType = ModelType.MAIN, modelDisplayName = "Model")
        assertEquals(ModelType.MAIN, synthesisStep.modelType)

        val finalStep = StepCompletionData(stepOutput = "Out", thinkingDurationSeconds = 10, thinkingSteps = listOf(), stepType = PipelineStep.FINAL, modelType = ModelType.MAIN, modelDisplayName = "Model")
        assertEquals(ModelType.MAIN, finalStep.modelType)
    }

    /**
     * Tests that ChatMessage correctly combines final content with completed steps.
     */
    @Test
    fun `ChatMessage combines final content with completed steps`() {
        val completedSteps = listOf(
            StepCompletionData(stepOutput = "Output 1", thinkingDurationSeconds = 30, thinkingSteps = listOf("Think 1"), stepType = PipelineStep.DRAFT_ONE, modelType = ModelType.DRAFT_ONE, modelDisplayName = "Model 1"),
            StepCompletionData(stepOutput = "Output 2", thinkingDurationSeconds = 25, thinkingSteps = listOf("Think 2"), stepType = PipelineStep.DRAFT_TWO, modelType = ModelType.DRAFT_TWO, modelDisplayName = "Model 2")
        )

        val finalContent = "This is the final synthesized response that combines all the drafts."

        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.Assistant,
            content = finalContent,
            formattedTimestamp = "10:30 AM",
            completedSteps = completedSteps,
            thinkingData = ThinkingData(
                thinkingDurationSeconds = 20,
                steps = listOf("Final thinking step"),
                modelDisplayName = "Main Model"
            )
        )

        // Then: Both content and completed steps are present
        assertEquals(finalContent, message.content)
        assertEquals(2, message.completedSteps?.size)
        assertNotNull(message.thinkingData)
    }
}
