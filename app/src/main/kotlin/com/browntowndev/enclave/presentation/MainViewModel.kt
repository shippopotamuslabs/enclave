package com.browntowndev.enclave.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.core.ui.component.vault.VaultAuthUiState
import com.browntowndev.enclave.core.ui.component.vault.toVaultAuthUiState
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.enclave.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.enclave.domain.usecase.download.InitializeModelsUseCase
import com.browntowndev.enclave.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.enclave.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.DecryptBiometricDekUseCase
import com.browntowndev.enclave.domain.usecase.security.GetBiometricDecryptCipherUseCase
import com.browntowndev.enclave.domain.usecase.security.GetBiometricEncryptCipherUseCase
import com.browntowndev.enclave.domain.usecase.security.StoreBiometricDekUseCase
import com.browntowndev.enclave.domain.usecase.security.VaultSessionInteractor
import com.browntowndev.enclave.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.enclave.domain.usecase.security.UnlockVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.VaultReconciliationOutcome
import com.browntowndev.enclave.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

/**
 * Snapshot of a `MismatchRequiresClobber` outcome that the UI must surface a confirmation
 * dialog for. The raw cloud DEK payload is kept private in the ViewModel so the observable
 * state only carries the dialog mode.
 */
data class PendingReconciliation(
    val isReconciling: Boolean = false,
)

sealed interface AppStartupState {
    data object Loading : AppStartupState

    data class Ready(
        val authState: VaultAuthUiState,
        val modelsResult: DownloadModelsResult?,
        val initializationError: String? = null,
        val vaultErrorMessage: String? = null,
        val isUnlocking: Boolean = false,
        val isVaultLocked: Boolean = false,
        val isBiometricUnlockPossible: Boolean = false,
        val isBiometricEnrollmentPending: Boolean = false,
        val pendingVaultReconciliation: PendingReconciliation? = null,
        val cloudSyncDeferred: Boolean = false,
        val unlockSyncProgress: Float? = null,
        val unlockSyncStatus: String? = null,
        val onboardingCompleted: Boolean = true,
    ) : AppStartupState {
        val initialRoute: String
            get() =
                when {
                    !onboardingCompleted -> Routes.ONBOARDING
                    initializationError != null ||
                        modelsResult == null ||
                        modelsResult.modelsToDownload.isNotEmpty() -> Routes.MODEL_DOWNLOAD
                    else -> Routes.CHAT
                }
    }
}

