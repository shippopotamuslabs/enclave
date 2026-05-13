package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CreateVaultUseCaseTest {

    private lateinit var crypto: FakeCryptoManager
    private lateinit var useCase: CreateVaultUseCase

    @BeforeEach
    fun setup() {
        crypto = FakeCryptoManager()
        useCase = CreateVaultUseCase(crypto)
    }

    @Test
    fun `derives KEK, generates DEK, wraps and caches DEK, returns wrapped blob`() = runTest {
        val result = useCase("password", ByteArray(16) { it.toByte() })

        assertTrue(result.isSuccess)
        assertNotNull(crypto.kekDerivedFor)
        assertTrue(crypto.dekGenerated)
        assertTrue(crypto.dekCached)
        assertArrayEquals(crypto.lastWrappedDek, result.getOrNull())
    }

    @Test
    fun `failure to derive KEK propagates and does not cache anything`() = runTest {
        crypto.deriveKekResult = Result.failure(IllegalStateException("derive failure"))

        val result = useCase("password", ByteArray(16))

        assertTrue(result.isFailure)
        assertFalse(crypto.dekCached)
        assertEquals("derive failure", result.exceptionOrNull()?.message)
    }

    @Test
    fun `failure to wrap DEK propagates and does not cache`() = runTest {
        crypto.wrapResult = Result.failure(RuntimeException("wrap failed"))

        val result = useCase("password", ByteArray(16))

        assertTrue(result.isFailure)
        assertFalse(crypto.dekCached)
    }

    @Test
    fun `cached DEK is the same one that was wrapped`() = runTest {
        useCase("password", ByteArray(16))

        assertArrayEquals(crypto.dekProduced, crypto.dekCachedValue)
    }

    private class FakeCryptoManager : CryptoManagerPort {
        var deriveKekResult: Result<ByteArray> = Result.success(ByteArray(32) { 0xAB.toByte() })
        var wrapResult: Result<ByteArray>? = null
        var kekDerivedFor: String? = null
        var dekGenerated = false
        var dekCached = false
        var dekProduced: ByteArray = ByteArray(0)
        var dekCachedValue: ByteArray = ByteArray(0)
        var lastWrappedDek: ByteArray = ByteArray(0)

        override fun deriveKek(password: String, salt: ByteArray): Result<ByteArray> {
            kekDerivedFor = password
            return deriveKekResult
        }
        override fun generateDek(size: Int): ByteArray {
            dekGenerated = true
            dekProduced = ByteArray(size) { 0x42.toByte() }
            return dekProduced
        }
        override fun wrapDek(dek: ByteArray, kek: ByteArray): Result<ByteArray> {
            val r = wrapResult ?: Result.success(byteArrayOf(0x99.toByte()) + dek)
            r.onSuccess { lastWrappedDek = it }
            return r
        }
        override fun unwrapDek(wrapped: ByteArray, kek: ByteArray): Result<ByteArray> = error("not used")
        override fun cacheDek(dek: ByteArray): Result<Unit> {
            dekCached = true
            dekCachedValue = dek
            return Result.success(Unit)
        }
        override fun getCachedDek(): ByteArray? = if (dekCached) dekCachedValue else null
        override fun clearKey() {
            dekCached = false
        }
        override fun hasCachedKey(): Boolean = dekCached
        override val isVaultUnlocked: StateFlow<Boolean> = MutableStateFlow(false)
        override fun encrypt(plaintext: String): Result<ByteArray> = error("not used")
        override fun encryptBytes(data: ByteArray): Result<ByteArray> = error("not used")
        override fun decrypt(ciphertext: ByteArray): Result<String> = error("not used")
        override fun decryptBytes(ciphertext: ByteArray): Result<ByteArray> = error("not used")
        override fun generateSalt(): ByteArray = ByteArray(16)
        override fun encodeBase64(data: ByteArray): String = error("not used")
        override fun decodeBase64(base64: String): ByteArray = error("not used")
        override fun generateRandomKey(size: Int): ByteArray = ByteArray(size)
        override fun generateRandomIv(size: Int): ByteArray = ByteArray(size)
        override fun encryptWithKey(data: ByteArray, key: ByteArray, iv: ByteArray): Result<ByteArray> = error("not used")
        override fun decryptWithKey(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): Result<ByteArray> = error("not used")
    }
}
