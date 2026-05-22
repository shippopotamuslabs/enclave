package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CircularIconButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun whenCircularIconButtonIsClicked_thenOnClickFires() {
        var clicked = false

        composeTestRule.setContent {
            PocketCrewTheme {
                CircularIconButton(onClick = { clicked = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Add").performClick()

        assertTrue(clicked)
    }
}
