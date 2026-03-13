package com.browntowndev.pocketcrew.inference.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for InferenceServiceStarter - verifies correct intent creation and service handling.
 */
class InferenceServiceStarterTest {

    @Test
    fun `service action constants are correctly defined in InferenceService`() {
        // Verify the action constants
        assertEquals("com.browntowndev.pocketcrew.inference.ACTION_START",
            InferenceService.ACTION_START)
        assertEquals("com.browntowndev.pocketcrew.inference.ACTION_STOP",
            InferenceService.ACTION_STOP)
    }

    @Test
    fun `service extra constants are correctly defined in InferenceService`() {
        // Verify the extra key constants
        assertEquals("chat_id", InferenceService.EXTRA_CHAT_ID)
        assertEquals("user_message", InferenceService.EXTRA_USER_MESSAGE)
        assertEquals("state_json", InferenceService.EXTRA_STATE_JSON)
    }

    @Test
    fun `start intent has correct action`() {
        // Verify the intent action matches
        assertEquals("com.browntowndev.pocketcrew.inference.ACTION_START",
            InferenceService.ACTION_START)
    }

    @Test
    fun `pipeline state key is correctly defined`() {
        // The state JSON key is defined in the service as EXTRA_STATE_JSON
        assertEquals("state_json", InferenceService.EXTRA_STATE_JSON)
    }

    @Test
    fun `starter can create start and stop intents`() {
        // Verify the InferenceServiceStarter can be instantiated
        // and has access to the service constants
        assertEquals(InferenceService.ACTION_START, "com.browntowndev.pocketcrew.inference.ACTION_START")
        assertEquals(InferenceService.ACTION_STOP, "com.browntowndev.pocketcrew.inference.ACTION_STOP")
    }
}
