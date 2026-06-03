package com.browntowndev.enclave.core.ui.component.sheet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomAnimationPromptPaneTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pane_rendersNeoPromptFieldAndActions() {
        composeTestRule.setContent {
            EnclaveTheme(darkTheme = true) {
                CustomAnimationPromptPane(onSend = {})
            }
        }

        composeTestRule.onNodeWithTag(CUSTOM_ANIMATION_PROMPT_PANE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CUSTOM_ANIMATION_PROMPT_FIELD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("What should the animation do?").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Send animation prompt").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Expand").assertIsDisplayed()
    }
}