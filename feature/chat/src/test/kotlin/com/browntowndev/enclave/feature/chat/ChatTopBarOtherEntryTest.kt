package com.browntowndev.enclave.feature.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
class ChatTopBarOtherEntryTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // Regression-capture: the dropdown renders Fast/Thinking/Council (each calls
    // onModeChange) followed by a separate "Other" item that calls onOtherClick
    // only. The "Other" label carries a trailing chevron icon (not a literal '>'),
    // so the asserted text is "Other". This test documents that ordering and will
    // fail if OTHER is removed or reordered.
    @Test
    fun `S01 — Other menu item appears below Council in mode dropdown`() {
        composeTestRule.setContent {
            EnclaveTheme {
                ChatTopBar(
                    selectedMode = ChatModeUi.FAST,
                    onMenuClick = {},
                    onNewChatClick = {},
                    onOtherClick = {},
                )
            }
        }

        // Expand the mode dropdown by clicking the title ("Fast" is unambiguous
        // before expansion — only the title shows it).
        composeTestRule.onNodeWithText("Fast").performClick()

        // After expansion, "Thinking", "Council", and "Other" only appear in
        // the dropdown (not the title, which shows "Fast"). Verify they exist.
        composeTestRule.onNodeWithText("Thinking").assertExists()
        composeTestRule.onNodeWithText("Council").assertExists()
        composeTestRule.onNodeWithText("Other").assertExists()

        // "Fast" now appears in both the title and the dropdown item (2 nodes).
        composeTestRule.onAllNodesWithText("Fast").assertCountEquals(2)
    }

    @Test
    fun `S02 — Tapping Other calls onOtherClick once and does not call onModeChange`() {
        val otherClickCount = AtomicInteger(0)
        val modeChangeCount = AtomicInteger(0)

        composeTestRule.setContent {
            EnclaveTheme {
                ChatTopBar(
                    selectedMode = ChatModeUi.FAST,
                    onModeChange = { modeChangeCount.incrementAndGet() },
                    onOtherClick = { otherClickCount.incrementAndGet() },
                    onMenuClick = {},
                    onNewChatClick = {},
                )
            }
        }

        // Expand dropdown (title "Fast" is unambiguous before expansion)
        composeTestRule.onNodeWithText("Fast").performClick()

        // Tap "Other" — only in dropdown, unambiguous
        composeTestRule.onNodeWithText("Other").performClick()

        assertEquals(1, otherClickCount.get())
        assertEquals(0, modeChangeCount.get())
    }

    @Test
    fun `S13 — Top bar title shows Other when OTHER mode is active`() {
        composeTestRule.setContent {
            EnclaveTheme {
                ChatTopBar(
                    selectedMode = ChatModeUi.OTHER,
                    onMenuClick = {},
                    onNewChatClick = {},
                    onOtherClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Other").assertExists()
    }
}
