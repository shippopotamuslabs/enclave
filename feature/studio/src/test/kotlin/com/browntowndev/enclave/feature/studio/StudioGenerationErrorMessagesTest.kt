package com.browntowndev.enclave.feature.studio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StudioGenerationErrorMessagesTest {
    @Test
    fun toSnackbarMessage_mapsFalContentPolicyViolation() {
        val raw =
            "fal video generation failed: could not fetch queue result from " +
                "https://queue.fal.run/bytedance/seedance-2.0/requests/abc " +
                "(fal queue result failed: 422 Output video has sensitive content.)"

        val message = StudioGenerationErrorMessages.toSnackbarMessage(raw)

        assertTrue(message.contains("sensitive content", ignoreCase = true))
        assertTrue(message.contains("blocked", ignoreCase = true))
        assertFalse(message.contains("queue.fal.run"))
        assertFalse(message.contains("base64"))
    }

    @Test
    fun toSnackbarMessage_passesThroughShortProviderMessages() {
        assertEquals(
            "No image generation provider assigned",
            StudioGenerationErrorMessages.toSnackbarMessage("No image generation provider assigned"),
        )
    }

    @Test
    fun toSnackbarMessage_usesDefaultForBlank() {
        assertEquals(
            "Generation failed. Try again.",
            StudioGenerationErrorMessages.toSnackbarMessage(null),
        )
    }

    @Test
    fun isModerationError_detectsContentPolicyMessage() {
        val raw =
            "fal video generation failed: could not fetch queue result from " +
                "https://queue.fal.run/requests/1 " +
                "(fal queue result failed: 422 Output video has sensitive content.)"

        assertTrue(StudioGenerationErrorMessages.isModerationError(raw))
    }

    @Test
    fun isModerationError_detectsContentPolicyType() {
        val raw = "Request failed: content_policy_violation"

        assertTrue(StudioGenerationErrorMessages.isModerationError(raw))
    }

    @Test
    fun isModerationError_returnsFalseForGenericFailure() {
        assertFalse(StudioGenerationErrorMessages.isModerationError("fal image generation failed: timeout"))
        assertFalse(StudioGenerationErrorMessages.isModerationError(null))
        assertFalse(StudioGenerationErrorMessages.isModerationError(""))
    }
}
