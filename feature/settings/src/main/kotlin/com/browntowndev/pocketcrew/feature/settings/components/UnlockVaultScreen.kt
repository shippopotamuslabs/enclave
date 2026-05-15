package com.browntowndev.pocketcrew.feature.settings.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.browntowndev.pocketcrew.core.ui.component.vault.VaultUnlockContent
import com.browntowndev.pocketcrew.core.ui.component.vault.VaultAuthUiState

/**
 * Full-screen wrapper around the shared [VaultUnlockContent] body. Owns the [Surface] chrome,
 * system-bar / IME insets, and vertical scroll behaviour for the cold-start unlock route.
 */
@Composable
fun UnlockVaultScreen(
    authState: VaultAuthUiState,
    errorMessage: String?,
    isUnlocking: Boolean = false,
    isBiometricUnlockConfigured: Boolean = false,
    onLoginClick: () -> Unit,
    onSubmitVaultPassword: (password: String, enableBiometric: Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        VaultUnlockContent(
            authState = authState,
            errorMessage = errorMessage,
            isUnlocking = isUnlocking,
            showGoogleSignInOnUnauth = true,
            showBiometricToggle = true,
            initialBiometricEnabled = isBiometricUnlockConfigured,
            onLoginClick = onLoginClick,
            onSubmitVaultPassword = onSubmitVaultPassword,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        )
    }
}
