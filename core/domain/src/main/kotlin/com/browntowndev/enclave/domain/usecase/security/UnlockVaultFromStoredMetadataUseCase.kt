package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import javax.inject.Inject

/**
 * Unlocks the vault using locally persisted metadata when available; otherwise fetches cloud
 * vault metadata, persists it locally, and unlocks. Shared by Settings re-auth and any flow that
 * must cache the DEK after reconciliation without duplicating cloud-vs-local branching.
 */
open class UnlockVaultFromStoredMetadataUseCase
    @Inject
    constructor(
        private val unlockVaultUseCase: UnlockVaultUseCase,
        private val cryptoManager: CryptoManagerPort,
        private val vaultMetadataPort: VaultMetadataPort,
        private val cloudSyncPort: CloudSyncPort,
    ) {
        open suspend operator fun invoke(password: String): Result<Unit> {
            val localSaltBase64 = vaultMetadataPort.getVaultSalt()?.takeIf { it.isNotBlank() }
            val localWrappedBase64 = vaultMetadataPort.getWrappedDek()?.takeIf { it.isNotBlank() }
            return if (localSaltBase64 != null && localWrappedBase64 != null) {
                unlockVaultUseCase(
                    password = password,
                    salt = cryptoManager.decodeBase64(localSaltBase64),
                    wrappedDek = cryptoManager.decodeBase64(localWrappedBase64),
                )
            } else {
                unlockFromCloudMetadata(password)
            }
        }

        private suspend fun unlockFromCloudMetadata(password: String): Result<Unit> =
            runCatching {
                val cloudMetadata =
                    cloudSyncPort.fetchVaultMetadata().getOrThrow()
                        ?: error("Cloud vault metadata missing")
                val cloudSaltBase64 =
                    cloudMetadata.salt.takeIf { it.isNotBlank() }
                        ?: error("Cloud vault salt missing")
                val cloudWrappedBase64 =
                    cloudMetadata.wrappedDek?.takeIf { it.isNotBlank() }
                        ?: error("Cloud wrapped DEK missing")

                unlockVaultUseCase(
                    password = password,
                    salt = cryptoManager.decodeBase64(cloudSaltBase64),
                    wrappedDek = cryptoManager.decodeBase64(cloudWrappedBase64),
                ).getOrThrow()

                vaultMetadataPort.saveVaultSalt(cloudSaltBase64)
                vaultMetadataPort.saveWrappedDek(cloudWrappedBase64)
                cloudMetadata.verification?.takeIf { it.isNotBlank() }?.let {
                    vaultMetadataPort.saveVaultVerificationData(it)
                }
            }
    }
