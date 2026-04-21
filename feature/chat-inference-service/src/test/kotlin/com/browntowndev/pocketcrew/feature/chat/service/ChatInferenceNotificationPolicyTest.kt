package com.browntowndev.pocketcrew.feature.chat.service

import android.app.Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatInferenceNotificationPolicyTest {

    @Test
    fun `foreground stop mode removes ongoing notification`() {
        assertEquals(Service.STOP_FOREGROUND_REMOVE, ChatInferenceNotificationPolicy.FOREGROUND_STOP_MODE)
    }

    @Test
    fun `shouldShowCompletionNotification returns false when app is foregrounded`() {
        assertFalse(ChatInferenceNotificationPolicy.shouldShowCompletionNotification(isAppForeground = true))
    }

    @Test
    fun `shouldShowCompletionNotification returns true when app is backgrounded`() {
        assertTrue(ChatInferenceNotificationPolicy.shouldShowCompletionNotification(isAppForeground = false))
    }
}
