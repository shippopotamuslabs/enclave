/*
package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PocketCrewTabToolbarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `when toolbar is displayed, then both tabs are visible`() {
        composeTestRule.setContent {
            PocketCrewTheme {
                PocketCrewTabToolbar(
                    selectedTab = PocketCrewTab.CHAT,
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
        val onTabClick = mockk<(PocketCrewTab) -> Unit>(relaxed = true)
        
        composeTestRule.setContent {
            PocketCrewTheme {
                PocketCrewTabToolbar(
                    selectedTab = PocketCrewTab.CHAT,
                    onTabClick = onTabClick,
                    onMenuClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Studio").performClick()

        verify { onTabClick(PocketCrewTab.STUDIO) }
    }

    @Test
    fun `when menu icon is clicked, then onMenuClick is called`() {
        val onMenuClick = mockk<() -> Unit>(relaxed = true)
        
        composeTestRule.setContent {
            PocketCrewTheme {
                PocketCrewTabToolbar(
                    selectedTab = PocketCrewTab.CHAT,
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
