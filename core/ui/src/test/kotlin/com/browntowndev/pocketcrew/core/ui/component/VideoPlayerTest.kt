package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoPlayerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mutedByDefault_toggleUpdatesPlaybackHandleAndControls() {
        lateinit var playbackHandle: FakeVideoPlaybackHandle

        composeTestRule.setContent {
            PocketCrewTheme {
                VideoPlayer(
                    localUri = "file:///preview-video.mp4",
                    contentDescription = "Studio clip",
                    modifier = Modifier.size(120.dp),
                    playerFactory = { _, _, autoPlay, muted ->
                        FakeVideoPlaybackHandle(
                            autoPlay = autoPlay,
                            muted = muted,
                        ).also { handle -> playbackHandle = handle }
                    },
                    playerContent = { _, modifier ->
                        Box(
                            modifier = modifier
                                .background(Color.Black)
                                .testTag("player-surface"),
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Unmute video").assertIsDisplayed()

        composeTestRule.runOnIdle {
            assertEquals(0f, playbackHandle.volume)
            assertFalse(playbackHandle.playWhenReady)
        }

        composeTestRule.onNodeWithContentDescription("Unmute video").performClick()

        composeTestRule.runOnIdle {
            assertEquals(1f, playbackHandle.volume)
        }
        composeTestRule.onNodeWithContentDescription("Mute video").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Play video").performClick()

        composeTestRule.runOnIdle {
            assertTrue(playbackHandle.playWhenReady)
        }
        composeTestRule.onNodeWithContentDescription("Pause video").assertIsDisplayed()
    }

    @Test
    fun changingLocalUri_resetsMutedStateAndReleasesPreviousHandle() {
        val handles = linkedMapOf<String, FakeVideoPlaybackHandle>()
        var localUri by mutableStateOf("file:///clip-1.mp4")

        composeTestRule.setContent {
            PocketCrewTheme {
                VideoPlayer(
                    localUri = localUri,
                    contentDescription = "Studio clip",
                    modifier = Modifier.size(120.dp),
                    playerFactory = { _, uri, autoPlay, muted ->
                        handles.getOrPut(uri) {
                            FakeVideoPlaybackHandle(
                                autoPlay = autoPlay,
                                muted = muted,
                            )
                        }
                    },
                    playerContent = { _, modifier ->
                        Box(
                            modifier = modifier
                                .background(Color.Black)
                                .testTag("player-surface"),
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Unmute video").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Unmute video").performClick()

        composeTestRule.runOnIdle {
            assertEquals(1f, handles.getValue("file:///clip-1.mp4").volume)
        }

        composeTestRule.runOnIdle {
            localUri = "file:///clip-2.mp4"
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Unmute video").assertIsDisplayed()

        composeTestRule.runOnIdle {
            assertEquals(0f, handles.getValue("file:///clip-2.mp4").volume)
            assertTrue(handles.getValue("file:///clip-1.mp4").released)
        }
    }

    private class FakeVideoPlaybackHandle(
        autoPlay: Boolean = false,
        muted: Boolean = false,
    ) : VideoPlaybackHandle {
        override val player: Player = mockk(relaxed = true)
        override var playWhenReady: Boolean = autoPlay
        override var volume: Float = if (muted) 0f else 1f
        var released: Boolean = false

        override fun release() {
            released = true
        }
    }
}
