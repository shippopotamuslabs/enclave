package com.browntowndev.enclave.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NeoIconButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun whenNeoIconButtonIsClicked_thenOnClickFires() {
        var clicked = false

        composeTestRule.setContent {
            EnclaveTheme {
                NeoIconButton(
                    onClick = { clicked = true },
                    icon = Icons.Default.Add,
                    contentDescription = "Add",
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Add").performClick()

        assertTrue(clicked)
    }
}
