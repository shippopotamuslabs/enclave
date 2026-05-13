package com.browntowndev.pocketcrew.core.data.security

import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64

class AtRestEncryptionServiceTest {
    private lateinit var cryptoManager: FakeCryptoManager
    private lateinit var service: LocalDataEncryptionService

    @BeforeEach
    fun setUp() {
        cryptoManager = FakeCryptoManager()
        service = CryptoAtRestEncryptionService(cryptoManager)
    }

    @Test
    fun encryptString_unlocked_returnsVersionedEnvelopeWithoutPlaintext() {
        cryptoManager.unlock()

        val encrypted = service.encryptString("hello")

        assertTrue(service.isEncrypted(encrypted))
        assertFalse(encrypted.contains("hello"))
        assertEquals("hello", service.decryptString(encrypted))
    }

    @Test
    fun encryptString_locked_failsClosed() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                service.encryptString("hello")
            }

        assertTrue(error.message?.contains("locked") == true)
    }

    @Test
    fun decryptString_legacyPlaintext_unlocked_returnsPlaintext() {
        cryptoManager.unlock()

        val decrypted = service.decryptString("legacy plaintext")

        assertEquals("legacy plaintext", decrypted)
    }

    @Test
    fun decryptString_legacyPlaintext_locked_failsClosed() {
        assertThrows(IllegalStateException::class.java) {
            service.decryptString("legacy plaintext")
        }
    }

    private class FakeCryptoManager : CryptoManagerPort {
        private val unlocked = MutableStateFlow(false)
        override val isVaultUnlocked = unlocked

        fun unlock() {
            unlocked.value = true
        }

        override fun deriveKek(
            password: String,
            salt: ByteArray,
        ): Result<ByteArray> = Result.success(ByteArray(32) { 9 })

        override fun generateDek(size: Int): ByteArray = ByteArray(size) { 7 }

        override fun wrapDek(
            dek: ByteArray,
            kek: ByteArray,
        ): Result<ByteArray> = Result.success(dek)

        override fun unwrapDek(
            wrapped: ByteArray,
            kek: ByteArray,
        ): Result<ByteArray> = Result.success(wrapped)

        override fun cacheDek(dek: ByteArray): Result<Unit> {
            unlock()
            return Result.success(Unit)
        }

        override fun getCachedDek(): ByteArray? = if (hasCachedKey()) ByteArray(32) { 7 } else null

        override fun clearKey() {
            unlocked.value = false
        }

        override fun hasCachedKey(): Boolean = unlocked.value

        override fun encrypt(plaintext: String): Result<ByteArray> = encryptBytes(plaintext.toByteArray())

        override fun encryptBytes(data: ByteArray): Result<ByteArray> =
            if (hasCachedKey()) {
                Result.success("ENC:v1:${String(data)}".toByteArray())
            } else {
                Result.failure(IllegalStateException("locked"))
            }

        override fun decrypt(ciphertext: ByteArray): Result<String> = decryptBytes(ciphertext).map { String(it) }

        override fun decryptBytes(ciphertext: ByteArray): Result<ByteArray> {
            if (!hasCachedKey()) return Result.failure(IllegalStateException("locked"))
            val text = String(ciphertext)
            return Result.success(text.removePrefix("ENC:v1:").toByteArray())
        }

        override fun generateSalt(): ByteArray = byteArrayOf(1, 2, 3)

        override fun encodeBase64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

        override fun decodeBase64(base64: String): ByteArray = Base64.getDecoder().decode(base64)

        override fun generateRandomKey(size: Int): ByteArray = ByteArray(size) { 1 }

        override fun generateRandomIv(size: Int): ByteArray = ByteArray(size) { 2 }

        override fun encryptWithKey(
            data: ByteArray,
            key: ByteArray,
            iv: ByteArray,
        ): Result<ByteArray> = Result.success(data)

        override fun decryptWithKey(
            ciphertext: ByteArray,
            key: ByteArray,
            iv: ByteArray,
        ): Result<ByteArray> = Result.success(ciphertext)
    }
}
