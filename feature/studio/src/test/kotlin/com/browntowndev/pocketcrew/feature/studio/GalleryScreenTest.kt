package com.browntowndev.pocketcrew.feature.studio

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GalleryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun videoItemsRenderStaticThumbnailWithoutPlayerControls() {
        composeTestRule.setContent {
            PocketCrewTheme {
                GalleryScreen(
                    uiState = GalleryUiState(
                        albums = listOf(
                            GalleryAlbumUi(
                                id = DEFAULT_GALLERY_ALBUM_ID,
                                name = "Default Album",
                                items = listOf(
                                    StudioMediaUi(
                                        id = "video-1",
                                        localUri = "file:///missing-preview.mp4",
                                        prompt = "Video prompt",
                                        mediaType = MediaCapability.VIDEO,
                                        createdAt = 1L,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    onBackClick = {},
                    onMediaClick = { _, _ -> },
                    onAddAlbum = {},
                    onRenameAlbum = { _, _ -> },
                    onDeleteAlbums = {},
                    onShareMedia = {},
                    onMediaItemMeasured = { _, _ -> },
                    onDeleteMedia = {},
                    onMoveMedia = { _, _ -> },
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithContentDescription("Video prompt")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Video prompt").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Unmute video").assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription("Play video").assertCountEquals(0)
    }
}
