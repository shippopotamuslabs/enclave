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

package com.browntowndev.enclave.domain.usecase.auth

import com.browntowndev.enclave.domain.port.auth.AuthManager
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.auth.UserProfile
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.CleanupRepository
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.usecase.settings.AutoAssignLocalModelsUseCase
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Performs Google sign-in only. Vault reconciliation, cloud sync, and Firestore listener
 * start-up are deliberately NOT triggered here — those side effects must be deferred to the
 * caller (`MainViewModel` or `SettingsViewModel`) which can first prompt the user to confirm
 * a destructive clobber when local and cloud DEKs differ. Auto-syncing here would race the
 * user-facing reconciliation prompt and risk uploading data encrypted with the wrong DEK.
 */
open class LoginWithGoogleUseCase
    @Inject
    constructor(
        private val authManager: AuthManager,
        private val loggingPort: LoggingPort,
    ) {
        companion object {
            private const val TAG = "LoginUseCase"
            const val VAULT_VERIFICATION_MAGIC_STRING = "VERIFY_ZK_VAULT_v1"
        }

        open suspend operator fun invoke(): Result<UserProfile> {
            loggingPort.debug(TAG, "LoginWithGoogleUseCase: Started")
            val authResult = authManager.signInWithGoogle()
            loggingPort.debug(TAG, "LoginWithGoogleUseCase: authResult.isSuccess = ${authResult.isSuccess}")
            return authResult
        }
    }

open class LogoutUseCase
    @Inject
    constructor(
        private val authManager: AuthManager,
        private val cleanupRepository: CleanupRepository,
        private val cloudSyncPort: CloudSyncPort,
        private val autoAssignLocalModelsUseCase: AutoAssignLocalModelsUseCase,
    ) {
        open suspend operator fun invoke(): Result<Unit> {
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

open class GetAuthStateUseCase
    @Inject
    constructor(
        private val authManager: AuthManager,
    ) {
        open operator fun invoke(): StateFlow<AuthState> = authManager.authState
    }
