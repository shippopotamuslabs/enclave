/*
package com.browntowndev.enclave.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnclaveTabToolbarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `when toolbar is displayed, then both tabs are visible`() {
        composeTestRule.setContent {
            EnclaveTheme {
                EnclaveTabToolbar(
                    selectedTab = EnclaveTab.CHAT,
                    onTabClick = {},
                    onMenuClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Studio").assertIsDisplayed()
    }

    @Test
    fun `when studio tab is clicked, then onTabClick is called with STUDIO`() {
        val onTabClick: (EnclaveTab) -> Unit = {}

        composeTestRule.setContent {
            EnclaveTheme {
                EnclaveTabToolbar(
                    selectedTab = EnclaveTab.CHAT,
                    onTabClick = onTabClick,
                    onMenuClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Studio").performClick()

        verify { onTabClick(EnclaveTab.STUDIO) }
    }

    @Test
    fun `when menu icon is clicked, then onMenuClick is called`() {
        val onMenuClick: () -> Unit = {}

        composeTestRule.setContent {
            EnclaveTheme {
                EnclaveTabToolbar(
                    selectedTab = EnclaveTab.CHAT,
                    onTabClick = {},
                    onMenuClick = onMenuClick
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Menu").performClick()

        verify { onMenuClick() }
    }
}
*/
