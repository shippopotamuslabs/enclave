package com.browntowndev.pocketcrew.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.pocketcrew.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.pocketcrew.domain.usecase.download.InitializeModelsUseCase
import com.browntowndev.pocketcrew.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.BiometricPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.usecase.security.UnlockVaultUseCase
import javax.crypto.Cipher

sealed interface AppStartupState {
    data object Loading : AppStartupState

    data class Ready(
        val authState: AuthState,
        val modelsResult: DownloadModelsResult?,
        val initializationError: String? = null,
        val vaultErrorMessage: String? = null,
        val isVaultLocked: Boolean = false,
        val isBiometricUnlockPossible: Boolean = false,
        val pendingBiometricEnrollmentPassword: String? = null,
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
class MainViewModel
    @Inject
    constructor(
        private val getAuthStateUseCase: GetAuthStateUseCase,
        private val loginWithGoogleUseCase: LoginWithGoogleUseCase,
        private val initializeModelsUseCase: InitializeModelsUseCase,
        private val cryptoManager: CryptoManagerPort,
        private val cloudSyncPort: CloudSyncPort,
        private val biometricPort: BiometricPort,
        private val unlockVaultUseCase: UnlockVaultUseCase,
        private val loggingPort: LoggingPort,
        private val errorHandler: ViewModelErrorHandler,
    ) : ViewModel() {
        companion object {
            private const val TAG = "MainViewModel"
            private const val VAULT_PASSWORD_SECURE_KEY = "vault_password"
        }
        private val _startupState = MutableStateFlow<AppStartupState>(AppStartupState.Loading)
        val startupState: StateFlow<AppStartupState> = _startupState.asStateFlow()

        init {
            initializeApp()
        }

        @Suppress("TooGenericExceptionCaught")
        private fun initializeApp() {
            viewModelScope.launch {
                try {
                    // Suspends until network fetch, DB update, and cache init complete
                    // Returns DownloadModelsResult with scan result to avoid duplicate scanning
                    val modelsResult = initializeModelsUseCase()
                    
                    if (!cryptoManager.hasCachedKey()) {
                        val saltResult = cloudSyncPort.getVaultSalt()
                        if (saltResult.isSuccess) {
                            val cloudSalt = saltResult.getOrNull()
                            if (cloudSalt != null) {
                                // Salt found
                            }
                        }
                    }

                    val isLocked = !cryptoManager.hasCachedKey()
                    val isBioPossible = isLocked && 
                        biometricPort.isBiometricAvailable() && 
                        biometricPort.hasSecureValue(VAULT_PASSWORD_SECURE_KEY)

                    loggingPort.debug(TAG, "initializeApp: isLocked=$isLocked, isBioPossible=$isBioPossible")

                    getAuthStateUseCase().collect { authState ->
                        _startupState.update { 
                            if (it is AppStartupState.Ready) {
                                it.copy(authState = authState)
                            } else {
                                AppStartupState.Ready(
                                    authState = authState,
                                    modelsResult = modelsResult,
                                    isVaultLocked = isLocked,
                                    isBiometricUnlockPossible = isBioPossible
                                ) 
                            }
                        }
                    }
                } catch (e: Exception) {
                    errorHandler.handleError(
                        "MainViewModel",
                        "Critical failure during app initialization",
                        e,
                        "Failed to initialize models",
                    )
                    getAuthStateUseCase().collect { authState ->
                        _startupState.update {
                            AppStartupState.Ready(
                                authState = authState,
                                modelsResult = null,
                                initializationError = "Failed to initialize models",
                                isVaultLocked = !cryptoManager.hasCachedKey()
                            )
                        }
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
                if (it is AppStartupState.Ready) it.copy(isVaultLocked = false, vaultErrorMessage = null) else it 
            }
        }

        fun unlockVault(password: String, enableBiometric: Boolean = false) {
            viewModelScope.launch {
                loggingPort.debug(TAG, "unlockVault: Attempting unlock")
                
                // 1. Get Salt
                val saltResult = cloudSyncPort.getVaultSalt()
                var saltBase64 = saltResult.getOrNull()
                
                if (saltBase64 == null) {
                    loggingPort.info(TAG, "unlockVault: No salt found in cloud, initializing new vault")
                    val newSalt = cryptoManager.generateSalt()
                    saltBase64 = android.util.Base64.encodeToString(newSalt, android.util.Base64.NO_WRAP)
                    val saveResult = cloudSyncPort.updateVaultSalt(saltBase64)
                    if (saveResult.isFailure) {
                        loggingPort.error(TAG, "unlockVault: Failed to save new salt to cloud", saveResult.exceptionOrNull())
                        return@launch
                    }
                }

                val salt = android.util.Base64.decode(saltBase64, android.util.Base64.NO_WRAP)
                
                // 2. Verification Check (Magic String)
                val verificationResult = cloudSyncPort.getVaultVerificationData()
                val verificationCiphertext = verificationResult.getOrNull()

                // First, derive the candidate key
                val deriveResult = unlockVaultUseCase(password, salt)
                if (deriveResult.isFailure) {
                    loggingPort.error(TAG, "unlockVault: Failed to derive key", deriveResult.exceptionOrNull())
                    return@launch
                }

                if (verificationCiphertext != null) {
                    // Try to decrypt the magic string to verify the password
                    val ciphertextBytes = android.util.Base64.decode(verificationCiphertext, android.util.Base64.DEFAULT)
                    val decryptResult = cryptoManager.decrypt(ciphertextBytes)
                    
                    if (decryptResult.isFailure || decryptResult.getOrNull() != com.browntowndev.pocketcrew.domain.usecase.auth.LoginWithGoogleUseCase.VAULT_VERIFICATION_MAGIC_STRING) {
                        loggingPort.error(TAG, "unlockVault: Wrong password (verification mismatch)")
                        cryptoManager.clearKey() // Critical: clear the wrong key
                        _startupState.update { 
                            if (it is AppStartupState.Ready) it.copy(vaultErrorMessage = "Incorrect Vault Password") else it 
                        }
                        return@launch
                    }
                    loggingPort.debug(TAG, "unlockVault: Password verified successfully")
                } else {
                    // Initializing vault verification
                    loggingPort.info(TAG, "unlockVault: Initializing vault verification data")
                    val magicString = com.browntowndev.pocketcrew.domain.usecase.auth.LoginWithGoogleUseCase.VAULT_VERIFICATION_MAGIC_STRING
                    val encryptResult = cryptoManager.encrypt(magicString)
                    encryptResult.onSuccess { ciphertext ->
                        val base64Ciphertext = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
                        cloudSyncPort.updateVaultVerificationData(base64Ciphertext)
                    }
                }

                // 3. Finalize
                loggingPort.debug(TAG, "unlockVault: Vault unlocked successfully")
                
                if (enableBiometric && biometricPort.isBiometricAvailable()) {
                    loggingPort.debug(TAG, "unlockVault: Biometric opt-in detected, requesting enrollment")
                    _startupState.update { 
                        if (it is AppStartupState.Ready) it.copy(
                            isVaultLocked = false, 
                            vaultErrorMessage = null,
                            pendingBiometricEnrollmentPassword = password
                        ) else it 
                    }
                    return@launch
                }

                _startupState.update { 
                    if (it is AppStartupState.Ready) it.copy(isVaultLocked = false, vaultErrorMessage = null) else it 
                }
            }
        }

        fun getBiometricEncryptCipher(): Cipher? {
            return (biometricPort as? com.browntowndev.pocketcrew.core.data.security.AndroidBiometricManager)
                ?.getEncryptCipher(VAULT_PASSWORD_SECURE_KEY)
        }

        fun finalizeBiometricEnrollment(password: String, cipher: Cipher) {
            viewModelScope.launch {
                val manager = biometricPort as? com.browntowndev.pocketcrew.core.data.security.AndroidBiometricManager
                manager?.storeSecureValueWithCipher(VAULT_PASSWORD_SECURE_KEY, password, cipher)
                _startupState.update { 
                    if (it is AppStartupState.Ready) it.copy(pendingBiometricEnrollmentPassword = null) else it 
                }
            }
        }
        
        fun cancelBiometricEnrollment() {
             _startupState.update { 
                 if (it is AppStartupState.Ready) it.copy(pendingBiometricEnrollmentPassword = null) else it 
             }
        }

        fun getBiometricDecryptCipher(): Cipher? {
            return (biometricPort as? com.browntowndev.pocketcrew.core.data.security.AndroidBiometricManager)
                ?.getDecryptCipher(VAULT_PASSWORD_SECURE_KEY)
        }

        fun unlockWithBiometricCipher(cipher: Cipher) {
            viewModelScope.launch {
                val manager = biometricPort as? com.browntowndev.pocketcrew.core.data.security.AndroidBiometricManager
                val password = manager?.decryptWithCipher(VAULT_PASSWORD_SECURE_KEY, cipher)
                
                if (password != null) {
                    loggingPort.debug(TAG, "unlockWithBiometricCipher: Successfully decrypted password, unlocking vault")
                    unlockVault(password)
                } else {
                    loggingPort.error(TAG, "unlockWithBiometricCipher: Failed to decrypt password")
                }
            }
        }
    }
