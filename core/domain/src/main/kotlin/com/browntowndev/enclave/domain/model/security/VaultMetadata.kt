package com.browntowndev.enclave.domain.model.security

/**
 * Snapshot of the vault material persisted on the user's Firestore document or in local DataStore.
 *
 * - [salt] — Base64 PBKDF2 salt used to derive the Key Encryption Key (KEK) from the password.
 * - [verification] — Base64 ciphertext of [VAULT_VERIFICATION_MAGIC_STRING] encrypted with the
 *   currently cached Data Encryption Key (DEK). Used as a sanity check that the unwrapped DEK
 *   actually decrypts user data. Optional because it may not have been written yet.
 * - [wrappedDek] — Base64 `iv || aesGcm(KEK, dek)` blob. Optional because vaults created before
 *   the key-wrapping migration (or partially-corrupted cloud documents) won't have one.
 */
data class VaultMetadata(
    val salt: String,
    val verification: String?,
    val wrappedDek: String?,
)
