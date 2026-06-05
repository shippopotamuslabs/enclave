package com.browntowndev.enclave.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FullscreenMediaDetailTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun partitionFullscreenMediaActions_splitsPrimaryAndSheetActions() {
        val actions =
            listOf(
                FullscreenMediaAction(
                    icon = Icons.Default.Share,
                    label = "Share",
                    onClick = {},
                    placement = FullscreenMediaActionPlacement.PrimaryBar,
                ),
                FullscreenMediaAction(
                    icon = Icons.Default.Edit,
                    label = "Edit",
                    onClick = {},
                    placement = FullscreenMediaActionPlacement.SheetOnly,
                ),
            )

        val (primary, sheet) = partitionFullscreenMediaActions(actions)

        assertEquals(listOf("Share"), primary.map { it.label })
        assertEquals(listOf("Edit"), sheet.map { it.label })
    }

    @Test
    fun fullscreenMediaActionsOverlay_showsPrimaryIconsAndHidesSheetLabelsUntilMore() {
        composeTestRule.setContent {
            EnclaveTheme {
                FullscreenMediaActionsOverlay(
                    prompt = "Cinematic mountain lake",
                    actions =
                        listOf(
                            FullscreenMediaAction(
                                icon = Icons.Default.Share,
                                label = "Share",
                                onClick = {},
                                placement = FullscreenMediaActionPlacement.PrimaryBar,
                            ),
                            FullscreenMediaAction(
                                icon = Icons.Default.Download,
                                label = "Save",
                                onClick = {},
                                placement = FullscreenMediaActionPlacement.PrimaryBar,
                            ),
                            FullscreenMediaAction(
                                icon = Icons.Default.Edit,
                                label = "Edit",
                                onClick = {},
                                placement = FullscreenMediaActionPlacement.SheetOnly,
                            ),
                        ),
                )
            }
        }

        composeTestRule.onNodeWithText("Cinematic mountain lake").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Share").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Save").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(FULLSCREEN_MEDIA_MORE_ACTIONS_LABEL).assertIsDisplayed()
        composeTestRule.onNodeWithText("Share").assertIsNotDisplayed()
        composeTestRule.onNodeWithText("Edit").assertIsNotDisplayed()

        composeTestRule.onNodeWithContentDescription(FULLSCREEN_MEDIA_MORE_ACTIONS_LABEL).performClick()
        composeTestRule.onNodeWithText("Edit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Actions").assertIsDisplayed()
    }

    @Test
    fun fullscreenMediaActionsOverlay_listsDestructiveActionsLastInSheet() {
        composeTestRule.setContent {
            EnclaveTheme {
                FullscreenMediaActionsOverlay(
                    prompt = "Prompt",
                    actions =
                        listOf(
                            FullscreenMediaAction(
                                icon = Icons.Default.Share,
                                label = "Share",
                                onClick = {},
                                placement = FullscreenMediaActionPlacement.PrimaryBar,
                            ),
                            FullscreenMediaAction(
                                icon = Icons.Default.Edit,
                                label = "Edit",
                                onClick = {},
                                placement = FullscreenMediaActionPlacement.SheetOnly,
                            ),
                            FullscreenMediaAction(
                                icon = Icons.Default.Delete,
                                label = "Delete",
                                onClick = {},
                                isDestructive = true,
                                placement = FullscreenMediaActionPlacement.SheetOnly,
                            ),
                        ),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(FULLSCREEN_MEDIA_MORE_ACTIONS_LABEL).performClick()
        composeTestRule.onNodeWithText("Edit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
    }
}
