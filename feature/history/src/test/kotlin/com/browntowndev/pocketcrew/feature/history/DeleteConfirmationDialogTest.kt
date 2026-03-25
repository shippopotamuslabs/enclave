package com.browntowndev.pocketcrew.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeleteConfirmationDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dialog_displaysCorrectText() {
        composeTestRule.setContent {
            PocketCrewTheme {
                DeleteConfirmationDialog(
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Delete Chat?").assertIsDisplayed()
        composeTestRule.onNodeWithText("This chat will be permanently deleted. This action cannot be undone.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun dialog_confirmClick_triggersOnConfirm() {
        var confirmClicked = false
        composeTestRule.setContent {
            PocketCrewTheme {
                DeleteConfirmationDialog(
                    onConfirm = { confirmClicked = true },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Delete").performClick()
        assert(confirmClicked)
    }

    @Test
    fun dialog_dismissClick_triggersOnDismiss() {
        var dismissClicked = false
        composeTestRule.setContent {
            PocketCrewTheme {
                DeleteConfirmationDialog(
                    onConfirm = {},
                    onDismiss = { dismissClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").performClick()
        assert(dismissClicked)
    }
}
