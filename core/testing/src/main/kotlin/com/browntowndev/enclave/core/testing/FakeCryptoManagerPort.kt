package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory [CryptoManagerPort] fake (ENC-28). Base64 and salt behavior match the
 * real manager; encryption is identity-shaped (results are test-controlled vars),
 * because these tests assert vault/session orchestration, not cryptography.
 */
class FakeCryptoManagerPort : CryptoManagerPort {
    var hasCachedKeyValue: Boolean = false
    var cachedDekValue: ByteArray? = null
    var cacheDekResult: Result<Unit> = Result.success(Unit)
    val cacheDekCalls = mutableListOf<ByteArray>()
    var clearKeyCallCount = 0
    var generatedSalt: ByteArray = ByteArray(16) { 0x21 }
    var generateSaltCallCount = 0
    var encodeBase64CallCount = 0

    var encryptResult: Result<ByteArray> = Result.success(ByteArray(0))
    var decryptResult: Result<String> = Result.success("")
    val decryptCalls = mutableListOf<Pair<ByteArray, ByteArray?>>()

    /** Overrides [decodeBase64] when a test needs specific decoded bytes. */
    var decodeBase64Override: ((String) -> ByteArray)? = null

    /** Optional shared event log for cross-collaborator ordering assertions. */
    var events: MutableList<String>? = null

    private val unlocked = MutableStateFlow(false)
    override val isVaultUnlocked: StateFlow<Boolean> = unlocked

    fun setUnlocked(value: Boolean) {
        unlocked.value = value
    }

    override fun deriveKek(password: String, salt: ByteArray): Result<ByteArray> =
        Result.success(ByteArray(32) { 0x0A })

    override fun generateDek(size: Int): ByteArray = ByteArray(size) { DEK_FILL }

    private companion object {
        const val DEK_FILL: Byte = 0x0B
    }

    override fun wrapDek(dek: ByteArray, kek: ByteArray): Result<ByteArray> = Result.success(dek)

    override fun unwrapDek(wrapped: ByteArray, kek: ByteArray): Result<ByteArray> = Result.success(wrapped)

    override fun cacheDek(dek: ByteArray): Result<Unit> {
        cacheDekCalls += dek
        if (cacheDekResult.isSuccess) {
            cachedDekValue = dek
            hasCachedKeyValue = true
        }
        return cacheDekResult
    }

    override fun getCachedDek(): ByteArray? = cachedDekValue

    override fun clearKey() {
        clearKeyCallCount++
        events?.add("clearKey")
        cachedDekValue = null
        hasCachedKeyValue = false
    }

    override fun hasCachedKey(): Boolean = hasCachedKeyValue

    override fun encrypt(plaintext: String, aad: ByteArray?): Result<ByteArray> = encryptResult

    override fun encryptBytes(data: ByteArray, aad: ByteArray?): Result<ByteArray> =
        Result.success(data)

    override fun decrypt(ciphertext: ByteArray, aad: ByteArray?): Result<String> {
        decryptCalls += ciphertext to aad
        return decryptResult
    }

    override fun decryptBytes(ciphertext: ByteArray, aad: ByteArray?): Result<ByteArray> =
        Result.success(ciphertext)

    override fun generateSalt(): ByteArray {
        generateSaltCallCount++
        return generatedSalt
    }

    override fun encodeBase64(data: ByteArray): String {
        encodeBase64CallCount++
        return java.util.Base64.getEncoder().encodeToString(data)
    }

    override fun decodeBase64(base64: String): ByteArray =
        decodeBase64Override?.invoke(base64) ?: java.util.Base64.getDecoder().decode(base64)

    override fun generateRandomKey(size: Int): ByteArray = ByteArray(size) { 0x0C }

    override fun generateRandomIv(size: Int): ByteArray = ByteArray(size) { 0x0D }

    override fun encryptWithKey(data: ByteArray, key: ByteArray, iv: ByteArray): Result<ByteArray> =
        Result.success(data)

    override fun decryptWithKey(data: ByteArray, key: ByteArray, iv: ByteArray): Result<ByteArray> =
        Result.success(data)
}
