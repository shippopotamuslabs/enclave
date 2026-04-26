package com.browntowndev.pocketcrew.feature.chat.components

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.feature.chat.ChatModeUi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class InputBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `when mic clicked without permission, onMicClick is not called`() {
        var onMicClickCalled = false

        composeTestRule.setContent {
            InputBar(
                inputText = "",
                speechState = SpeechState.Idle,
                selectedImageUri = null,
                isPhotoAttachmentEnabled = true,
                photoAttachmentDisabledReason = null,
                selectedMode = ChatModeUi.FAST,
                isGenerating = false,
                canStop = false,
                isGlobalInferenceBlocked = false,
                onInputChange = {},
                onModeChange = {},
                onSend = {},
                onStopGenerating = {},
                onAttach = {},
                onClearAttachment = {},
                onMicClick = { onMicClickCalled = true }
            )
        }

        // The description for the mic button when idle is "Voice input"
        composeTestRule.onNodeWithContentDescription("Voice input").performClick()

        // It should request permission and NOT call onMicClick directly
        assertFalse(onMicClickCalled)
    }

    @Test
    fun `when mic clicked with permission granted, onMicClick is called`() {
        var onMicClickCalled = false
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)

        composeTestRule.setContent {
            InputBar(
                inputText = "",
                speechState = SpeechState.Idle,
                selectedImageUri = null,
                isPhotoAttachmentEnabled = true,
                photoAttachmentDisabledReason = null,
                selectedMode = ChatModeUi.FAST,
                isGenerating = false,
                canStop = false,
                isGlobalInferenceBlocked = false,
                onInputChange = {},
                onModeChange = {},
                onSend = {},
                onStopGenerating = {},
                onAttach = {},
                onClearAttachment = {},
                onMicClick = { onMicClickCalled = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("Voice input").performClick()

        assertTrue(onMicClickCalled)
    }
}
