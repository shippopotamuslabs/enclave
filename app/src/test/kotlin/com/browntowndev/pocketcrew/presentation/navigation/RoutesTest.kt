package com.browntowndev.pocketcrew.presentation.navigation

import com.browntowndev.pocketcrew.feature.chat.service.ChatNotificationDeepLink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoutesTest {
    @Test
    fun `chat route declares notification deep link pattern`() {
        assertEquals(ChatNotificationDeepLink.URI_PATTERN, Routes.CHAT_DEEP_LINK_PATTERN)
    }

    @Test
    fun `gallery route is declared`() {
        assertEquals("gallery", Routes.GALLERY)
    }
}
