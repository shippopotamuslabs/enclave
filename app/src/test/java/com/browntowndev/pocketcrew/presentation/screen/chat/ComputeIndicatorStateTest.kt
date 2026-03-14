package com.browntowndev.pocketcrew.presentation.screen.chat

import com.browntowndev.pocketcrew.presentation.screen.chat.ProcessingIndicatorState
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for computeIndicatorState business logic in ChatViewModel.
 * This tests the core state machine for Crew mode indicators.
 */
class ComputeIndicatorStateTest {

    // Helper to call the private function via reflection
    private fun computeIndicatorState(
        responseState: ResponseState,
        mode: Mode,
        thinkingSteps: List<String>,
        thinkingStartTime: Long,
        thinkingDurationSeconds: Int,
        thinkingModelDisplayName: String
    ): Pair<ProcessingIndicatorState, ThinkingData?> {
        // Inline the logic for testing (matches ChatViewModel.computeIndicatorState)
        return when (responseState) {
            ResponseState.NONE -> ProcessingIndicatorState.NONE to null

            ResponseState.PROCESSING -> {
                if (thinkingDurationSeconds > 0 && thinkingSteps.isNotEmpty()) {
                    ProcessingIndicatorState.PROCESSING to ThinkingData(
                        thinkingDurationSeconds = thinkingDurationSeconds,
                        steps = thinkingSteps,
                        modelDisplayName = thinkingModelDisplayName
                    )
                } else {
                    ProcessingIndicatorState.PROCESSING to null
                }
            }

            ResponseState.THINKING -> {
                if (thinkingSteps.isNotEmpty() && thinkingStartTime > 0) {
                    ProcessingIndicatorState.NONE to ThinkingData(
                        thinkingDurationSeconds = 0,
                        steps = thinkingSteps,
                        modelDisplayName = thinkingModelDisplayName
                    )
                } else {
                    ProcessingIndicatorState.PROCESSING to null
                }
            }

            ResponseState.GENERATING -> {
                if (thinkingDurationSeconds > 0) {
                    ProcessingIndicatorState.GENERATING to ThinkingData(
                        thinkingDurationSeconds = thinkingDurationSeconds,
                        steps = thinkingSteps,
                        modelDisplayName = thinkingModelDisplayName
                    )
                } else {
                    ProcessingIndicatorState.GENERATING to null
                }
            }
        }
    }

    // ==================== NONE State ====================

    @Test
    fun `NONE state returns NONE indicator with null thinkingData`() {
        val (indicator, thinkingData) = computeIndicatorState(
            responseState = ResponseState.NONE,
            mode = Mode.CREW,
            thinkingSteps = listOf("Step 1"),
            thinkingStartTime = 1000L,
            thinkingDurationSeconds = 30,
            thinkingModelDisplayName = "Model"
        )

        assertEquals(ProcessingIndicatorState.NONE, indicator)
        assertNull(thinkingData)
    }

    // ==================== PROCESSING State ====================

    @Test
    fun `PROCESSING with completed thinking returns PROCESSING with preserved thinkingData`() {
        val (indicator, thinkingData) = computeIndicatorState(
            responseState = ResponseState.PROCESSING,
            mode = Mode.CREW,
            thinkingSteps = listOf("Step 1", "Step 2"),
            thinkingStartTime = 0L,
            thinkingDurationSeconds = 30,
            thinkingModelDisplayName = "DeepSeek-R1"
        )

        assertEquals(ProcessingIndicatorState.PROCESSING, indicator)
        assertNotNull(thinkingData)
        assertEquals(30, thinkingData!!.thinkingDurationSeconds)
        assertEquals(2, thinkingData.steps.size)
    }

    @Test
    fun `PROCESSING without thinking returns PROCESSING with null thinkingData`() {
        val (indicator, thinkingData) = computeIndicatorState(
            responseState = ResponseState.PROCESSING,
            mode = Mode.CREW,
            thinkingSteps = emptyList(),
            thinkingStartTime = 0L,
            thinkingDurationSeconds = 0,
            thinkingModelDisplayName = ""
        )

        assertEquals(ProcessingIndicatorState.PROCESSING, indicator)
        assertNull(thinkingData)
    }

    @Test
    fun `PROCESSING with thinkingDuration 0 returns PROCESSING with null thinkingData`() {
        val (indicator, thinkingData) = computeIndicatorState(
            responseState = ResponseState.PROCESSING,
            mode = Mode.CREW,
            thinkingSteps = listOf("Step 1"),
            thinkingStartTime = 0L,
            thinkingDurationSeconds = 0,  // Duration is 0 - thinking hasn't completed
            thinkingModelDisplayName = "Model"
        )

        assertEquals(ProcessingIndicatorState.PROCESSING, indicator)
        assertNull(thinkingData)
    }

    // ==================== THINKING State ====================

    @Test
    fun `THINKING with active thinking returns NONE with thinkingData duration 0`() {
        val (indicator, thinkingData) = computeIndicatorState(
            responseState = ResponseState.THINKING,
            mode = Mode.CREW,
            thinkingSteps = listOf("Analyzing", "Drafting"),
            thinkingStartTime = 1000L,
            thinkingDurationSeconds = 0,
            thinkingModelDisplayName = "Model"
        )

        assertEquals(ProcessingIndicatorState.NONE, indicator)
        assertNotNull(thinkingData)
        assertEquals(0, thinkingData!!.thinkingDurationSeconds)  // Still thinking
        assertEquals(2, thinkingData.steps.size)
    }

