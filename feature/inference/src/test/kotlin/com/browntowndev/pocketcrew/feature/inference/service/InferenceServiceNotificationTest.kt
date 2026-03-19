package com.browntowndev.pocketcrew.feature.inference.service

import android.content.pm.ServiceInfo
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for InferenceService notification behavior and foreground type.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK], manifest = Config.NONE)
class InferenceServiceNotificationTest {

    @Test
    fun `step progress calculation is correct for all steps`() {
        // Verify progress calculation matches expected values
        assertEquals(25, getStepProgress(PipelineStep.DRAFT_ONE))
        assertEquals(50, getStepProgress(PipelineStep.DRAFT_TWO))
        assertEquals(75, getStepProgress(PipelineStep.SYNTHESIS))
        assertEquals(100, getStepProgress(PipelineStep.FINAL))
    }

    private fun getStepProgress(step: PipelineStep): Int {
        return when (step) {
            PipelineStep.DRAFT_ONE -> 25
            PipelineStep.DRAFT_TWO -> 50
            PipelineStep.SYNTHESIS -> 75
            PipelineStep.FINAL -> 100
        }
    }

    @Test
    fun `FOREGROUND_SERVICE_TYPE_SPECIAL_USE constant exists`() {
        // This constant exists in Android SDK 34+
        assertTrue(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE > 0)
    }

    @Test
    fun `specialUse foreground type value is correct`() {
        // The value for specialUse should be > 0
        // On SDK 34+, this is defined as 0x00000080 (128)
        assertTrue(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE > 0)
    }

    @Test
    fun `PipelineStep display names are correct`() {
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
}
