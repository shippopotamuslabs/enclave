package com.browntowndev.pocketcrew.domain.usecase.auth

import com.browntowndev.pocketcrew.domain.port.auth.AuthManager
import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.auth.UserProfile
import com.browntowndev.pocketcrew.domain.port.repository.CleanupRepository
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class LoginWithGoogleUseCase @Inject constructor(
    private val authManager: AuthManager,
    private val cloudSyncPort: CloudSyncPort
) {
    suspend operator fun invoke(): Result<UserProfile> {
        return authManager.signInWithGoogle().also { result ->
            if (result.isSuccess) {
                cloudSyncPort.syncAllLocalDataToCloud()
                cloudSyncPort.startListening()
            }
        }
    }
}

class LogoutUseCase @Inject constructor(
    private val authManager: AuthManager,
    private val cleanupRepository: CleanupRepository,
    private val cloudSyncPort: CloudSyncPort
) {
    suspend operator fun invoke(): Result<Unit> {
        return authManager.signOut().also {
            if (it.isSuccess) {
                cloudSyncPort.stopListening()
                cleanupRepository.clearAllData()
            }
        }
    }
}

class GetAuthStateUseCase @Inject constructor(
    private val authManager: AuthManager
) {
    operator fun invoke(): StateFlow<AuthState> = authManager.authState
}
