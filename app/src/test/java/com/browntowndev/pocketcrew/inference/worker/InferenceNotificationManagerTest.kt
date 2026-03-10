package com.browntowndev.pocketcrew.inference.worker

import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for InferenceNotificationManager logic.
 */
class InferenceNotificationManagerTest {

    @Test
    fun `notification IDs are correctly defined`() {
        assertEquals(2001, InferenceNotificationManager.NOTIFICATION_ID)
        assertEquals("crew_inference_channel", InferenceNotificationManager.CHANNEL_ID)
        assertEquals("crew_completion_channel", InferenceNotificationManager.COMPLETION_CHANNEL_ID)
    }

    @Test
    fun `KEY_STATE_JSON is correctly defined`() {
        assertEquals("pipeline_state_json", InferenceNotificationManager.KEY_STATE_JSON)
    }

    @Test
    fun `PipelineStep display names are human readable`() {
        assertEquals("Draft One", PipelineStep.DRAFT_ONE.displayName())
        assertEquals("Draft Two", PipelineStep.DRAFT_TWO.displayName())
        assertEquals("Synthesis", PipelineStep.SYNTHESIS.displayName())
        assertEquals("Final Review", PipelineStep.FINAL.displayName())
    }

    @Test
    fun `PipelineStep progression is correct`() {
        assertEquals(PipelineStep.DRAFT_TWO, PipelineStep.DRAFT_ONE.next())
        assertEquals(PipelineStep.SYNTHESIS, PipelineStep.DRAFT_TWO.next())
        assertEquals(PipelineStep.FINAL, PipelineStep.SYNTHESIS.next())
        assertEquals(null, PipelineStep.FINAL.next())
    }

    @Test
    fun `notification channel IDs are not empty`() {
        assertNotNull(InferenceNotificationManager.CHANNEL_ID)
        assertNotNull(InferenceNotificationManager.COMPLETION_CHANNEL_ID)
        assert(InferenceNotificationManager.CHANNEL_ID.isNotEmpty())
        assert(InferenceNotificationManager.COMPLETION_CHANNEL_ID.isNotEmpty())
    }
}
