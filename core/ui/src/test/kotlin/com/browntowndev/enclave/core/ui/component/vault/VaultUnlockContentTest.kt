package com.browntowndev.enclave.core.ui.component.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
    fun unlockingLatchReleasesWhenVaultStaysLockedAfterUnlockWork() {
        composeTestRule.setContent {
            EnclaveTheme {
                VaultUnlockContent(
                    authState = authenticatedUser,
                    errorMessage = null,
                    isUnlocking = false,
                    isVaultLocked = true,
                    showGoogleSignInOnUnauth = false,
                    showBiometricToggle = true,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText("Signing In...").assertDoesNotExist()
        composeTestRule.onNodeWithText("Enclave Password").assertExists()
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

    @Test
    fun biometricEnrollmentPendingReleasesUnlockingLatchAndShowsPasswordForm() {
        composeTestRule.setContent {
            EnclaveTheme {
                VaultUnlockContent(
                    authState = authenticatedUser,
                    errorMessage = null,
                    isUnlocking = false,
                    isBiometricEnrollmentPending = true,
                    showGoogleSignInOnUnauth = false,
                    showBiometricToggle = true,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText("Signing In...").assertDoesNotExist()
        composeTestRule.onNodeWithText("Enclave Password").assertExists()
    }

    @Test
    fun decryptionErrorResetsUnlockingStateAndShowsError() {
        var isUnlocking by mutableStateOf(true)
        var errorMessage by mutableStateOf<String?>(null)

        composeTestRule.setContent {
            EnclaveTheme {
                VaultUnlockContent(
                    authState = authenticatedUser,
                    errorMessage = errorMessage,
                    isUnlocking = isUnlocking,
                    showGoogleSignInOnUnauth = false,
                    showBiometricToggle = true,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                )
            }
        }

        // Initially in unlocking state (Signing In... or similar is visible)
        composeTestRule.onNodeWithTag("unlock_sync_status_text").assertExists()

        // Decryption fails: set error message and clear unlocking flag
        composeTestRule.runOnIdle {
            isUnlocking = false
            errorMessage = "Decryption failed: Incorrect password"
        }

        // Latch should release and the error message should be displayed along with password fields
        composeTestRule.onNodeWithText("Decryption failed: Incorrect password").assertExists()
        composeTestRule.onNodeWithText("Enclave Password").assertExists()
    }
}
