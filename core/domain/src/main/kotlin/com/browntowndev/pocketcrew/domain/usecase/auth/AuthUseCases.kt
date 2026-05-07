package com.browntowndev.pocketcrew.domain.usecase.auth

import com.browntowndev.pocketcrew.domain.port.auth.AuthManager
import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.auth.UserProfile
import com.browntowndev.pocketcrew.domain.port.repository.CleanupRepository
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.usecase.settings.AutoAssignLocalModelsUseCase
import android.util.Base64
import kotlinx.coroutines.flow.StateFlow
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import javax.inject.Inject
class LoginWithGoogleUseCase @Inject constructor(
    private val authManager: AuthManager,
    private val cloudSyncPort: CloudSyncPort,
    private val cryptoManager: CryptoManagerPort,
    private val loggingPort: LoggingPort
) {
    companion object {
        private const val TAG = "LoginUseCase"
        const val VAULT_VERIFICATION_MAGIC_STRING = "VERIFY_ZK_VAULT_v1"
    }

    suspend operator fun invoke(): Result<UserProfile> {
        loggingPort.debug(TAG, "LoginWithGoogleUseCase: Started")
        val authResult = authManager.signInWithGoogle()
        loggingPort.debug(TAG, "LoginWithGoogleUseCase: authResult.isSuccess = ${authResult.isSuccess}")
        if (authResult.isSuccess) {
            val user = authResult.getOrThrow()

            // Check for existing salt
            val saltResult = cloudSyncPort.getVaultSalt()
            if (saltResult.isSuccess && saltResult.getOrNull() == null) {
                // First login, generate and store salt
                loggingPort.info(TAG, "First login detected, generating new vault salt")
                val newSalt = cryptoManager.generateSalt()
                val base64Salt = Base64.encodeToString(newSalt, Base64.NO_WRAP)
                cloudSyncPort.updateVaultSalt(base64Salt)

                // Note: We cannot save the verification data here because we don't have the 
                // user's vault password yet. That will be handled by the UI during first unlock.
            }

            loggingPort.debug(TAG, "LoginWithGoogleUseCase: Calling syncAllLocalDataToCloud")
            val syncResult = cloudSyncPort.syncAllLocalDataToCloud()
            loggingPort.debug(TAG, "LoginWithGoogleUseCase: syncResult.isSuccess = ${syncResult.isSuccess}")
            if (syncResult.isFailure) {
                return Result.failure(syncResult.exceptionOrNull() ?: Exception("Cloud sync failed"))
            }
            loggingPort.debug(TAG, "LoginWithGoogleUseCase: Calling startListening")
            cloudSyncPort.startListening()
        }
        return authResult
    }
}

class LogoutUseCase @Inject constructor(
    private val authManager: AuthManager,
    private val cleanupRepository: CleanupRepository,
    private val cloudSyncPort: CloudSyncPort,
    private val autoAssignLocalModelsUseCase: AutoAssignLocalModelsUseCase,
) {
    suspend operator fun invoke(): Result<Unit> {
        val result = authManager.signOut()
        if (result.isSuccess) {
            cloudSyncPort.stopListening()
            // We use runCatching to ensure database cleanup errors don't crash the logout flow
            // after the user has already been signed out from the auth provider.
            runCatching {
                cleanupRepository.clearAllData()
                autoAssignLocalModelsUseCase()
            }
        }
        return result
    }
}

class GetAuthStateUseCase @Inject constructor(
    private val authManager: AuthManager
) {
    operator fun invoke(): StateFlow<AuthState> = authManager.authState
}
