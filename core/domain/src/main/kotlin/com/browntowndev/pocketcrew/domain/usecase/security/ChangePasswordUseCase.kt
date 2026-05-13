package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.model.security.VaultMetadata
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.port.security.VaultMetadataPort
import javax.inject.Inject

/**
 * Re-wraps the existing Data Encryption Key (DEK) under a fresh KEK derived from [newPassword].
 * Touches **no** Room data — only the wrapped DEK and salt change. The currently cached DEK is
 * left intact so the user does not need to re-authenticate. Cloud (when signed in) and local
 * DataStore are both updated atomically.
 *
 * Steps:
 * 1. Derive `oldKek` from [oldPassword] + current `salt` and unwrap the locally stored
 *    `wrappedDek_old` to get `dek_old`.
 * 2. Compare `dek_old` to the currently cached DEK — if they don't match, the supplied old
 *    password is wrong; reject without changing any state.
 * 3. Generate a fresh salt, derive `newKek` from [newPassword] + `newSalt`, wrap `dek_old`
 *    with `newKek` to produce `wrappedDek_new`.
 * 4. Persist `{newSalt, wrappedDek_new}` to local + cloud. Verification ciphertext is
 *    unchanged because it was encrypted with the DEK (which has not changed).
 */
class ChangePasswordUseCase @Inject constructor(
    private val cryptoManager: CryptoManagerPort,
    private val vaultMetadataPort: VaultMetadataPort,
    private val cloudSyncPort: CloudSyncPort,
) {
    @Suppress("ReturnCount")
    suspend operator fun invoke(oldPassword: String, newPassword: String): Result<Unit> {
        if (newPassword.isBlank()) {
            return Result.failure(IllegalArgumentException("New password must not be blank"))
        }

        val cachedDek = cryptoManager.getCachedDek()
            ?: return Result.failure(IllegalStateException("Vault is locked"))

        val saltBase64 = vaultMetadataPort.getVaultSalt()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No salt found"))
        val wrappedDekBase64 = vaultMetadataPort.getWrappedDek()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No wrapped DEK found"))

        val saltBytes = cryptoManager.decodeBase64(saltBase64)
        val wrappedBytes = cryptoManager.decodeBase64(wrappedDekBase64)

        val oldKek = cryptoManager.deriveKek(oldPassword, saltBytes).getOrElse { return Result.failure(it) }
        val unwrappedDek = cryptoManager.unwrapDek(wrappedBytes, oldKek).getOrElse {
            return Result.failure(IllegalStateException("Incorrect current password", it))
        }

        if (!unwrappedDek.contentEquals(cachedDek)) {
            return Result.failure(IllegalStateException("Cached DEK does not match unwrapped DEK"))
        }

        val newSaltBytes = cryptoManager.generateSalt()
        val newKek = cryptoManager.deriveKek(newPassword, newSaltBytes).getOrElse { return Result.failure(it) }
        val newWrappedBytes = cryptoManager.wrapDek(unwrappedDek, newKek).getOrElse { return Result.failure(it) }

        val newSaltBase64 = cryptoManager.encodeBase64(newSaltBytes)
        val newWrappedBase64 = cryptoManager.encodeBase64(newWrappedBytes)

        val oldSaltBase64 = saltBase64
        val oldWrappedDekBase64 = wrappedDekBase64

        vaultMetadataPort.saveVaultSalt(newSaltBase64)
        vaultMetadataPort.saveWrappedDek(newWrappedBase64)

        return runCatching {
            cloudSyncPort.uploadVaultMetadata(
                VaultMetadata(
                    salt = newSaltBase64,
                    verification = vaultMetadataPort.getVaultVerificationData(),
                    wrappedDek = newWrappedBase64,
                ),
            ).getOrThrow()
        }.onFailure {
            runCatching {
                vaultMetadataPort.saveVaultSalt(oldSaltBase64)
                vaultMetadataPort.saveWrappedDek(oldWrappedDekBase64)
            }
        }.map { Unit }
    }
}