@HiltViewModel
@Suppress("MaxLineLength", "LongParameterList", "TooManyFunctions", "LargeClass")
class MainViewModel
    @Inject
    constructor(
        private val getAuthStateUseCase: GetAuthStateUseCase,
        private val loginWithGoogleUseCase: LoginWithGoogleUseCase,
        private val initializeModelsUseCase: InitializeModelsUseCase,
        private val vaultSession: VaultSessionInteractor,
        private val getBiometricEncryptCipherUseCase: GetBiometricEncryptCipherUseCase,
        private val getBiometricDecryptCipherUseCase: GetBiometricDecryptCipherUseCase,
        private val storeBiometricDekUseCase: StoreBiometricDekUseCase,
        private val decryptBiometricDekUseCase: DecryptBiometricDekUseCase,
        private val createVaultUseCase: CreateVaultUseCase,
        private val unlockVaultUseCase: UnlockVaultUseCase,
        private val detectVaultReconciliationStateUseCase: DetectVaultReconciliationStateUseCase,
        private val adoptCloudVaultUseCase: AdoptCloudVaultUseCase,
        private val bootstrapCloudVaultFromLocalUseCase: BootstrapCloudVaultFromLocalUseCase,
        private val loggingPort: LoggingPort,
        private val errorHandler: ViewModelErrorHandler,
    ) : ViewModel() {
        companion object {
            private const val TAG = "MainViewModel"
            private const val MODEL_INITIALIZATION_ERROR = "Failed to initialize models"
        }

        private var pendingVaultReconciliationPayload: VaultReconciliationOutcome.MismatchRequiresClobber? = null
        private val _startupState = MutableStateFlow<AppStartupState>(AppStartupState.Loading)
        val startupState: StateFlow<AppStartupState> = _startupState.asStateFlow()

        init {
            initializeApp()
        }

        @Suppress("TooGenericExceptionCaught")
        private fun initializeApp() {
            viewModelScope.launch {
                val isLocked = vaultSession.isVaultLocked()
                val isBioPossible = vaultSession.isBiometricUnlockPossible(isLocked)

                loggingPort.debug(TAG, "initializeApp: isLocked=$isLocked, isBioPossible=$isBioPossible")

                try {
                    val modelsResult =
                        if (isLocked) {
                            null
                        } else {
                            // Suspends until network fetch, DB update, and cache init complete.
                            // Returns DownloadModelsResult with scan result to avoid duplicate scanning.
                            initializeModelsUseCase()
                        }

                    collectStartupState(
                        modelsResult = modelsResult,
                        initializationError = null,
                        isVaultLocked = isLocked,
                        isBiometricUnlockPossible = isBioPossible,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorHandler.handleError(
                        "MainViewModel",
                        "Critical failure during app initialization",
                        e,
                        MODEL_INITIALIZATION_ERROR,
                    )
                    collectStartupState(
                        modelsResult = null,
                        initializationError = MODEL_INITIALIZATION_ERROR,
                        isVaultLocked = isLocked,
                        isBiometricUnlockPossible = isBioPossible,
                    )
                }
            }
        }

        private suspend fun collectStartupState(
            modelsResult: DownloadModelsResult?,
            initializationError: String?,
            isVaultLocked: Boolean,
            isBiometricUnlockPossible: Boolean,
        ) {
            val onboardingCompleted = vaultSession.onboardingCompleted()
            getAuthStateUseCase().collect { authState ->
                val vaultAuthUiState = authState.toVaultAuthUiState()
                val dekCached = !vaultSession.isVaultLocked()
                val vaultLockedNow = !dekCached
                _startupState.update {
                    if (it is AppStartupState.Ready) {
                        // Cached DEK is authoritative — auth token refresh must never re-lock an active session.
                        // While unlock work is in flight, keep the gate state stable until commitVaultUnlocked().
                        val nextVaultLocked =
                            when {
                                dekCached -> false
                                it.isUnlocking -> it.isVaultLocked
                                else -> vaultLockedNow
                            }
                        it.copy(
                            authState = vaultAuthUiState,
                            isVaultLocked = nextVaultLocked,
                        )
                    } else {
                        AppStartupState.Ready(
                            authState = vaultAuthUiState,
                            modelsResult = modelsResult,
                            initializationError = initializationError,
                            isVaultLocked = isVaultLocked,
                            isBiometricUnlockPossible = isBiometricUnlockPossible,
                            onboardingCompleted = onboardingCompleted,
                        )
                    }
                }
            }
        }

        fun loginWithGoogle() {
            viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to sign in with Google", "Failed to sign in")) {
                loginWithGoogleUseCase().getOrThrow()
                pendingVaultReconciliationPayload = null
                if (!vaultSession.isVaultLocked()) {
                    // Sign-in can finish after the user already unlocked locally (slow OAuth). Do not
                    // wipe the session or return to the unlock gate.
                    loggingPort.debug(TAG, "loginWithGoogle: vault already unlocked, preserving session")
                    commitVaultUnlocked()
                    _startupState.update {
                        if (it is AppStartupState.Ready) {
                            it.copy(
                                cloudSyncDeferred = false,
                                vaultErrorMessage = null,
                                pendingVaultReconciliation = null,
                            )
                        } else {
                            it
                        }
                    }
                    startCloudListenerIfEligible()
                    return@launch
                }
                // Locked vault: drop cached DEK and defer cloud sync until password unlock + reconcile.
                // Re-check after the slow OAuth hop — unlock may have finished while the browser was open.
                if (!vaultSession.isVaultLocked()) {
                    loggingPort.debug(TAG, "loginWithGoogle: vault unlocked during OAuth, preserving session")
                    commitVaultUnlocked()
                    startCloudListenerIfEligible()
                    return@launch
                }
                vaultSession.clearCachedKey()
                vaultSession.stopCloudListening()
                _startupState.update {
                    if (it is AppStartupState.Ready) {
                        it.copy(
                            isVaultLocked = true,
                            isBiometricUnlockPossible = false,
                            vaultErrorMessage = null,
                            pendingVaultReconciliation = null,
                            cloudSyncDeferred = true,
                        )
                    } else {
                        it
                    }
                }
            }
        }

        fun unlockVault(
            password: String,
            enableBiometric: Boolean = false,
        ) {
            if ((startupState.value as? AppStartupState.Ready)?.isUnlocking == true) return

            viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to unlock vault", "Failed to unlock")) {
                loggingPort.debug(TAG, "unlockVault: Attempting unlock")
                _startupState.update {
                    if (it is AppStartupState.Ready) it.copy(isUnlocking = true) else it
                }

                try {
                    val (saltBase64, salt) = vaultSession.resolveOrCreateSalt()

                    val wrappedDekBase64 = vaultSession.resolveWrappedDek()
                    val verificationCiphertext = vaultSession.resolveVerification()

                    if (wrappedDekBase64.isNullOrBlank()) {
                        // First-time vault creation: generate DEK, wrap with KEK, persist.
                        loggingPort.info(TAG, "unlockVault: No wrapped DEK found, creating new vault")
                        val createResult = createVaultUseCase(password, salt)
                        if (createResult.isFailure) {
                            loggingPort.error(TAG, "unlockVault: Failed to create vault", createResult.exceptionOrNull())
                            _startupState.update {
                                if (it is AppStartupState.Ready) it.copy(vaultErrorMessage = "Failed to initialize vault") else it
                            }
                            return@launch
                        }
                        val wrapped = createResult.getOrThrow()
                        val newWrappedBase64 = vaultSession.encodeBase64(wrapped)
                        vaultSession.persistWrappedDek(newWrappedBase64, salt = saltBase64)
                        vaultSession.ensureVerificationStored(verificationCiphertext)
                    } else {
                        // Existing vault: derive KEK, unwrap DEK, cache.
                        val wrappedBytes = vaultSession.decodeBase64(wrappedDekBase64)
                        val unlockResult = unlockVaultUseCase(password, salt, wrappedBytes)
                        if (unlockResult.isFailure) {
                            loggingPort.error(TAG, "unlockVault: Wrong password (DEK unwrap failed)", unlockResult.exceptionOrNull())
                            _startupState.update {
                                if (it is AppStartupState.Ready) it.copy(vaultErrorMessage = "Incorrect Vault Password") else it
                            }
                            return@launch
                        }

                        if (!verifyMagicOrFail(verificationCiphertext)) {
                            return@launch
                        }
                        vaultSession.ensureVerificationStored(verificationCiphertext)
                    }

                    loggingPort.debug(TAG, "unlockVault: Vault unlocked successfully")

                    val isAuthenticated =
                        (startupState.value as? AppStartupState.Ready)?.authState is VaultAuthUiState.Authenticated
                    if (isAuthenticated) {
                        // Reconcile before touching model defaults — Room may still hold ciphertext
                        // from a different DEK until sync/adoption completes.
                        reconcileWithCloudIfNeeded(password)
                    }

                    val readyAfterReconcile = startupState.value as? AppStartupState.Ready
                    val requestBiometricEnrollment = vaultSession.shouldRequestBiometricEnrollment(enableBiometric)
                    if (requestBiometricEnrollment) {
                        loggingPort.debug(TAG, "unlockVault: Biometric opt-in detected, requesting enrollment")
                        if (readyAfterReconcile?.pendingVaultReconciliation == null) {
                            initializeModelsIfNeeded()
                        }
                        _startupState.update {
                            if (it is AppStartupState.Ready) {
                                it.copy(
                                    isVaultLocked = true,
                                    vaultErrorMessage = null,
                                    isBiometricEnrollmentPending = true,
                                    isUnlocking = false,
                                    unlockSyncProgress = null,
                                    unlockSyncStatus = null,
                                )
                            } else {
                                it
                            }
                        }
                        return@launch
                    }

                    if (readyAfterReconcile?.pendingVaultReconciliation == null) {
                        initializeModelsIfNeeded()
                    }

                    // Open the main app only after model scan completes so DownloadViewModel receives
                    // a populated modelsResult and can auto-start downloads.
                    commitVaultUnlocked()
                } finally {
                    _startupState.update {
                        if (it is AppStartupState.Ready && it.isUnlocking) {
                            it.copy(
                                isUnlocking = false,
                                unlockSyncProgress = null,
                                unlockSyncStatus = null,
                            )
                        } else {
                            it
                        }
                    }
                }
            }
        }

        /**
         * Branches on the detector outcome:
         *  - `AlreadyMatching` / `NoLocalVault` → start cloud sync immediately.
         *  - `NoCloudVault` → bootstrap cloud from local, then start cloud sync.
         *  - `MismatchRequiresClobber` → set `pendingVaultReconciliation` and **do not start
         *    sync** until the user explicitly confirms via [confirmAdoptCloud].
         *  - `PasswordMismatch` → log only; the local unlock has already succeeded against the
         *    local wrappedDek so the user is functional locally.
         */
        private suspend fun reconcileWithCloudIfNeeded(password: String) {
            val outcome = detectVaultReconciliationStateUseCase(password).getOrNull()
            if (outcome == null) {
                loggingPort.error(TAG, "reconcileWithCloudIfNeeded: detector failed; deferring sync this session")
                return
            }
            when (outcome) {
                is VaultReconciliationOutcome.AlreadyMatching,
                is VaultReconciliationOutcome.NoLocalVault,
                -> {
                    startCloudSyncAndListen(initialUpload = true)
                }
                is VaultReconciliationOutcome.NoCloudVault -> {
                    val bootstrap = bootstrapCloudVaultFromLocalUseCase()
                    if (bootstrap.isFailure) {
                        loggingPort.error(
                            TAG,
                            "reconcileWithCloudIfNeeded: bootstrap failed",
                            bootstrap.exceptionOrNull(),
                        )
                    }
                    startCloudSyncAndListen(initialUpload = true)
                }
                is VaultReconciliationOutcome.MismatchRequiresClobber -> {
                    loggingPort.info(
                        TAG,
                        "reconcileWithCloudIfNeeded: cloud DEK differs from local; awaiting user confirmation",
                    )
                    pendingVaultReconciliationPayload = outcome
                    _startupState.update {
                        if (it is AppStartupState.Ready) {
                            it.copy(pendingVaultReconciliation = PendingReconciliation())
                        } else {
                            it
                        }
                    }
                }
                is VaultReconciliationOutcome.PasswordMismatch -> {
                    loggingPort.error(
                        TAG,
                        "reconcileWithCloudIfNeeded: password does not unwrap cloud DEK; deferring sync",
                    )
                    _startupState.update {
                        if (it is AppStartupState.Ready) it.copy(cloudSyncDeferred = true) else it
                    }
                }
            }
        }

        private suspend fun startCloudSyncAndListen(initialUpload: Boolean): Boolean {
            val progressJob =
                viewModelScope.launch {
                    vaultSession.syncProgress.collect { progress ->
                        _startupState.update { state ->
                            if (state is AppStartupState.Ready) {
                                state.copy(
                                    unlockSyncProgress = progress?.fraction,
                                    unlockSyncStatus = progress?.status,
                                )
                            } else {
                                state
                            }
                        }
                    }
                }

            val result = vaultSession.startInitialSync(initialUpload)
            progressJob.cancel()

            val isSuccess = result.isSuccess
            val failureMessage = result.exceptionOrNull()?.message

            if (isSuccess) {
                _startupState.update {
                    if (it is AppStartupState.Ready) {
                        it.copy(
                            cloudSyncDeferred = false,
                            vaultErrorMessage = null,
                            unlockSyncProgress = null,
                            unlockSyncStatus = null,
                        )
                    } else {
                        it
                    }
                }
                return true
            }

            loggingPort.error(TAG, "startCloudSyncAndListen: cloud sync worker failed: $failureMessage")
            _startupState.update {
                if (it is AppStartupState.Ready) {
                    it.copy(
                        cloudSyncDeferred = true,
                        vaultErrorMessage = failureMessage ?: "Failed to start cloud sync",
                        unlockSyncProgress = null,
                        unlockSyncStatus = null,
                    )
                } else {
                    it
                }
            }
            return false
        }

        /**
         * User confirmed the destructive clobber. Runs the adopter (wipes Room, caches cloud
         * DEK, persists cloud metadata, clears the biometric Keystore alias) and then starts
         * the Firestore listener so Room re-populates from cloud.
         */
        fun confirmAdoptCloud() {
            val ready = startupState.value as? AppStartupState.Ready ?: return
            val pending = ready.pendingVaultReconciliation ?: return
            if (pending.isReconciling) return
            val payload = pendingVaultReconciliationPayload ?: return

            viewModelScope.launch(
                errorHandler.coroutineExceptionHandler(
                    TAG,
                    "Failed to adopt cloud vault",
                    "Failed to replace local data with cloud",
                ),
            ) {
                _startupState.update {
                    if (it is AppStartupState.Ready) {
                        it.copy(pendingVaultReconciliation = pending.copy(isReconciling = true))
                    } else {
                        it
                    }
                }
                val result = adoptCloudVaultUseCase(payload)
                if (result.isFailure) {
                    loggingPort.error(TAG, "confirmAdoptCloud: adoption failed", result.exceptionOrNull())
                    _startupState.update {
                        if (it is AppStartupState.Ready) {
                            it.copy(
                                pendingVaultReconciliation = pending.copy(isReconciling = false),
                                vaultErrorMessage = "Failed to replace local data with cloud",
                            )
                        } else {
                            it
                        }
                    }
                    return@launch
                }
                // Skip syncAllLocalDataToCloud — Room is empty after clearAllData; nothing to push.
                pendingVaultReconciliationPayload = null
                if (!startCloudSyncAndListen(initialUpload = false)) {
                    _startupState.update {
                        if (it is AppStartupState.Ready) {
                            it.copy(pendingVaultReconciliation = pending.copy(isReconciling = false))
                        } else {
                            it
                        }
                    }
                    return@launch
                }
                initializeModelsIfNeeded()
                _startupState.update {
                    if (it is AppStartupState.Ready) {
                        it.copy(pendingVaultReconciliation = null, cloudSyncDeferred = false)
                    } else {
                        it
                    }
                }
            }
        }

        /**
         * User declined the clobber. We stay signed in, leave Room intact, and skip cloud sync
         * for this session. The next cold-start unlock will re-detect and re-prompt.
         */
        fun cancelReconciliation() {
            pendingVaultReconciliationPayload = null
            _startupState.update {
                if (it is AppStartupState.Ready) {
                    it.copy(
                        pendingVaultReconciliation = null,
                        cloudSyncDeferred = true,
                    )
                } else {
                    it
                }
            }
        }

        private fun verifyMagicOrFail(verificationCiphertext: String?): Boolean {
            if (vaultSession.verifyMagic(verificationCiphertext)) return true
            loggingPort.error(TAG, "verifyMagicOrFail: Verification mismatch — DEK does not decrypt magic string")
            vaultSession.clearCachedKey()
            _startupState.update {
                if (it is AppStartupState.Ready) it.copy(vaultErrorMessage = "Incorrect Vault Password") else it
            }
            return false
        }

        private fun invalidateStaleBiometricUnlock(failureMessage: String) {
            loggingPort.error(TAG, "invalidateStaleBiometricUnlock: $failureMessage")
            vaultSession.clearKeyAndBiometricValue()
            _startupState.update {
                if (it is AppStartupState.Ready) {
                    it.copy(
                        isVaultLocked = true,
                        isBiometricUnlockPossible = false,
                        vaultErrorMessage = failureMessage,
                    )
                } else {
                    it
                }
            }
        }

        private suspend fun startCloudListenerIfEligible() {
            val ready = startupState.value as? AppStartupState.Ready ?: return
            if (ready.authState !is VaultAuthUiState.Authenticated) return
            if (ready.pendingVaultReconciliation != null || ready.cloudSyncDeferred) return
            startCloudSyncAndListen(initialUpload = false)
        }

        fun getBiometricEncryptCipher(): Cipher? = getBiometricEncryptCipherUseCase()

        /**
         * Persists the currently cached DEK behind the biometric-bound AndroidKeyStore key.
         */
        fun finalizeBiometricEnrollment(cipher: Cipher) {
            viewModelScope.launch {
                val dek = vaultSession.getCachedDek()
                if (dek == null) {
                    loggingPort.error(TAG, "finalizeBiometricEnrollment: vault is locked, cannot enroll")
                    _startupState.update {
                        if (it is AppStartupState.Ready) it.copy(isBiometricEnrollmentPending = false, isVaultLocked = false) else it
                    }
                    return@launch
                }
                val result = storeBiometricDekUseCase(dek, cipher)
                _startupState.update {
                    if (it is AppStartupState.Ready) {
                        val bioPossibleNow =
                            result.isSuccess && vaultSession.isBiometricAvailableWithSecureValue()
                        if (!result.isSuccess) {
                            loggingPort.error(
                                TAG,
                                "finalizeBiometricEnrollment: Failed to store wrapped DEK",
                                result.exceptionOrNull(),
                            )
                        }
                        it.copy(
                            isBiometricEnrollmentPending = false,
                            isBiometricUnlockPossible = bioPossibleNow || it.isBiometricUnlockPossible,
                            isVaultLocked = false,
                        )
                    } else {
                        it
                    }
                }
            }
        }

        fun cancelBiometricEnrollment() {
            _startupState.update {
                if (it is AppStartupState.Ready) {
                    it.copy(
                        isBiometricEnrollmentPending = false,
                        isVaultLocked = false,
                    )
                } else {
                    it
                }
            }
        }

        fun getBiometricDecryptCipher(): Cipher? {
            val cipher = getBiometricDecryptCipherUseCase()
            if (cipher == null && vaultSession.hasBiometricSecureValue()) {
                loggingPort.error(
                    TAG,
                    "getBiometricDecryptCipher: Cipher is null but has secure value. Invalidate stale biometric credentials.",
                )
                invalidateStaleBiometricUnlock("Biometric key invalidated. Enter your vault password.")
            }
            return cipher
        }

        /**
         * Biometric unlock restores the local DEK cache after proving it still decrypts the
         * vault verification anchor (cloud first, then local). Full reconciliation (bootstrap,
         * clobber, initial upload) remains password-only. A stale biometric blob is cleared and
         * the user is asked for their vault password.
         */
        fun unlockWithBiometricCipher(cipher: Cipher) {
            if ((startupState.value as? AppStartupState.Ready)?.isUnlocking == true) return

            viewModelScope.launch(
                errorHandler.coroutineExceptionHandler(
                    TAG,
                    "Failed to unlock vault with biometrics",
                    "Failed to unlock",
                ),
            ) {
                _startupState.update {
                    if (it is AppStartupState.Ready) it.copy(isUnlocking = true) else it
                }

                try {
                    val dek = decryptBiometricDekUseCase(cipher)
                    if (dek == null) {
                        loggingPort.error(TAG, "unlockWithBiometricCipher: Failed to unwrap DEK")
                        invalidateStaleBiometricUnlock(
                            "Could not unlock with biometrics. Enter your vault password.",
                        )
                        return@launch
                    }
                    val cached = vaultSession.cacheDek(dek)
                    if (cached.isFailure) {
                        loggingPort.error(
                            TAG,
                            "unlockWithBiometricCipher: Failed to cache DEK",
                            cached.exceptionOrNull(),
                        )
                        invalidateStaleBiometricUnlock(
                            "Could not unlock with biometrics. Enter your vault password.",
                        )
                        return@launch
                    }

                    val biometricFailureMessage =
                        "Could not unlock with biometrics. Enter your vault password."
                    if (!vaultSession.verifyMagic(vaultSession.resolveVerificationPreferCloud(), requireVerification = true)) {
                        invalidateStaleBiometricUnlock(biometricFailureMessage)
                        return@launch
                    }

                    initializeModelsIfNeeded()

                    _startupState.update {
                        if (it is AppStartupState.Ready) it.copy(isVaultLocked = false, vaultErrorMessage = null) else it
                    }

                    startCloudListenerIfEligible()
                } finally {
                    _startupState.update {
                        if (it is AppStartupState.Ready) it.copy(isUnlocking = false) else it
                    }
                }
            }
        }

        suspend fun refreshDownloadModels() {
            initializeModelsUseCase()
        }

        private fun commitVaultUnlocked() {
            _startupState.update {
                if (it is AppStartupState.Ready) {
                    it.copy(
                        isVaultLocked = false,
                        vaultErrorMessage = if (it.cloudSyncDeferred) it.vaultErrorMessage else null,
                        isUnlocking = false,
                        unlockSyncProgress = null,
                        unlockSyncStatus = null,
                    )
                } else {
                    it
                }
            }
        }

        private suspend fun initializeModelsIfNeeded() {
            val readyState = startupState.value as? AppStartupState.Ready
            if (readyState?.modelsResult != null && readyState.initializationError == null) {
                return
            }

            try {
                val modelsResult = initializeModelsUseCase()
                _startupState.update {
                    if (it is AppStartupState.Ready) {
                        it.copy(
                            modelsResult = modelsResult,
                            initializationError = null,
                        )
                    } else {
                        it
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorHandler.handleError(
                    TAG,
                    "Failed to initialize models after vault unlock",
                    e,
                    MODEL_INITIALIZATION_ERROR,
                )
                _startupState.update {
                    if (it is AppStartupState.Ready) {
                        it.copy(initializationError = MODEL_INITIALIZATION_ERROR)
                    } else {
                        it
                    }
                }
            }
        }
    }
