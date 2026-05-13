package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.port.repository.CleanupRepository
import com.browntowndev.pocketcrew.domain.port.security.BiometricPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.port.security.VaultMetadataPort
import com.browntowndev.pocketcrew.domain.security.VaultKeystoreAliases
import javax.inject.Inject

/**
 * Adopts the cloud-side vault as the source of truth on this device. Destructive — wipes all
 * local user data via [CleanupRepository.clearAllData], caches the cloud DEK, persists the
 * cloud salt / wrappedDek / verification locally, and clears the AndroidKeyStore-bound DEK
 * alias so a stale biometric blob (wrapping the OLD plaintext DEK) cannot decrypt with the
 * wrong key on the next biometric unlock.
 *
 * Caller MUST surface user confirmation before invoking this use case — the destructive
 * `clearAllData` call cannot be undone.
 */
class AdoptCloudVaultUseCase @Inject constructor(
    private val cryptoManager: CryptoManagerPort,
    private val vaultMetadataPort: VaultMetadataPort,
    private val cleanupRepository: CleanupRepository,
    private val biometricPort: BiometricPort,
) {
    suspend operator fun invoke(
        payload: VaultReconciliationOutcome.MismatchRequiresClobber,
    ): Result<Unit> = runCatching {
        cleanupRepository.clearAllData()
        cryptoManager.cacheDek(payload.cloudDek).getOrThrow()
        vaultMetadataPort.saveVaultSalt(payload.cloudSalt)
        vaultMetadataPort.saveWrappedDek(payload.cloudWrappedDek)
        vaultMetadataPort.saveVaultVerificationData(payload.cloudVerification)
        biometricPort.clearSecureValue(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY)
    }
}
