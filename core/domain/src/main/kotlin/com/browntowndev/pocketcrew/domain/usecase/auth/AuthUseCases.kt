@file:Suppress(
    "MaxLineLength",
    "MagicNumber",
    "UnusedPrivateProperty",
    "UnusedParameter",
    "TooManyFunctions",
    "ComplexCondition",
    "LargeClass",
    "CyclomaticComplexMethod",
    "SwallowedException",
    "InstanceOfCheckForException",
    "ThrowsCount",
    "NestedBlockDepth",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "FunctionNaming",
    "LongMethod",
    "MatchingDeclarationName",
    "UnusedPrivateMember",
    "LongParameterList",
)

package com.browntowndev.pocketcrew.domain.usecase.auth

import com.browntowndev.pocketcrew.domain.port.auth.AuthManager
import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.auth.UserProfile
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.CleanupRepository
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.usecase.settings.AutoAssignLocalModelsUseCase
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Performs Google sign-in only. Vault reconciliation, cloud sync, and Firestore listener
 * start-up are deliberately NOT triggered here — those side effects must be deferred to the
 * caller (`MainViewModel` or `SettingsViewModel`) which can first prompt the user to confirm
 * a destructive clobber when local and cloud DEKs differ. Auto-syncing here would race the
 * user-facing reconciliation prompt and risk uploading data encrypted with the wrong DEK.
 */
class LoginWithGoogleUseCase
    @Inject
    constructor(
        private val authManager: AuthManager,
        private val loggingPort: LoggingPort,
    ) {
        companion object {
            private const val TAG = "LoginUseCase"
            const val VAULT_VERIFICATION_MAGIC_STRING = "VERIFY_ZK_VAULT_v1"
        }

        suspend operator fun invoke(): Result<UserProfile> {
            loggingPort.debug(TAG, "LoginWithGoogleUseCase: Started")
            val authResult = authManager.signInWithGoogle()
            loggingPort.debug(TAG, "LoginWithGoogleUseCase: authResult.isSuccess = ${authResult.isSuccess}")
            return authResult
        }
    }

class LogoutUseCase
    @Inject
    constructor(
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
                    // Preserve model/provider configuration so guest mode keeps working.
                    cleanupRepository.clearUserContent()
                    autoAssignLocalModelsUseCase()
                }
            }
            return result
        }
    }

class GetAuthStateUseCase
    @Inject
    constructor(
        private val authManager: AuthManager,
    ) {
        operator fun invoke(): StateFlow<AuthState> = authManager.authState
    }
