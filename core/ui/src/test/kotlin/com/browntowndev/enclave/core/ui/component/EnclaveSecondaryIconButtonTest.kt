package com.browntowndev.enclave.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSecondaryIconButton
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EnclaveSecondaryIconButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun whenNeoSecondaryIconButtonIsClicked_thenOnClickFires() {
        var clicked = false

        composeTestRule.setContent {
            EnclaveTheme {
                EnclaveSecondaryIconButton(
                    onClick = { clicked = true },
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Copy").performClick()

        assertTrue(clicked)
    }
}
