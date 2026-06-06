package com.browntowndev.enclave.presentation.navigation

import com.browntowndev.enclave.feature.chat.service.ChatNotificationDeepLink
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

    @Test
    fun `studio route declares notification deep link pattern`() {
        assertEquals("enclave://studio", Routes.STUDIO_DEEP_LINK_PATTERN)
    }
}
