package com.browntowndev.enclave.domain.port.security

import javax.crypto.Cipher

/**
 * Hardware-backed biometric cipher operations for the vault DEK.
 *
 * Split from [BiometricPort] because these methods hand a live [Cipher] to the UI layer
 * (BiometricPrompt `CryptoObject`), while the value-based [BiometricPort] API cannot.
 * Implemented by the AndroidKeyStore-backed biometric manager in `:core:data`.
 */
interface BiometricCipherPort {
    /** Creates a fresh encrypt cipher bound to the keystore key for [key], or null on failure. */
    fun getEncryptCipher(key: String): Cipher?

    /** Creates a decrypt cipher for the blob stored under [key], or null if absent/unusable. */
    fun getDecryptCipher(key: String): Cipher?

    /** Encrypts [value] with [cipher] and persists it under [key]. */
    suspend fun storeSecureBytesWithCipher(
        key: String,
        value: ByteArray,
        cipher: Cipher,
    ): Result<Unit>

    /** Decrypts the blob stored under [key] with [cipher], or null on failure. */
    fun decryptBytesWithCipher(
        key: String,
        cipher: Cipher,
    ): ByteArray?
}