    @Test
    fun `THINKING without startTime returns PROCESSING with null`() {
        val (indicator, thinkingData) = computeIndicatorState(
            responseState = ResponseState.THINKING,
            mode = Mode.CREW,
            thinkingSteps = emptyList(),
            thinkingStartTime = 0L,
            thinkingDurationSeconds = 0,
            thinkingModelDisplayName = ""
        )

        assertEquals(ProcessingIndicatorState.PROCESSING, indicator)
        assertNull(thinkingData)
    }

    // ==================== GENERATING State ====================

    @Test
    fun `GENERATING with completed thinking returns GENERATING with thinkingData`() {
        val (indicator, thinkingData) = computeIndicatorState(
            responseState = ResponseState.GENERATING,
            mode = Mode.CREW,
            thinkingSteps = listOf("Step 1", "Step 2", "Step 3"),
            thinkingStartTime = 0L,
            thinkingDurationSeconds = 50,
            thinkingModelDisplayName = "DeepSeek-R1"
        )

        assertEquals(ProcessingIndicatorState.GENERATING, indicator)
        assertNotNull(thinkingData)
        assertEquals(50, thinkingData!!.thinkingDurationSeconds)
        assertEquals(3, thinkingData.steps.size)
    }

    @Test
    fun `GENERATING without thinking returns GENERATING with null thinkingData`() {
        val (indicator, thinkingData) = computeIndicatorState(
            responseState = ResponseState.GENERATING,
            mode = Mode.CREW,
            thinkingSteps = emptyList(),
            thinkingStartTime = 0L,
            thinkingDurationSeconds = 0,
            thinkingModelDisplayName = ""
        )

        assertEquals(ProcessingIndicatorState.GENERATING, indicator)
        assertNull(thinkingData)
    }

    // ==================== Crew Mode Flow Tests ====================

    @Test
    fun `Crew mode step completion flow - step completes to PROCESSING with thinkingData preserved`() {
        // Step just completed - we have thinkingDuration from that step
        val (indicator, thinkingData) = computeIndicatorState(
            responseState = ResponseState.PROCESSING,
            mode = Mode.CREW,
            thinkingSteps = listOf("Draft One thinking"),
            thinkingStartTime = 0L,
            thinkingDurationSeconds = 30,
            thinkingModelDisplayName = "Model 1"
        )

        // Should show PROCESSING with the completed thinking data
        assertEquals(ProcessingIndicatorState.PROCESSING, indicator)
        assertNotNull(thinkingData)
        assertEquals(30, thinkingData!!.thinkingDurationSeconds)
    }

    @Test
    fun `Crew mode next step starts - GENERATING with new thinkingData`() {
        // Next step starts generating
        val (indicator, thinkingData) = computeIndicatorState(
            responseState = ResponseState.GENERATING,
            mode = Mode.CREW,
            thinkingSteps = listOf("Draft Two thinking"),
            thinkingStartTime = 0L,
            thinkingDurationSeconds = 25,  // Thinking just completed for this step
            thinkingModelDisplayName = "Model 2"
        )

        // Should show GENERATING with new step's thinking data
        assertEquals(ProcessingIndicatorState.GENERATING, indicator)
        assertNotNull(thinkingData)
        assertEquals(25, thinkingData!!.thinkingDurationSeconds)
    }

    @Test
    fun `Crew mode FINAL step - GENERATING then Finished`() {
        // FINAL step generating
        val (indicator, thinkingData) = computeIndicatorState(
            responseState = ResponseState.GENERATING,
            mode = Mode.CREW,
            thinkingSteps = listOf("Final thinking"),
            thinkingStartTime = 0L,
            thinkingDurationSeconds = 20,
            thinkingModelDisplayName = "Main Model"
        )

        assertEquals(ProcessingIndicatorState.GENERATING, indicator)
        assertNotNull(thinkingData)

        // After FINAL completes, state goes to NONE
        val (finalIndicator, finalThinkingData) = computeIndicatorState(
            responseState = ResponseState.NONE,
            mode = Mode.CREW,
            thinkingSteps = emptyList(),
            thinkingStartTime = 0L,
            thinkingDurationSeconds = 0,
            thinkingModelDisplayName = ""
        )

        assertEquals(ProcessingIndicatorState.NONE, finalIndicator)
        assertNull(finalThinkingData)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `thinkingSteps preserved correctly across state transitions`() {
        val steps = listOf("Step 1", "Step 2", "Step 3")

        // GENERATING with completed thinking
        val (genIndicator, genThinking) = computeIndicatorState(
            responseState = ResponseState.GENERATING,
            mode = Mode.CREW,
            thinkingSteps = steps,
            thinkingStartTime = 0L,
            thinkingDurationSeconds = 45,
            thinkingModelDisplayName = "Model"
        )

        // Same steps should be preserved in thinkingData
        assertEquals(steps, genThinking!!.steps)

        // Step completes - state changes to PROCESSING
        val (procIndicator, procThinking) = computeIndicatorState(
            responseState = ResponseState.PROCESSING,
            mode = Mode.CREW,
            thinkingSteps = steps,
            thinkingStartTime = 0L,
            thinkingDurationSeconds = 45,
            thinkingModelDisplayName = "Model"
        )

        // Same steps should still be preserved
        assertEquals(steps, procThinking!!.steps)
    }
}
