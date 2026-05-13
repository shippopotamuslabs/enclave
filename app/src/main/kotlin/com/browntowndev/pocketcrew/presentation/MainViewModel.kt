package com.browntowndev.pocketcrew.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.core.data.security.AndroidBiometricManager
import com.browntowndev.pocketcrew.core.ui.component.vault.VaultAuthUiState
import com.browntowndev.pocketcrew.core.ui.component.vault.toVaultAuthUiState
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.port.security.VaultMetadataPort
import com.browntowndev.pocketcrew.domain.security.VaultKeystoreAliases
import com.browntowndev.pocketcrew.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.pocketcrew.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.pocketcrew.domain.usecase.auth.LoginWithGoogleUseCase.Companion.VAULT_VERIFICATION_MAGIC_STRING
import com.browntowndev.pocketcrew.domain.usecase.download.InitializeModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.UnlockVaultUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.VaultReconciliationOutcome
import com.browntowndev.pocketcrew.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    ) : AppStartupState {
        val initialRoute =
            if (initializationError != null ||
                modelsResult?.modelsToDownload?.isNotEmpty() == true
            ) {
                Routes.MODEL_DOWNLOAD
            } else {
                Routes.CHAT
            }
    }
}

@HiltViewModel
@Suppress("MaxLineLength", "LongParameterList", "TooManyFunctions")
class MainViewModel
    @Inject
    constructor(
        private val getAuthStateUseCase: GetAuthStateUseCase,
        private val loginWithGoogleUseCase: LoginWithGoogleUseCase,
        private val initializeModelsUseCase: InitializeModelsUseCase,
        private val cryptoManager: CryptoManagerPort,
        private val cloudSyncPort: CloudSyncPort,
        private val vaultMetadataPort: VaultMetadataPort,
        private val biometricManager: AndroidBiometricManager,
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
                val isLocked = !cryptoManager.hasCachedKey()
                val isBioPossible = isLocked &&
                    biometricManager.isBiometricAvailable() &&
                    biometricManager.hasSecureValue(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY)

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
            getAuthStateUseCase().collect { authState ->
                val vaultAuthUiState = authState.toVaultAuthUiState()
                _startupState.update {
                    if (it is AppStartupState.Ready) {
                        it.copy(authState = vaultAuthUiState)
                    } else {
                        AppStartupState.Ready(
                            authState = vaultAuthUiState,
                            modelsResult = modelsResult,
                            initializationError = initializationError,
                            isVaultLocked = isVaultLocked,
                            isBiometricUnlockPossible = isBiometricUnlockPossible,
                        )
                    }
                }
            }
        }

        fun loginWithGoogle() {
            viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to sign in with Google", "Failed to sign in")) {
                loginWithGoogleUseCase().getOrThrow()
            }
        }

        fun skipLogin() {
            _startupState.update {
                if (it is AppStartupState.Ready) {
                    it.copy(
                        isVaultLocked = !cryptoManager.hasCachedKey(),
                        vaultErrorMessage = null,
                    )
                } else {
                    it
                }
            }
        }

        fun unlockVault(password: String, enableBiometric: Boolean = false) {
            if ((startupState.value as? AppStartupState.Ready)?.isUnlocking == true) return

            viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to unlock vault", "Failed to unlock")) {
                loggingPort.debug(TAG, "unlockVault: Attempting unlock")
                _startupState.update {
                    if (it is AppStartupState.Ready) it.copy(isUnlocking = true) else it
                }

                try {
                    val (saltBase64, salt) = resolveOrCreateSalt()

                    val wrappedDekBase64 = resolveWrappedDek()
                    val verificationCiphertext = resolveVerification()

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
                        val newWrappedBase64 = android.util.Base64.encodeToString(wrapped, android.util.Base64.NO_WRAP)
                        persistWrappedDek(newWrappedBase64, salt = saltBase64)
                        ensureVerificationStored(verificationCiphertext)
                    } else {
                        // Existing vault: derive KEK, unwrap DEK, cache.
                        val wrappedBytes = android.util.Base64.decode(wrappedDekBase64, android.util.Base64.NO_WRAP)
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
                        ensureVerificationStored(verificationCiphertext)
                    }

                    loggingPort.debug(TAG, "unlockVault: Vault unlocked successfully")

                    initializeModelsIfNeeded()

                    if (enableBiometric && biometricManager.isBiometricAvailable()) {
                        loggingPort.debug(TAG, "unlockVault: Biometric opt-in detected, requesting enrollment")
                        _startupState.update {
                            if (it is AppStartupState.Ready) it.copy(
                                isVaultLocked = false,
                                vaultErrorMessage = null,
                                isBiometricEnrollmentPending = true,
                            ) else it
                        }
                        return@launch
                    }

                    _startupState.update {
                        if (it is AppStartupState.Ready) it.copy(isVaultLocked = false, vaultErrorMessage = null) else it
                    }

                    if ((startupState.value as? AppStartupState.Ready)?.authState is VaultAuthUiState.Authenticated) {
                        reconcileWithCloudIfNeeded(password)
                    }
                } finally {
                    _startupState.update {
                        if (it is AppStartupState.Ready) it.copy(isUnlocking = false) else it
                    }
                }
            }
        }

        private suspend fun resolveOrCreateSalt(): Pair<String, ByteArray> {
            var saltBase64 = vaultMetadataPort.getVaultSalt()?.takeIf { it.isNotBlank() }
            if (saltBase64 == null) {
                saltBase64 = cloudSyncPort.getVaultSalt().getOrNull()?.takeIf { it.isNotBlank() }
                if (saltBase64 != null) {
                    vaultMetadataPort.saveVaultSalt(saltBase64)
                }
            }
            if (saltBase64 == null) {
                loggingPort.info(TAG, "resolveOrCreateSalt: No salt anywhere, generating new")
                val newSalt = cryptoManager.generateSalt()
                saltBase64 = android.util.Base64.encodeToString(newSalt, android.util.Base64.NO_WRAP)
                vaultMetadataPort.saveVaultSalt(saltBase64)
                runCatching { cloudSyncPort.updateVaultSalt(saltBase64) }
            }
            val salt = android.util.Base64.decode(saltBase64, android.util.Base64.NO_WRAP)
            return saltBase64 to salt
        }

        private suspend fun resolveWrappedDek(): String? {
            var blob = vaultMetadataPort.getWrappedDek()?.takeIf { it.isNotBlank() }
            if (blob == null) {
                blob = cloudSyncPort.getWrappedDek().getOrNull()?.takeIf { it.isNotBlank() }
                if (blob != null) {
                    vaultMetadataPort.saveWrappedDek(blob)
                }
            }
            return blob
        }

        private suspend fun resolveVerification(): String? {
            var verification = vaultMetadataPort.getVaultVerificationData()?.takeIf { it.isNotBlank() }
            if (verification == null) {
                verification = cloudSyncPort.getVaultVerificationData().getOrNull()?.takeIf { it.isNotBlank() }
                if (verification != null) {
                    vaultMetadataPort.saveVaultVerificationData(verification)
                }
            }
            return verification
        }

        private suspend fun persistWrappedDek(wrappedBase64: String, salt: String) {
            vaultMetadataPort.saveWrappedDek(wrappedBase64)
            runCatching { cloudSyncPort.uploadVaultMetadata(
                com.browntowndev.pocketcrew.domain.model.security.VaultMetadata(
                    salt = salt,
                    verification = null,
                    wrappedDek = wrappedBase64,
                ),
            ) }
        }

        private suspend fun ensureVerificationStored(existing: String?) {
            if (existing != null) return
            val encryptResult = cryptoManager.encrypt(VAULT_VERIFICATION_MAGIC_STRING)
            encryptResult.onSuccess { ciphertext ->
                val base64Ciphertext = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
                vaultMetadataPort.saveVaultVerificationData(base64Ciphertext)
                runCatching { cloudSyncPort.updateVaultVerificationData(base64Ciphertext) }
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
                is VaultReconciliationOutcome.NoLocalVault -> {
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

        private suspend fun startCloudSyncAndListen(initialUpload: Boolean) {
            if (initialUpload) {
                runCatching { cloudSyncPort.syncAllLocalDataToCloud() }
            }
            cloudSyncPort.startListening()
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
                cloudSyncPort.startListening()
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

        @Suppress("ReturnCount")
        private fun verifyMagicOrFail(verificationCiphertext: String?): Boolean {
            if (verificationCiphertext == null) return true
            val ciphertextBytes = android.util.Base64.decode(verificationCiphertext, android.util.Base64.DEFAULT)
            val decryptResult = cryptoManager.decrypt(ciphertextBytes)
            if (decryptResult.isFailure || decryptResult.getOrNull() != VAULT_VERIFICATION_MAGIC_STRING) {
                loggingPort.error(TAG, "verifyMagicOrFail: Verification mismatch — DEK does not decrypt magic string")
                cryptoManager.clearKey()
                _startupState.update {
                    if (it is AppStartupState.Ready) it.copy(vaultErrorMessage = "Incorrect Vault Password") else it
                }
                return false
            }
            return true
        }

        fun getBiometricEncryptCipher(): Cipher? =
            biometricManager.getEncryptCipher(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY)

        /**
         * Persists the currently cached DEK behind the biometric-bound AndroidKeyStore key.
         */
        fun finalizeBiometricEnrollment(cipher: Cipher) {
            viewModelScope.launch {
                val dek = cryptoManager.getCachedDek()
                if (dek == null) {
                    loggingPort.error(TAG, "finalizeBiometricEnrollment: vault is locked, cannot enroll")
                    _startupState.update {
                        if (it is AppStartupState.Ready) it.copy(isBiometricEnrollmentPending = false) else it
                    }
                    return@launch
                }
                val result = biometricManager.storeSecureBytesWithCipher(
                    VaultKeystoreAliases.VAULT_DEK_SECURE_KEY,
                    dek,
                    cipher,
                )
                _startupState.update {
                    if (it is AppStartupState.Ready) {
                        if (!result.isSuccess) {
                            loggingPort.error(
                                TAG,
                                "finalizeBiometricEnrollment: Failed to store wrapped DEK",
                                result.exceptionOrNull(),
                            )
                        }
                        it.copy(isBiometricEnrollmentPending = false)
                    } else {
                        it
                    }
                }
            }
        }

        fun cancelBiometricEnrollment() {
            _startupState.update {
                if (it is AppStartupState.Ready) it.copy(isBiometricEnrollmentPending = false) else it
            }
        }

        fun getBiometricDecryptCipher(): Cipher? =
            biometricManager.getDecryptCipher(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY)

        /**
         * Biometric unlock only restores the local DEK cache. Cloud sync and listener startup
         * remain gated behind password-based reconciliation so a stale biometric blob cannot
         * pull down cloud state before the vault has been re-authenticated.
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
                    val dek = biometricManager.decryptBytesWithCipher(
                        VaultKeystoreAliases.VAULT_DEK_SECURE_KEY,
                        cipher,
                    )
                    if (dek == null) {
                        loggingPort.error(TAG, "unlockWithBiometricCipher: Failed to unwrap DEK")
                        _startupState.update {
                            if (it is AppStartupState.Ready) {
                                it.copy(vaultErrorMessage = "Could not unlock with biometrics. Enter your vault password.")
                            } else {
                                it
                            }
                        }
                        return@launch
                    }
                    val cached = cryptoManager.cacheDek(dek)
                    if (cached.isFailure) {
                        loggingPort.error(
                            TAG,
                            "unlockWithBiometricCipher: Failed to cache DEK",
                            cached.exceptionOrNull(),
                        )
                        _startupState.update {
                            if (it is AppStartupState.Ready) {
                                it.copy(vaultErrorMessage = "Could not unlock with biometrics. Enter your vault password.")
                            } else {
                                it
                            }
                        }
                        return@launch
                    }

                    initializeModelsIfNeeded()

                    _startupState.update {
                        if (it is AppStartupState.Ready) it.copy(isVaultLocked = false, vaultErrorMessage = null) else it
                    }
                } finally {
                    _startupState.update {
                        if (it is AppStartupState.Ready) it.copy(isUnlocking = false) else it
                    }
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
