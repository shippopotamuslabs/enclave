package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import javax.inject.Inject

/**
 * Read-only classification of the local-vs-cloud vault state. Never mutates anything — does
 * not cache DEKs, does not write to local storage, does not call cloud writes. Pure analysis.
 *
 * The caller is expected to act on the returned [VaultReconciliationOutcome]:
 *  - [VaultReconciliationOutcome.AlreadyMatching] / [VaultReconciliationOutcome.NoLocalVault]
 *    → start cloud sync immediately.
 *  - [VaultReconciliationOutcome.NoCloudVault] → run [BootstrapCloudVaultFromLocalUseCase].
 *  - [VaultReconciliationOutcome.PasswordMismatch] → surface "Incorrect Vault Password".
 *  - [VaultReconciliationOutcome.MismatchRequiresClobber] → MUST surface user confirmation
 *    before invoking [AdoptCloudVaultUseCase] (destructive).
 */
open class DetectVaultReconciliationStateUseCase
    @Inject
    constructor(
        private val cryptoManager: CryptoManagerPort,
        private val vaultMetadataPort: VaultMetadataPort,
        private val cloudSyncPort: CloudSyncPort,
    ) {
        @Suppress("ReturnCount")
        open suspend operator fun invoke(password: String): Result<VaultReconciliationOutcome> =
            runCatching {
                val cloudMetadata =
                    cloudSyncPort.fetchVaultMetadata().getOrNull()
                        ?: return@runCatching VaultReconciliationOutcome.NoCloudVault
                val cloudSalt =
                    cloudMetadata.salt.takeIf { it.isNotBlank() }
                        ?: return@runCatching VaultReconciliationOutcome.NoCloudVault
                val cloudWrapped =
                    cloudMetadata.wrappedDek
                        ?.takeIf { it.isNotBlank() }
                        ?: return@runCatching VaultReconciliationOutcome.NoCloudVault

                val localWrappedBase64 =
                    vaultMetadataPort.getWrappedDek()?.takeIf { it.isNotBlank() }
                        ?: return@runCatching VaultReconciliationOutcome.NoLocalVault
                val localSaltBase64 =
                    vaultMetadataPort.getVaultSalt()?.takeIf { it.isNotBlank() }
                        ?: return@runCatching VaultReconciliationOutcome.NoLocalVault

                if (localWrappedBase64 == cloudWrapped && localSaltBase64 == cloudSalt) {
                    return@runCatching VaultReconciliationOutcome.AlreadyMatching
                }

                val cloudSaltBytes = cryptoManager.decodeBase64(cloudSalt)
                val cloudWrappedBytes = cryptoManager.decodeBase64(cloudWrapped)
                val cloudKek =
                    cryptoManager.deriveKek(password, cloudSaltBytes).getOrElse {
                        return@runCatching VaultReconciliationOutcome.PasswordMismatch
                    }
                val cloudDek =
                    cryptoManager.unwrapDek(cloudWrappedBytes, cloudKek).getOrElse {
                        return@runCatching VaultReconciliationOutcome.PasswordMismatch
                    }

                val localSaltBytes = cryptoManager.decodeBase64(localSaltBase64)
                val localWrappedBytes = cryptoManager.decodeBase64(localWrappedBase64)
                val localKek = cryptoManager.deriveKek(password, localSaltBytes).getOrNull()
                val localDek = localKek?.let { cryptoManager.unwrapDek(localWrappedBytes, it).getOrNull() }

                if (localDek != null && localDek.contentEquals(cloudDek)) {
                    return@runCatching VaultReconciliationOutcome.AlreadyMatching
                }

                VaultReconciliationOutcome.MismatchRequiresClobber(
                    cloudDek = cloudDek,
                    cloudSalt = cloudMetadata.salt,
                    cloudWrappedDek = cloudWrapped,
                    cloudVerification = cloudMetadata.verification,
                )
            }
    }
