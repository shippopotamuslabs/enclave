package com.browntowndev.enclave.core.ui.component.vault

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
class VaultUnlockContentTddRedTest {
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
    fun `SCENARIO-18 LinearProgressIndicator renders with visual purity and correct test tags`() {
        composeTestRule.setContent {
            EnclaveTheme {
                VaultUnlockContent(
                    authState = authenticatedUser,
                    errorMessage = null,
                    isUnlocking = true,
                    showGoogleSignInOnUnauth = false,
                    showBiometricToggle = false,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                    unlockSyncProgress = 0.75f,
                    unlockSyncStatus = "Syncing updates...",
                )
            }
        }

        // Progress indicator with determinate progress should be shown
        composeTestRule.onNodeWithTag("unlock_sync_progress_indicator").assertIsDisplayed()
        // Status text should be displayed
        composeTestRule.onNodeWithText("Syncing updates...").assertIsDisplayed()
        // Indeterminate indicator should NOT be shown when progress is known
        composeTestRule.onNodeWithTag("unlock_sync_progress_indicator_indeterminate").assertDoesNotExist()
    }

    @Test
    fun `SCENARIO-18b Indeterminate indicator renders when progress fraction is null`() {
        composeTestRule.setContent {
            EnclaveTheme {
                VaultUnlockContent(
                    authState = authenticatedUser,
                    errorMessage = null,
                    isUnlocking = true,
                    showGoogleSignInOnUnauth = false,
                    showBiometricToggle = false,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                    unlockSyncProgress = null, // indeterminate
                    unlockSyncStatus = "Checking for updates...",
                )
            }
        }

        // Indeterminate progress indicator should be shown
        composeTestRule.onNodeWithTag("unlock_sync_progress_indicator_indeterminate").assertIsDisplayed()
        // Determinate indicator should NOT be shown when progress is unknown
        composeTestRule.onNodeWithTag("unlock_sync_progress_indicator").assertDoesNotExist()
        // Status text should show the status message
        composeTestRule.onNodeWithText("Checking for updates...").assertIsDisplayed()
    }

    @Test
    fun `SCENARIO-18c Progress indicator is hidden when not unlocking`() {
        composeTestRule.setContent {
            EnclaveTheme {
                VaultUnlockContent(
                    authState = authenticatedUser,
                    errorMessage = null,
                    isUnlocking = false,
                    showGoogleSignInOnUnauth = false,
                    showBiometricToggle = false,
                    onLoginClick = {},
                    onSubmitVaultPassword = { _, _ -> },
                    unlockSyncProgress = 0.5f,
                    unlockSyncStatus = "Syncing...",
                )
            }
        }

        // No progress indicators should be shown when not unlocking
        composeTestRule.onNodeWithTag("unlock_sync_progress_indicator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("unlock_sync_progress_indicator_indeterminate").assertDoesNotExist()
    }
}
