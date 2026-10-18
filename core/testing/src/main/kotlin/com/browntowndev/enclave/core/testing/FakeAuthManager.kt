package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.port.auth.AuthManager
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.auth.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [AuthManager] fake (ENC-28). */
class FakeAuthManager(
    initialState: AuthState = AuthState.Unauthenticated,
) : AuthManager {
    private val mutableAuthState = MutableStateFlow(initialState)
    override val authState: StateFlow<AuthState> = mutableAuthState

    var signInResult: Result<UserProfile> =
        Result.success(UserProfile("uid", "User", "u@example.com", null))
    var signOutResult: Result<Unit> = Result.success(Unit)
    var signInCallCount = 0

    fun setAuthState(state: AuthState) {
        mutableAuthState.value = state
    }

    override suspend fun signInWithGoogle(): Result<UserProfile> {
        signInCallCount++
        return signInResult
    }

    var signOutCallCount = 0

    override suspend fun signOut(): Result<Unit> {
        signOutCallCount++
        return signOutResult
    }
}
