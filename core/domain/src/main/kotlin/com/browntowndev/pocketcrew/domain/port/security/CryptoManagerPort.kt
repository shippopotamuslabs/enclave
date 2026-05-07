package com.browntowndev.pocketcrew.domain.port.security

interface CryptoManagerPort {
    /**
     * Derives a secret key from the password and salt, and caches it in memory and persistent storage.
     */
    fun deriveAndCacheKey(password: String, salt: ByteArray): Result<Unit>

    /**
     * Clears the cached key from memory and persistent storage.
     */
    fun clearKey()

    /**
     * Returns true if a key is currently cached (in memory or persistent storage).
     */
    fun hasCachedKey(): Boolean

    /**
     * Encrypts the plaintext using the cached key.
     */
    fun encrypt(plaintext: String): Result<ByteArray>

    /**
     * Decrypts the ciphertext using the cached key.
     */
    fun decrypt(ciphertext: ByteArray): Result<String>

    /**
     * Generates a new random salt for key derivation.
     */
    fun generateSalt(): ByteArray
}
