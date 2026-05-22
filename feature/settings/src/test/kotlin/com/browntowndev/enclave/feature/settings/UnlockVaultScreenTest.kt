package com.browntowndev.enclave.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.browntowndev.enclave.core.ui.component.vault.VaultAuthUiState
import com.browntowndev.enclave.core.ui.component.vault.VaultUserUiState
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.feature.settings.components.UnlockVaultScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnlockVaultScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun unauthenticatedUserSeesPasswordUnlockAndOptionalGoogleSync() {
        composeTestRule.setContent {
            EnclaveTheme {
                UnlockVaultScreen(
                    authState = VaultAuthUiState.Unauthenticated,
                    errorMessage = null,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText("Sign in with Google for sync").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enclave Password").assertExists()
        composeTestRule.onNodeWithText("Unlock").assertExists()
        composeTestRule.onNodeWithText("Skip for now").assertDoesNotExist()
    }

    @Test
    fun authenticatedUserStillMustUnlockWithPassword() {
        composeTestRule.setContent {
            EnclaveTheme {
                UnlockVaultScreen(
                    authState =
                        VaultAuthUiState.Authenticated(
                            VaultUserUiState(
                                uid = "user-1",
                                displayName = "Ada",
                                email = "ada@example.com",
                                photoUrl = null,
                            ),
                        ),
                    errorMessage = null,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText("Welcome back, Ada").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enclave Password").assertExists()
        composeTestRule.onNodeWithText("Sign in with Google for sync").assertDoesNotExist()
    }

    @Test
    fun unlockingStateKeepsLoadingUiVisibleUntilTheScreenLeaves() {
        var isUnlocking by mutableStateOf(true)
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            EnclaveTheme {
                UnlockVaultScreen(
                    authState =
                        VaultAuthUiState.Authenticated(
                            VaultUserUiState(
                                uid = "user-1",
                                displayName = "Ada",
                                email = "ada@example.com",
                                photoUrl = null,
                            ),
                        ),
                    errorMessage = null,
                    isUnlocking = isUnlocking,
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
                UnlockVaultScreen(
                    authState =
                        VaultAuthUiState.Authenticated(
                            VaultUserUiState(
                                uid = "user-1",
                                displayName = "Ada",
                                email = "ada@example.com",
                                photoUrl = null,
                            ),
                        ),
                    errorMessage = null,
                    isBiometricUnlockConfigured = true,
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
    fun useBiometricsActionInvokesCallback() {
        var clicked = false
        composeTestRule.setContent {
            EnclaveTheme {
                UnlockVaultScreen(
                    authState =
                        VaultAuthUiState.Authenticated(
                            VaultUserUiState(
                                uid = "user-1",
                                displayName = "Ada",
                                email = "ada@example.com",
                                photoUrl = null,
                            ),
                        ),
                    errorMessage = null,
                    isBiometricUnlockConfigured = true,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                    onBiometricUnlockClick = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("use_biometrics_button").performScrollTo().performClick()
        assertTrue(clicked)
    }
}
