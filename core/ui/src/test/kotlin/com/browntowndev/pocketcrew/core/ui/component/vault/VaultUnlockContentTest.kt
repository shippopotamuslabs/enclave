package com.browntowndev.pocketcrew.core.ui.component.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultUnlockContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val authenticatedUser = VaultAuthUiState.Authenticated(
        VaultUserUiState(
            uid = "user-1",
            displayName = "Ada",
            email = "ada@example.com",
            photoUrl = null,
        ),
    )

    @Test
    fun unlockingStateStaysVisibleWhenUnlockFlagDropsBeforeDismissal() {
        var isUnlocking by mutableStateOf(true)
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            PocketCrewTheme {
                VaultUnlockContent(
                    authState = authenticatedUser,
                    errorMessage = null,
                    isUnlocking = isUnlocking,
                    showGoogleSignInOnUnauth = false,
                    showBiometricToggle = true,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText("Signing In...").assertExists()
        composeTestRule.onAllNodesWithText("Enclave Password").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Biometric Unlock").assertCountEquals(0)

        composeTestRule.runOnIdle {
            isUnlocking = false
        }
        composeTestRule.mainClock.advanceTimeBy(500)

        composeTestRule.onNodeWithText("Signing In...").assertExists()
        composeTestRule.onAllNodesWithText("Enclave Password").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Biometric Unlock").assertCountEquals(0)
    }
}
