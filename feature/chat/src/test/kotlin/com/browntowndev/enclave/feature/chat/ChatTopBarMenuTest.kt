package com.browntowndev.enclave.feature.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatTopBarMenuTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `TEL_004 chat top bar overflow menu preserves new chat behavior and adds chat stats`() {
        val newChatCount = AtomicInteger(0)
        val chatStatsCount = AtomicInteger(0)

        composeTestRule.setContent {
            EnclaveTheme {
                ChatTopBar(
                    onMenuClick = {},
                    onNewChatClick = { newChatCount.incrementAndGet() },
                    onViewChatStatsClick = { chatStatsCount.incrementAndGet() },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Chat options").performClick()
        composeTestRule.onNodeWithText("New chat").assertExists()
        composeTestRule.onNodeWithText("View chat stats").assertExists()
        composeTestRule.onNodeWithText("New chat").performClick()
        assertEquals(1, newChatCount.get())

        composeTestRule.onNodeWithContentDescription("Chat options").performClick()
        composeTestRule.onNodeWithText("View chat stats").performClick()
        assertEquals(1, chatStatsCount.get())
    }
}
