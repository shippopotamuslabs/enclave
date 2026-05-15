package com.browntowndev.pocketcrew.domain.port.security

interface CryptoManagerPort {
    /**
     * Derives a Key Encryption Key (KEK) from the password and salt using PBKDF2-HMAC-SHA256.
     * The KEK is returned as raw bytes and is NOT cached. Callers should treat it as transient
     * and zero it from memory ASAP after using it to wrap/unwrap a DEK.
     */
    fun deriveKek(password: String, salt: ByteArray): Result<ByteArray>

    /**
     * Generates a fresh random Data Encryption Key (DEK) of [size] bytes (default 32 bytes / 256 bits).
     */
    fun generateDek(size: Int = 32): ByteArray

    /**
     * Wraps a DEK with a KEK using AES/GCM/NoPadding.
     * Returns `iv (12 bytes) || ciphertext` — the same layout as [encrypt].
     */
    fun wrapDek(dek: ByteArray, kek: ByteArray): Result<ByteArray>

    /**
     * Unwraps a previously [wrapDek]-produced blob back to the raw DEK using the supplied KEK.
     * Returns failure (typically wrapping `AEADBadTagException`) when the KEK is wrong or
     * the blob is corrupted/truncated.
     */
    fun unwrapDek(wrapped: ByteArray, kek: ByteArray): Result<ByteArray>

    /**
     * Caches the supplied DEK as the active data key. Subsequent calls to [encrypt] / [decrypt]
     * use this DEK. Replaces any previously cached key.
     */
    fun cacheDek(dek: ByteArray): Result<Unit>

    /**
     * Returns the currently cached DEK bytes, or null if the vault is locked.
     * Used by the biometric enrollment flow to wrap the DEK with an AndroidKeyStore key.
     */
    fun getCachedDek(): ByteArray?

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
     * Encrypts the plaintext using the cached DEK.
     */
    fun encrypt(plaintext: String, aad: ByteArray? = null): Result<ByteArray>

    /**
     * Encrypts the raw bytes using the cached DEK.
     */
    fun encryptBytes(data: ByteArray, aad: ByteArray? = null): Result<ByteArray>

    /**
     * Decrypts the ciphertext using the cached DEK.
     */
    fun decrypt(ciphertext: ByteArray, aad: ByteArray? = null): Result<String>

    /**
     * Decrypts the raw bytes using the cached DEK.
     */
    fun decryptBytes(ciphertext: ByteArray, aad: ByteArray? = null): Result<ByteArray>

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
