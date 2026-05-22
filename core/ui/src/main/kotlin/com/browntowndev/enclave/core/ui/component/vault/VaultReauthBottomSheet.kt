@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.browntowndev.enclave.core.ui.component.vault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Settings re-auth surface. Wraps [VaultUnlockContent] inside a [ModalBottomSheet] so the user
 * can re-enter their vault password without leaving the Settings screen — needed after a
 * post-Settings sign-in so the cloud DEK can be reconciled before the Firestore listener
 * starts.
 *
 * Hides the Google sign-in button (the user is guaranteed signed-in by the time this sheet
 * shows) and the biometric toggle (re-auth is not the moment to enroll a new fingerprint).
 *
 * @param showConfirmDialog when true, an overlay [VaultMismatchConfirmDialog] is rendered on
 *   top of the sheet to gate the destructive clobber.
 */
@Composable
fun VaultReauthBottomSheet(
    authState: VaultAuthUiState,
    errorMessage: String?,
    isUnlocking: Boolean,
    showConfirmDialog: Boolean,
    isReconciling: Boolean,
    onSubmitVaultPassword: (password: String) -> Unit,
    onConfirmAdoptCloud: () -> Unit,
    onCancelReconciliation: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            if (!isReconciling) onDismiss()
        },
        sheetState = sheetState,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
        ) {
            VaultUnlockContent(
                authState = authState,
                errorMessage = errorMessage,
                isUnlocking = isUnlocking,
                showGoogleSignInOnUnauth = false,
                showBiometricToggle = false,
                onLoginClick = {},
                onSubmitVaultPassword = { password, _ -> onSubmitVaultPassword(password) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (showConfirmDialog) {
                VaultMismatchConfirmDialog(
                    isReconciling = isReconciling,
                    onConfirm = onConfirmAdoptCloud,
                    onCancel = onCancelReconciliation,
                )
            }
        }
    }
}
