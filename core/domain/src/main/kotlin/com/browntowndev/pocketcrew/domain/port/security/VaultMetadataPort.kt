package com.browntowndev.pocketcrew.domain.port.security

interface VaultMetadataPort {
    suspend fun getVaultSalt(): String?

    suspend fun saveVaultSalt(salt: String)

    suspend fun getVaultVerificationData(): String?

    /**
     * Persists the verification ciphertext locally. Pass `null` to remove the entry — used by
     * the clobber/adopt flow when the cloud-supplied verification is itself absent and the
     * stale local ciphertext must not survive.
     */
    suspend fun saveVaultVerificationData(ciphertext: String?)

    /**
     * Returns the locally cached `Base64(iv || aesGcm(KEK, dek))` blob, or `null` if no
     * vault has been created yet on this device.
     */
    suspend fun getWrappedDek(): String?

    /**
     * Persists the wrapped DEK locally so the vault can be unlocked offline on subsequent runs.
     * Pass `null` to clear the local entry (e.g. clobber-on-mismatch flow).
     */
    suspend fun saveWrappedDek(wrappedDek: String?)
}
