package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.pocketcrew.core.ui.component.vault.VaultAuthUiState
import com.browntowndev.pocketcrew.core.ui.component.vault.VaultUserUiState
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.feature.settings.components.UnlockVaultScreen
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
            PocketCrewTheme {
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
            PocketCrewTheme {
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
            PocketCrewTheme {
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
    fun biometricToggleStartsOnWhenBiometricUnlockIsConfigured() {
        composeTestRule.setContent {
            PocketCrewTheme {
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
                )
            }
        }

        composeTestRule.onNodeWithText("Biometric Unlock").assertExists()
        composeTestRule.onNode(isToggleable()).assertIsOn()
    }
}
