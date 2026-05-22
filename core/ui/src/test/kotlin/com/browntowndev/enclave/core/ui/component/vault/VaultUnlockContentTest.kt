package com.browntowndev.enclave.core.ui.component.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
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

    private val authenticatedUser =
        VaultAuthUiState.Authenticated(
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
            EnclaveTheme {
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

    @Test
    fun configuredBiometricsShowUseBiometricsActionInsteadOfToggle() {
        composeTestRule.setContent {
            EnclaveTheme {
                VaultUnlockContent(
                    authState = authenticatedUser,
                    errorMessage = null,
                    showGoogleSignInOnUnauth = false,
                    showBiometricToggle = true,
                    initialBiometricEnabled = true,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                    onBiometricUnlockClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Use Biometrics").assertExists()
        composeTestRule.onAllNodesWithText("Biometric Unlock").assertCountEquals(0)
        composeTestRule.onNode(isToggleable()).assertDoesNotExist()
    }

    @Test
    fun biometricToggleStartsOffWhenBiometricsNotYetConfigured() {
        var initial by mutableStateOf(false)
        composeTestRule.setContent {
            EnclaveTheme {
                VaultUnlockContent(
                    authState = authenticatedUser,
                    errorMessage = null,
                    showGoogleSignInOnUnauth = false,
                    showBiometricToggle = true,
                    initialBiometricEnabled = initial,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText("Biometric Unlock").assertExists()
        composeTestRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun biometricToggleResyncsWhenInitialBiometricEnabledChangesToFalse() {
        var initial by mutableStateOf(true)
        composeTestRule.setContent {
            EnclaveTheme {
                VaultUnlockContent(
                    authState = authenticatedUser,
                    errorMessage = null,
                    showGoogleSignInOnUnauth = false,
                    showBiometricToggle = true,
                    initialBiometricEnabled = initial,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                    onBiometricUnlockClick = if (initial) ({}) else null,
                )
            }
        }

        composeTestRule.onNodeWithText("Use Biometrics").assertExists()

        composeTestRule.runOnIdle { initial = false }
        composeTestRule.onNodeWithText("Biometric Unlock").assertExists()
        composeTestRule.onNode(isToggleable()).assertIsOff()
    }
}
