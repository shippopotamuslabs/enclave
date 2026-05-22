package com.browntowndev.pocketcrew.presentation.navigation

import androidx.navigation.navOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ChatNavigationPolicy")
class ChatNavigationPolicyTest {
    @Test
    @DisplayName("SC-PERF-09 — openChatFromHistory does not use launchSingleTop")
    fun scPerf09_openChatFromHistoryDoesNotUseLaunchSingleTop() {
        val options = navOptions { openChatFromHistory() }

        assertFalse(options.shouldLaunchSingleTop())
    }

    @Test
    @DisplayName("SC-PERF-10 — openChatFromHistory replaces chat destination inclusively")
    fun scPerf10_openChatFromHistoryReplacesChatDestination() {
        val options = navOptions { openChatFromHistory() }

        assertEquals(Routes.CHAT_WITH_ID, options.popUpToRoute)
        assertTrue(options.isPopUpToInclusive())
    }
}
