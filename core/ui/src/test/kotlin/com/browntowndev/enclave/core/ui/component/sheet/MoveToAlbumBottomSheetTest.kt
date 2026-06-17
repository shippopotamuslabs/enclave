package com.browntowndev.enclave.core.ui.component.sheet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.enclave.core.ui.model.AlbumSelectionItem
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoveToAlbumBottomSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sheetShowsNeoHeaderCreateButtonAndAlbumRows() {
        composeTestRule.setContent {
            EnclaveTheme {
                MoveToAlbumBottomSheet(
                    albums =
                        listOf(
                            AlbumSelectionItem(
                                id = "album-1",
                                name = "Vacation",
                                itemCount = 3,
                                coverUri = null,
                                coverMediaType = null,
                            ),
                        ),
                    onDismiss = {},
                    onMoveToAlbum = {},
                    onAddAlbum = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Save to Album").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create New Album").assertIsDisplayed()
        composeTestRule.onNodeWithText("EXISTING ALBUMS").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vacation").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 items").assertIsDisplayed()
    }
}
