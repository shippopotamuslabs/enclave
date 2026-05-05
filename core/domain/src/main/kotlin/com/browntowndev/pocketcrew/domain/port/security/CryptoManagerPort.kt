package com.browntowndev.pocketcrew.domain.port.security

interface CryptoManagerPort {
    /**
     * Derives an AES-256-GCM key from the given password and salt using PBKDF2
     * and caches it in memory for subsequent encrypt/decrypt operations.
     */
    fun deriveAndCacheKey(password: String, salt: ByteArray): Result<Unit>
    
    /**
     * Clears the cached key from memory.
     */
    fun clearKey()
    
    /**
     * Returns true if a key is currently cached in memory.
     */
    fun hasCachedKey(): Boolean

    /**
     * Encrypts a plaintext string. The resulting ByteArray contains the
     * Initialization Vector (IV) prepended to the ciphertext.
     */
    fun encrypt(plaintext: String): Result<ByteArray>

    /**
     * Decrypts a ciphertext ByteArray that has the IV prepended to it.
     */
    fun decrypt(ciphertext: ByteArray): Result<String>
}
