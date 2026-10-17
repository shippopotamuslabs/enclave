package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.domain.model.security.VaultMetadata
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.port.repository.SyncOrchestrator
import com.browntowndev.enclave.domain.port.repository.SyncProgress
import com.browntowndev.enclave.domain.port.security.BiometricPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import com.browntowndev.enclave.domain.security.VaultKeystoreAliases
import com.browntowndev.enclave.domain.usecase.auth.LoginWithGoogleUseCase.Companion.VAULT_VERIFICATION_MAGIC_STRING
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Vault/session operations behind `MainViewModel` (ENC-24).
 *
 * Owns every repository/port dependency the app-startup ViewModel used to inject directly:
 * local/cloud vault metadata resolution and persistence, DEK cache state, biometric
 * availability, onboarding flag reads, and initial cloud sync. The ViewModel keeps only
 * UI-state orchestration and depends on this interactor plus single-purpose use cases.
 */
class VaultSessionInteractor
    @Inject
    constructor(
        private val cryptoManager: CryptoManagerPort,
        private val cloudSyncPort: CloudSyncPort,
        private val vaultMetadataPort: VaultMetadataPort,
        private val biometricPort: BiometricPort,
        private val settingsRepository: SettingsRepository,
        private val syncOrchestrator: SyncOrchestrator,
        private val loggingPort: LoggingPort,
    ) {
        fun isVaultLocked(): Boolean = !cryptoManager.hasCachedKey()

        fun isBiometricUnlockPossible(isLocked: Boolean): Boolean =
            isLocked &&
                biometricPort.isBiometricAvailable() &&
                biometricPort.hasSecureValue(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY)

        fun shouldRequestBiometricEnrollment(enableBiometric: Boolean): Boolean =
            enableBiometric &&
                biometricPort.isBiometricAvailable() &&
                !biometricPort.hasSecureValue(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY)

        fun isBiometricAvailableWithSecureValue(): Boolean =
            biometricPort.isBiometricAvailable() &&
                biometricPort.hasSecureValue(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY)

        fun hasBiometricSecureValue(): Boolean =
            biometricPort.hasSecureValue(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY)

        fun clearCachedKey() = cryptoManager.clearKey()

        /** Drops the cached DEK and any biometric-wrapped copy (stale biometric invalidation). */
        fun clearKeyAndBiometricValue() {
            cryptoManager.clearKey()
            biometricPort.clearSecureValue(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY)
        }

        fun stopCloudListening() = cloudSyncPort.stopListening()

        fun getCachedDek(): ByteArray? = cryptoManager.getCachedDek()

        fun cacheDek(dek: ByteArray): Result<Unit> = cryptoManager.cacheDek(dek)

        fun encodeBase64(data: ByteArray): String = cryptoManager.encodeBase64(data)

        fun decodeBase64(base64: String): ByteArray = cryptoManager.decodeBase64(base64)

        suspend fun onboardingCompleted(): Boolean =
            settingsRepository
                .settingsFlow
                .first()
                .onboardingCompleted

        val syncProgress: Flow<SyncProgress?>
            get() = syncOrchestrator.syncProgress

        suspend fun startInitialSync(initialUpload: Boolean): Result<Unit> =
            syncOrchestrator.startInitialSync(initialUpload)

        /** Local salt first, then cloud (persisting locally), else generate and persist everywhere. */
        suspend fun resolveOrCreateSalt(): Pair<String, ByteArray> {
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
                saltBase64 = cryptoManager.encodeBase64(newSalt)
                vaultMetadataPort.saveVaultSalt(saltBase64)
                runCatching { cloudSyncPort.updateVaultSalt(saltBase64) }
            }
            val salt = cryptoManager.decodeBase64(saltBase64)
            return saltBase64 to salt
        }

        /** Local wrapped DEK first, then cloud (persisting locally). Null means no vault yet. */
        suspend fun resolveWrappedDek(): String? {
            var blob = vaultMetadataPort.getWrappedDek()?.takeIf { it.isNotBlank() }
            if (blob == null) {
                blob = cloudSyncPort.getWrappedDek().getOrNull()?.takeIf { it.isNotBlank() }
                if (blob != null) {
                    vaultMetadataPort.saveWrappedDek(blob)
                }
            }
            return blob
        }

        /** Local verification ciphertext first, then cloud (persisting locally). */
        suspend fun resolveVerification(): String? {
            var verification = vaultMetadataPort.getVaultVerificationData()?.takeIf { it.isNotBlank() }
            if (verification == null) {
                verification = cloudSyncPort.getVaultVerificationData().getOrNull()?.takeIf { it.isNotBlank() }
                if (verification != null) {
                    vaultMetadataPort.saveVaultVerificationData(verification)
                }
            }
            return verification
        }

        /** Cloud anchor first so biometric unlock proves the DEK still matches remote vault state. */
        suspend fun resolveVerificationPreferCloud(): String? {
            val verification = cloudSyncPort.getVaultVerificationData().getOrNull()?.takeIf { it.isNotBlank() }
            if (verification != null) {
                vaultMetadataPort.saveVaultVerificationData(verification)
                return verification
            }
            return vaultMetadataPort.getVaultVerificationData()?.takeIf { it.isNotBlank() }
        }

        /** Persists a freshly wrapped DEK locally and uploads the vault metadata anchor. */
        suspend fun persistWrappedDek(
            wrappedBase64: String,
            salt: String,
        ) {
            vaultMetadataPort.saveWrappedDek(wrappedBase64)
            runCatching {
                cloudSyncPort.uploadVaultMetadata(
                    VaultMetadata(
                        salt = salt,
                        verification = null,
                        wrappedDek = wrappedBase64,
                    ),
                )
            }
        }

        /** Encrypts and stores the verification magic string when no anchor exists yet. */
        suspend fun ensureVerificationStored(existing: String?) {
            if (existing != null) return
            val encryptResult = cryptoManager.encrypt(VAULT_VERIFICATION_MAGIC_STRING)
            encryptResult.onSuccess { ciphertext ->
                val base64Ciphertext = cryptoManager.encodeBase64(ciphertext)
                vaultMetadataPort.saveVaultVerificationData(base64Ciphertext)
                runCatching { cloudSyncPort.updateVaultVerificationData(base64Ciphertext) }
            }
        }

        /**
         * True when the cached DEK decrypts [verificationCiphertext] to the magic string.
         * A missing anchor passes unless [requireVerification] demands one (biometric unlock).
         */
        fun verifyMagic(
            verificationCiphertext: String?,
            requireVerification: Boolean = false,
        ): Boolean {
            if (verificationCiphertext == null) return !requireVerification
            val ciphertextBytes = cryptoManager.decodeBase64(verificationCiphertext)
            val decryptResult = cryptoManager.decrypt(ciphertextBytes)
            return decryptResult.isSuccess && decryptResult.getOrNull() == VAULT_VERIFICATION_MAGIC_STRING
        }

        private companion object {
            private const val TAG = "VaultSessionInteractor"
        }
    }
