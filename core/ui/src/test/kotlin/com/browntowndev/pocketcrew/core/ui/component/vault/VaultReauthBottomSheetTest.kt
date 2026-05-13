package com.browntowndev.pocketcrew.core.ui.component.vault

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.pocketcrew.core.ui.component.vault.VaultAuthUiState
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultReauthBottomSheetTest {

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
    fun bottomSheetShowsPasswordFieldButHidesGoogleSignIn() {
        composeTestRule.setContent {
            PocketCrewTheme {
                VaultReauthBottomSheet(
                    authState = authenticatedUser,
                    errorMessage = null,
                    isUnlocking = false,
                    showConfirmDialog = false,
                    isReconciling = false,
                    onSubmitVaultPassword = {},
                    onConfirmAdoptCloud = {},
                    onCancelReconciliation = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Welcome back, Ada").assertExists()
        composeTestRule.onNodeWithText("Enclave Password").assertExists()
        composeTestRule.onNodeWithText("Unlock").assertExists()
        composeTestRule.onNodeWithText("Sign in with Google for sync").assertDoesNotExist()
    }

    @Test
    fun bottomSheetHidesBiometricToggle() {
        composeTestRule.setContent {
            PocketCrewTheme {
                VaultReauthBottomSheet(
                    authState = authenticatedUser,
                    errorMessage = null,
                    isUnlocking = false,
                    showConfirmDialog = false,
                    isReconciling = false,
                    onSubmitVaultPassword = {},
                    onConfirmAdoptCloud = {},
                    onCancelReconciliation = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Biometric Unlock").assertDoesNotExist()
    }

    @Test
    fun bottomSheetSurfacesInlineErrorMessage() {
        composeTestRule.setContent {
            PocketCrewTheme {
                VaultReauthBottomSheet(
                    authState = authenticatedUser,
                    errorMessage = "Incorrect Vault Password",
                    isUnlocking = false,
                    showConfirmDialog = false,
                    isReconciling = false,
                    onSubmitVaultPassword = {},
                    onConfirmAdoptCloud = {},
                    onCancelReconciliation = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Incorrect Vault Password").assertExists()
    }

    @Test
    fun confirmDialogOverlaysSheetAndShowsReplaceLocalLabel() {
        composeTestRule.setContent {
            PocketCrewTheme {
                VaultReauthBottomSheet(
                    authState = authenticatedUser,
                    errorMessage = null,
                    isUnlocking = false,
                    showConfirmDialog = true,
                    isReconciling = false,
                    onSubmitVaultPassword = {},
                    onConfirmAdoptCloud = {},
                    onCancelReconciliation = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Replace local data with cloud?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Replace local").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun confirmDialogShowsReplacingSpinnerWhileReconciling() {
        composeTestRule.setContent {
            PocketCrewTheme {
                VaultReauthBottomSheet(
                    authState = authenticatedUser,
                    errorMessage = null,
                    isUnlocking = false,
                    showConfirmDialog = true,
                    isReconciling = true,
                    onSubmitVaultPassword = {},
                    onConfirmAdoptCloud = {},
                    onCancelReconciliation = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("  Replacing...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Replace local").assertIsNotDisplayed()
    }
}
