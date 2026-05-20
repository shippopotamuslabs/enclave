package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.port.repository.CleanupRepository
import com.browntowndev.pocketcrew.domain.port.security.BiometricPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.port.security.VaultMetadataPort
import com.browntowndev.pocketcrew.domain.security.VaultKeystoreAliases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdoptCloudVaultUseCaseTest {
    private lateinit var crypto: FakeCrypto
    private lateinit var metadata: FakeMetadata
    private lateinit var cleanup: FakeCleanup
    private lateinit var biometric: FakeBiometric
    private lateinit var useCase: AdoptCloudVaultUseCase

    private val payload =
        VaultReconciliationOutcome.MismatchRequiresClobber(
            cloudDek = ByteArray(32) { 0xCC.toByte() },
            cloudSalt = "cloud-salt-base64",
            cloudWrappedDek = "cloud-wrapped-base64",
            cloudVerification = "cloud-verification-base64",
        )

    @BeforeEach
    fun setUp() {
        crypto = FakeCrypto()
        metadata = FakeMetadata()
        cleanup = FakeCleanup()
        biometric = FakeBiometric()
        useCase = AdoptCloudVaultUseCase(crypto, metadata, cleanup, biometric)
    }

    @Test
    fun `adoption clears Room exactly once`() =
        runTest {
            useCase(payload).getOrThrow()

            assertEquals(1, cleanup.clearCallCount)
        }

    @Test
    fun `adoption caches cloud DEK plaintext`() =
        runTest {
            useCase(payload).getOrThrow()

            assertArrayEquals(payload.cloudDek, crypto.stashedDek)
        }

    @Test
    fun `adoption persists cloud vault metadata locally`() =
        runTest {
            useCase(payload).getOrThrow()

            assertEquals("cloud-salt-base64", metadata.salt)
            assertEquals("cloud-wrapped-base64", metadata.wrapped)
            assertEquals("cloud-verification-base64", metadata.verification)
        }

    @Test
    fun `adoption clears biometric-bound DEK alias`() =
        runTest {
            useCase(payload).getOrThrow()

            assertEquals(
                listOf(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY),
                biometric.clearedKeys,
            )
        }

    @Test
    fun `adoption with null cloud verification clears stale local verification`() =
        runTest {
            metadata.verification = "stale-old-dek-ciphertext"

            useCase(payload.copy(cloudVerification = null)).getOrThrow()

            assertNull(metadata.verification, "Stale verification under old DEK must not survive clobber")
        }

    private class FakeCrypto : CryptoManagerPort {
        var stashedDek: ByteArray? = null

        override fun deriveKek(
            password: String,
            salt: ByteArray,
        ): Result<ByteArray> = error("not used")

        override fun generateDek(size: Int): ByteArray = error("not used")

        override fun wrapDek(
            dek: ByteArray,
            kek: ByteArray,
        ): Result<ByteArray> = error("not used")

        override fun unwrapDek(
            wrapped: ByteArray,
            kek: ByteArray,
        ): Result<ByteArray> = error("not used")

        override fun cacheDek(dek: ByteArray): Result<Unit> {
            stashedDek = dek
            return Result.success(Unit)
        }

        override fun getCachedDek(): ByteArray? = stashedDek

        override fun clearKey() {
            stashedDek = null
        }

        override fun hasCachedKey(): Boolean = stashedDek != null

        override val isVaultUnlocked: StateFlow<Boolean> = MutableStateFlow(true)

        override fun encrypt(
            plaintext: String,
            aad: ByteArray?,
        ): Result<ByteArray> = error("not used")

        override fun encryptBytes(
            data: ByteArray,
            aad: ByteArray?,
        ): Result<ByteArray> = error("not used")

        override fun decrypt(
            ciphertext: ByteArray,
            aad: ByteArray?,
        ): Result<String> = error("not used")

        override fun decryptBytes(
            ciphertext: ByteArray,
            aad: ByteArray?,
        ): Result<ByteArray> = error("not used")

        override fun generateSalt(): ByteArray = error("not used")

        override fun encodeBase64(data: ByteArray): String = error("not used")

        override fun decodeBase64(base64: String): ByteArray = error("not used")

        override fun generateRandomKey(size: Int): ByteArray = error("not used")

        override fun generateRandomIv(size: Int): ByteArray = error("not used")

        override fun encryptWithKey(
            data: ByteArray,
            key: ByteArray,
            iv: ByteArray,
        ): Result<ByteArray> = error("not used")

        override fun decryptWithKey(
            ciphertext: ByteArray,
            key: ByteArray,
            iv: ByteArray,
        ): Result<ByteArray> = error("not used")
    }

    private class FakeMetadata : VaultMetadataPort {
        var salt: String? = null
        var verification: String? = null
        var wrapped: String? = null

        override suspend fun getVaultSalt(): String? = salt

        override suspend fun saveVaultSalt(salt: String) {
            this.salt = salt
        }

        override suspend fun getVaultVerificationData(): String? = verification

        override suspend fun saveVaultVerificationData(ciphertext: String?) {
            verification = ciphertext
        }

        override suspend fun getWrappedDek(): String? = wrapped

        override suspend fun saveWrappedDek(wrappedDek: String?) {
            wrapped = wrappedDek
        }
    }

    private class FakeCleanup : CleanupRepository {
        var clearCallCount = 0

        override suspend fun clearAllData() {
            clearCallCount++
        }

        override suspend fun deleteAllArtifacts() = Unit
    }

    private class FakeBiometric : BiometricPort {
        val clearedKeys = mutableListOf<String>()

        override fun isBiometricAvailable(): Boolean = false

        override suspend fun authenticate(
            title: String,
            subtitle: String,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun storeSecureValue(
            key: String,
            value: String,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun getSecureValue(
            key: String,
            promptTitle: String,
            promptSubtitle: String,
        ): Result<String?> = Result.success(null)

        override fun hasSecureValue(key: String): Boolean = false

        override fun clearSecureValue(key: String) {
            clearedKeys += key
        }
    }
}
