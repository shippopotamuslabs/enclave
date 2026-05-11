package com.browntowndev.pocketcrew.domain.port.auth

import kotlinx.coroutines.flow.StateFlow

data class UserProfile(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data class Authenticated(val user: UserProfile) : AuthState
    data class Error(val message: String) : AuthState
    data object Loading : AuthState
}

interface AuthManager {
    val authState: StateFlow<AuthState>
    suspend fun signInWithGoogle(): Result<UserProfile>
    suspend fun signOut(): Result<Unit>
}
