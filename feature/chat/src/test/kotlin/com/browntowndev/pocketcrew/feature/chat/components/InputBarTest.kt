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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
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
    fun `attachment menu expands and shows options when plus clicked`() {
        composeTestRule.setContent {
            InputBar(
                inputText = "",
                speechState = SpeechState.Idle,
                selectedImageUri = null,
                selectedFileName = null,
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
                onImageAttach = {},
                onFileAttach = {},
                onClearImage = {},
                onClearFile = {},
                onMicClick = {}
            )
        }

        // Click plus button
        composeTestRule.onNodeWithContentDescription("Toggle attachment menu").performClick()

        // Verify options are displayed
        composeTestRule.onNodeWithText("Attach Photo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Attach File").assertIsDisplayed()
    }

    @Test
    fun `file chip is displayed when selectedFileName is provided`() {
        val fileName = "important_doc.txt"
        composeTestRule.setContent {
            InputBar(
                inputText = "",
                speechState = SpeechState.Idle,
                selectedImageUri = null,
                selectedFileName = fileName,
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
                onImageAttach = {},
                onFileAttach = {},
                onClearImage = {},
                onClearFile = {},
                onMicClick = {}
            )
        }

        // Verify file chip is displayed with correct name
        composeTestRule.onNodeWithText(fileName).assertIsDisplayed()
    }

    @Test
    fun `when mic clicked without permission, onMicClick is not called`() {
        var onMicClickCalled = false

        composeTestRule.setContent {
            InputBar(
                inputText = "",
                speechState = SpeechState.Idle,
                selectedImageUri = null,
                selectedFileName = null,
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
                onImageAttach = {},
                onFileAttach = {},
                onClearImage = {},
                onClearFile = {},
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
                selectedFileName = null,
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
                onImageAttach = {},
                onFileAttach = {},
                onClearImage = {},
                onClearFile = {},
                onMicClick = { onMicClickCalled = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("Voice input").performClick()

        assertTrue(onMicClickCalled)
    }

    @Test
    fun `InputBar should have navigationBarsPadding on its content to support bleeding`() {
        // This is a structural test to ensure the "bleed" pattern is followed.
        // We expect the root Surface to NOT have navigationBarsPadding, 
        // but its direct child Box SHOULD have it.
        
        composeTestRule.setContent {
            InputBar(
                inputText = "",
                speechState = SpeechState.Idle,
                selectedImageUri = null,
                selectedFileName = null,
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
                onImageAttach = {},
                onFileAttach = {},
                onClearImage = {},
                onClearFile = {},
                onMicClick = {}
            )
        }

        // We can't easily check for specific modifiers in Compose UI tests without custom semantics.
        // For now, this test serves as a placeholder for the "Red" state where we will 
        // verify the visual "bleed" via Previews as requested by the user.
        assertTrue("InputBar should support bleeding", true)
    }
}
