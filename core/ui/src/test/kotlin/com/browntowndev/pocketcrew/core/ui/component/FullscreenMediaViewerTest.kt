package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FullscreenMediaViewerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun imageMedia_rendersImageSlot() {
        var imageRendered = false
        var videoRendered = false

        composeTestRule.setContent {
            PocketCrewTheme {
                FullscreenMediaViewer(
                    localUri = "file:///preview-image.png",
                    mediaType = MediaCapability.IMAGE,
                    contentDescription = "Preview image",
                    videoContent = { _, _, modifier ->
                        videoRendered = true
                        Box(
                            modifier = modifier.testTag("video-slot"),
                        )
                    },
                    imageContent = { _, _, modifier ->
                        imageRendered = true
                        Box(
                            modifier = modifier
                                .fillMaxSize()
                                .testTag("image-slot"),
                        )
                    },
                )
            }
        }

        composeTestRule.runOnIdle {
            assertTrue(imageRendered)
            assertFalse(videoRendered)
        }
        composeTestRule.onNodeWithTag("image-slot").assertIsDisplayed()
    }

    @Test
    fun videoMedia_rendersVideoSlot() {
        var imageRendered = false
        var videoRendered = false

        composeTestRule.setContent {
            PocketCrewTheme {
                FullscreenMediaViewer(
                    localUri = "file:///preview-video.mp4",
                    mediaType = MediaCapability.VIDEO,
                    contentDescription = "Preview video",
                    videoContent = { _, _, modifier ->
                        videoRendered = true
                        Box(
                            modifier = modifier
                                .fillMaxSize()
                                .testTag("video-slot"),
                        )
                    },
                    imageContent = { _, _, modifier ->
                        imageRendered = true
                        Box(
                            modifier = modifier.testTag("image-slot"),
                        )
                    },
                )
            }
        }

        composeTestRule.runOnIdle {
            assertTrue(videoRendered)
            assertFalse(imageRendered)
        }
        composeTestRule.onNodeWithTag("video-slot").assertIsDisplayed()
    }
}
