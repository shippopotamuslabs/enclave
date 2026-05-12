package com.browntowndev.pocketcrew.domain.port.security

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.crypto.Cipher

/**
 * Ensures [BiometricPort] cipher enrollment/unlock APIs are part of the contract so
 * presentation code does not depend on concrete Android implementations.
 */
class BiometricPortCipherApiTest {

    @Test
    fun `cipher methods are callable through BiometricPort`() {
        val fake = FakeBiometricPort()
        val port: BiometricPort = fake

        assertNull(port.getEncryptCipher("vault_password"))
        assertNull(port.getDecryptCipher("vault_password"))

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val result = runBlocking {
            port.storeSecureValueWithCipher("vault_password", "secret", cipher)
        }
        assertTrue(result.isSuccess)
        assertEquals("secret", port.decryptWithCipher("vault_password", cipher))
    }

    private class FakeBiometricPort : BiometricPort {
        private var lastStored: Pair<String, Cipher>? = null

        override fun isBiometricAvailable(): Boolean = true

        override suspend fun authenticate(title: String, subtitle: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun storeSecureValue(key: String, value: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun getSecureValue(
            key: String,
            promptTitle: String,
            promptSubtitle: String,
        ): Result<String?> = Result.success(null)

        override fun hasSecureValue(key: String): Boolean = false

        override fun clearSecureValue(key: String) {}

        override fun getEncryptCipher(key: String): Cipher? = null

        override suspend fun storeSecureValueWithCipher(
            key: String,
            value: String,
            cipher: Cipher,
        ): Result<Unit> {
            lastStored = value to cipher
            return Result.success(Unit)
        }

        override fun getDecryptCipher(key: String): Cipher? = null

        override fun decryptWithCipher(key: String, cipher: Cipher): String? {
            val stored = lastStored ?: return null
            if (stored.second !== cipher) return null
            return stored.first
        }
    }
}
