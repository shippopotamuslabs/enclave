package com.browntowndev.pocketcrew.core.ui.component.vault

import androidx.compose.runtime.Immutable
import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.auth.UserProfile

@Immutable
data class VaultUserUiState(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

@Immutable
sealed interface VaultAuthUiState {
    data object Unauthenticated : VaultAuthUiState

    data object Loading : VaultAuthUiState

    data class Authenticated(
        val user: VaultUserUiState,
    ) : VaultAuthUiState

    data class Error(
        val message: String,
    ) : VaultAuthUiState
}

fun AuthState.toVaultAuthUiState(): VaultAuthUiState =
    when (this) {
        is AuthState.Authenticated -> VaultAuthUiState.Authenticated(user.toUi())
        AuthState.Loading -> VaultAuthUiState.Loading
        AuthState.Unauthenticated -> VaultAuthUiState.Unauthenticated
        is AuthState.Error -> VaultAuthUiState.Error(message)
    }

private fun UserProfile.toUi(): VaultUserUiState =
    VaultUserUiState(
        uid = uid,
        displayName = displayName,
        email = email,
        photoUrl = photoUrl,
    )
