package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.domain.model.security.VaultMetadata
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import javax.inject.Inject

/**
 * Pushes the locally-cached salt + wrappedDek (+ optional verification) to Firestore on the
 * first sign-in from a device whose user document has no vault material yet. Non-destructive
 * and safe to run silently — there is no other-device data at risk.
 *
 * Returns failure if the local store is missing salt or wrappedDek (the device cannot bootstrap
 * a vault it never had).
 */
class BootstrapCloudVaultFromLocalUseCase
    @Inject
    constructor(
        private val vaultMetadataPort: VaultMetadataPort,
        private val cloudSyncPort: CloudSyncPort,
    ) {
        suspend operator fun invoke(): Result<Unit> =
            runCatching {
                val salt =
                    vaultMetadataPort
                        .getVaultSalt()
                        ?.takeIf { it.isNotBlank() }
                        ?: error("Cannot bootstrap cloud vault: local salt is missing")
                val wrappedDek =
                    vaultMetadataPort
                        .getWrappedDek()
                        ?.takeIf { it.isNotBlank() }
                        ?: error("Cannot bootstrap cloud vault: local wrappedDek is missing")
                val verification = vaultMetadataPort.getVaultVerificationData()

                cloudSyncPort
                    .uploadVaultMetadata(
                        VaultMetadata(
                            salt = salt,
                            verification = verification,
                            wrappedDek = wrappedDek,
                        ),
                    ).getOrThrow()
            }
    }
