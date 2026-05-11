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
     * Flow that emits true when the vault is unlocked, false when locked.
     */
    val isVaultUnlocked: kotlinx.coroutines.flow.StateFlow<Boolean>

    /**
     * Encrypts the plaintext using the cached key.
     */
    fun encrypt(plaintext: String): Result<ByteArray>

    /**
     * Encrypts the raw bytes using the cached key.
     */
    fun encryptBytes(data: ByteArray): Result<ByteArray>

    /**
     * Decrypts the ciphertext using the cached key.
     */
    fun decrypt(ciphertext: ByteArray): Result<String>

    /**
     * Decrypts the raw bytes using the cached key.
     */
    fun decryptBytes(ciphertext: ByteArray): Result<ByteArray>

    /**
     * Generates a new random salt for key derivation.
     */
    fun generateSalt(): ByteArray

    /**
     * Encodes bytes to Base64 string.
     */
    fun encodeBase64(data: ByteArray): String

    /**
     * Decodes Base64 string to bytes.
     */
    fun decodeBase64(base64: String): ByteArray

    /**
     * Generates a random key of the specified size in bytes.
     */
    fun generateRandomKey(size: Int = 32): ByteArray

    /**
     * Generates a random IV of the specified size in bytes.
     */
    fun generateRandomIv(size: Int = 12): ByteArray

    /**
     * Encrypts the raw bytes using a provided transient key and IV.
     * This is used for cloud-based extraction where the key is generated on the fly.
     */
    fun encryptWithKey(data: ByteArray, key: ByteArray, iv: ByteArray): Result<ByteArray>
    /**
     * Decrypts raw data using a provided key and IV.
     * 
     * @param ciphertext The data to decrypt (includes auth tag).
     * @param key The AES-256 key.
     * @param iv The GCM initialization vector.
     * @return A Result containing the decrypted bytes.
     */
    fun decryptWithKey(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): Result<ByteArray>
}
