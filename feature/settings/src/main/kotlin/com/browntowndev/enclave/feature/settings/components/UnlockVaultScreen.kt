package com.browntowndev.enclave.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.browntowndev.enclave.core.ui.component.vault.VaultAuthUiState
import com.browntowndev.enclave.core.ui.component.vault.VaultUnlockContent

/**
 * Full-screen wrapper around the shared [VaultUnlockContent] body. Owns the neo-brutalist
 * background, system-bar / IME insets, and vertical scroll behaviour for the cold-start unlock
 * route.
 */
@Composable
fun UnlockVaultScreen(
    authState: VaultAuthUiState,
    errorMessage: String?,
    isUnlocking: Boolean = false,
    isVaultLocked: Boolean = true,
    isBiometricEnrollmentPending: Boolean = false,
    isBiometricUnlockConfigured: Boolean = false,
    onLoginClick: () -> Unit,
    onSubmitVaultPassword: (password: String, enableBiometric: Boolean) -> Unit,
    onBiometricUnlockClick: (() -> Unit)? = null,
    unlockSyncProgress: Float? = null,
    unlockSyncStatus: String? = null,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        VaultUnlockContent(
            authState = authState,
            errorMessage = errorMessage,
            isUnlocking = isUnlocking,
            isVaultLocked = isVaultLocked,
            isBiometricEnrollmentPending = isBiometricEnrollmentPending,
            showGoogleSignInOnUnauth = true,
            showBiometricToggle = true,
            initialBiometricEnabled = isBiometricUnlockConfigured,
            onLoginClick = onLoginClick,
            onSubmitVaultPassword = onSubmitVaultPassword,
            onBiometricUnlockClick = onBiometricUnlockClick,
            unlockSyncProgress = unlockSyncProgress,
            unlockSyncStatus = unlockSyncStatus,
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
        )
    }
}
