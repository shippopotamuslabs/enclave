package com.browntowndev.pocketcrew.core.data.security

import com.browntowndev.pocketcrew.core.data.security.EnvelopeParse
import com.browntowndev.pocketcrew.core.data.security.MalformedEnvelopeReason
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

    // S1: parseEnvelope treats non-envelope plaintext as NotEnveloped
    @Test
    fun parseEnvelope_plaintext_returnsNotEnveloped() {
        val result = (service as CryptoAtRestEncryptionService).parseEnvelope("hello-plain")
        assertEquals(EnvelopeParse.NotEnveloped, result)
    }

    // S2: parseEnvelope accepts minimal valid Base64 payload (28 bytes: 12 IV + 16 ciphertext)
    @Test
    fun parseEnvelope_minimalValidPayload_returnsOk() {
        val raw28 = ByteArray(28).also { arr ->
            for (i in 0..11) arr[i] = 0x00
            for (i in 12..27) arr[i] = 0x01
        }
        val encoded = java.util.Base64.getEncoder().encodeToString(raw28)
        val stored = "${CryptoAtRestEncryptionService.ENVELOPE_PREFIX}$encoded"
        val result = (service as CryptoAtRestEncryptionService).parseEnvelope(stored)
        assertTrue(result is EnvelopeParse.Ok)
        val ok = result as EnvelopeParse.Ok
        assertEquals(12, ok.iv.size)
        assertEquals(16, ok.ciphertext.size)
    }

    // S3: isEncrypted returns false for prefix-looking but invalid Base64
    @Test
    fun isEncrypted_prefixWithInvalidBase64_returnsFalse() {
        assertFalse(service.isEncrypted("${CryptoAtRestEncryptionService.ENVELOPE_PREFIX}@@@@"))
    }

    // E1: Wrong prefix → Malformed(BAD_PREFIX)
    @Test
    fun parseEnvelope_wrongPrefix_returnsMalformedBadPrefix() {
        val result = (service as CryptoAtRestEncryptionService).parseEnvelope("PCENC:v1:aes-gcmX")
        assertTrue(result is EnvelopeParse.Malformed)
        assertEquals(MalformedEnvelopeReason.BAD_PREFIX, (result as EnvelopeParse.Malformed).reason)
    }

    // E2: Invalid Base64 after prefix → Malformed(INVALID_BASE64)
    @Test
    fun parseEnvelope_invalidBase64AfterPrefix_returnsMalformedInvalidBase64() {
        val stored = "${CryptoAtRestEncryptionService.ENVELOPE_PREFIX}@@@@"
        val result = (service as CryptoAtRestEncryptionService).parseEnvelope(stored)
        assertTrue(result is EnvelopeParse.Malformed)
        assertEquals(MalformedEnvelopeReason.INVALID_BASE64, (result as EnvelopeParse.Malformed).reason)
    }

    // E3: Too-short decoded payload (27 bytes < 28 min) → Malformed(PAYLOAD_TOO_SHORT)
    @Test
    fun parseEnvelope_tooShortPayload_returnsMalformedPayloadTooShort() {
        val short27 = ByteArray(27) { 0x05.toByte() }
        val encoded = java.util.Base64.getEncoder().encodeToString(short27)
        val stored = "${CryptoAtRestEncryptionService.ENVELOPE_PREFIX}$encoded"
        val result = (service as CryptoAtRestEncryptionService).parseEnvelope(stored)
        assertTrue(result is EnvelopeParse.Malformed)
        assertEquals(MalformedEnvelopeReason.PAYLOAD_TOO_SHORT, (result as EnvelopeParse.Malformed).reason)
    }

    // E4: Too-large decoded payload (16777217 bytes > 16 MiB cap) → Malformed(PAYLOAD_TOO_LARGE)
    // Note: We test via Base64 string length check — the implementation should cap at base64 string length
    // before decoding to avoid allocating 16 MB. A base64 string for 16777217 bytes is ~22 MB chars.
    // We simulate this by crafting a prefix-valid string whose base64 portion length exceeds the cap.
    @Test
    fun parseEnvelope_tooLargePayload_returnsMalformedPayloadTooLarge() {
        // 16777217 bytes base64-encodes to ceil(16777217/3)*4 = 22369624 chars
        // Build a string of that many 'A' chars (valid base64 alphabet) after the prefix
        val oversizedB64 = "A".repeat(22369624)
        val stored = "${CryptoAtRestEncryptionService.ENVELOPE_PREFIX}$oversizedB64"
        val result = (service as CryptoAtRestEncryptionService).parseEnvelope(stored)
        assertTrue(result is EnvelopeParse.Malformed)
        assertEquals(MalformedEnvelopeReason.PAYLOAD_TOO_LARGE, (result as EnvelopeParse.Malformed).reason)
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

        override fun encrypt(plaintext: String, aad: ByteArray?): Result<ByteArray> = encryptBytes(plaintext.toByteArray(), aad)

        override fun encryptBytes(data: ByteArray, aad: ByteArray?): Result<ByteArray> =
            if (hasCachedKey()) {
                Result.success("ENC:v1:${String(data)}".toByteArray())
            } else {
                Result.failure(IllegalStateException("locked"))
            }

        override fun decrypt(ciphertext: ByteArray, aad: ByteArray?): Result<String> = decryptBytes(ciphertext, aad).map { String(it) }

        override fun decryptBytes(ciphertext: ByteArray, aad: ByteArray?): Result<ByteArray> {
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
