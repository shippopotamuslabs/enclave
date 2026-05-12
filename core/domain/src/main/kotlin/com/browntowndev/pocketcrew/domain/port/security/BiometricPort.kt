package com.browntowndev.pocketcrew.domain.port.security

import javax.crypto.Cipher

/**
 * Port for interacting with biometric authentication and hardware-backed storage.
 */
interface BiometricPort {
    /**
     * Checks if biometric authentication is available and enrolled on the device.
     */
    fun isBiometricAvailable(): Boolean

    /**
     * Prompts the user for biometric authentication.
     * @param title The title for the biometric prompt.
     * @param subtitle The subtitle for the biometric prompt.
     * @return Result.success(Unit) if authentication succeeded.
     */
    suspend fun authenticate(title: String, subtitle: String): Result<Unit>

    /**
     * Securely stores a value in hardware-backed storage, protected by biometrics.
     */
    suspend fun storeSecureValue(key: String, value: String): Result<Unit>

    /**
     * Retrieves a value from hardware-backed storage, prompting for biometrics if needed.
     */
    suspend fun getSecureValue(key: String, promptTitle: String, promptSubtitle: String): Result<String?>

    /**
     * Checks if a secure value exists for the given key.
     */
    fun hasSecureValue(key: String): Boolean

    /**
     * Clears a secure value.
     */
    fun clearSecureValue(key: String)

    /**
     * Builds a cipher for encrypting a new secure value, to be wrapped in a biometric crypto object
     * for user authentication before persisting via [storeSecureValueWithCipher].
     */
    fun getEncryptCipher(key: String): Cipher?

    /**
     * Persists ciphertext produced by [cipher] after the user authenticated the crypto object.
     */
    suspend fun storeSecureValueWithCipher(key: String, value: String, cipher: Cipher): Result<Unit>

    /**
     * Builds a cipher for decrypting an existing secure value for the biometric unlock prompt.
     */
    fun getDecryptCipher(key: String): Cipher?

    /**
     * Decrypts stored ciphertext using [cipher] after successful biometric authentication.
     */
    fun decryptWithCipher(key: String, cipher: Cipher): String?
}
