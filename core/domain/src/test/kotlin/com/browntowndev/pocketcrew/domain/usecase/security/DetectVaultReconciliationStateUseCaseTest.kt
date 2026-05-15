package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.security.VaultMetadata
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.port.security.VaultMetadataPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DetectVaultReconciliationStateUseCaseTest {

    private lateinit var crypto: FakeCrypto
    private lateinit var metadata: FakeMetadata
    private lateinit var cloud: FakeCloud
    private lateinit var useCase: DetectVaultReconciliationStateUseCase

    @BeforeEach
    fun setUp() {
        crypto = FakeCrypto()
        metadata = FakeMetadata()
        cloud = FakeCloud()
        useCase = DetectVaultReconciliationStateUseCase(crypto, metadata, cloud)
    }

    @Test
    fun `no cloud metadata returns NoCloudVault`() = runTest {
        cloud.metadata = null

        val outcome = useCase("password").getOrThrow()

        assertEquals(VaultReconciliationOutcome.NoCloudVault, outcome)
        assertNull(crypto.stashedDek)
    }

    @Test
    fun `cloud metadata without wrappedDek returns NoCloudVault`() = runTest {
        cloud.metadata = VaultMetadata(salt = "cloud-salt", verification = null, wrappedDek = null)

        val outcome = useCase("password").getOrThrow()

        assertEquals(VaultReconciliationOutcome.NoCloudVault, outcome)
    }

    @Test
    fun `blank cloud vault material returns NoCloudVault`() = runTest {
        cloud.metadata = VaultMetadata(salt = "", verification = null, wrappedDek = "")

        val outcome = useCase("password").getOrThrow()

        assertEquals(VaultReconciliationOutcome.NoCloudVault, outcome)
    }

    @Test
    fun `local missing salt returns NoLocalVault`() = runTest {
        cloud.metadata = VaultMetadata(salt = "cloud-salt", verification = "v", wrappedDek = "cw")
        metadata.salt = null
        metadata.wrapped = "local-blob"

        val outcome = useCase("password").getOrThrow()

        assertEquals(VaultReconciliationOutcome.NoLocalVault, outcome)
    }

    @Test
    fun `local missing wrappedDek returns NoLocalVault`() = runTest {
        cloud.metadata = VaultMetadata(salt = "cloud-salt", verification = "v", wrappedDek = "cw")
        metadata.salt = "local-salt"
        metadata.wrapped = null

        val outcome = useCase("password").getOrThrow()

        assertEquals(VaultReconciliationOutcome.NoLocalVault, outcome)
    }

    @Test
    fun `blank local vault material returns NoLocalVault`() = runTest {
        cloud.metadata = VaultMetadata(salt = "cloud-salt", verification = "v", wrappedDek = "cw")
        metadata.salt = ""
        metadata.wrapped = ""

        val outcome = useCase("password").getOrThrow()

        assertEquals(VaultReconciliationOutcome.NoLocalVault, outcome)
    }

    @Test
    fun `identical local and cloud Base64 returns AlreadyMatching without unwrap`() = runTest {
        cloud.metadata = VaultMetadata(salt = "S", verification = null, wrappedDek = "W")
        metadata.salt = "S"
        metadata.wrapped = "W"

        val outcome = useCase("password").getOrThrow()

        assertEquals(VaultReconciliationOutcome.AlreadyMatching, outcome)
        assertEquals(0, crypto.unwrapInvocationCount)
    }

    @Test
    fun `same DEK plaintext under different blobs returns AlreadyMatching`() = runTest {
        cloud.metadata = VaultMetadata(salt = "cloud-salt", verification = null, wrappedDek = "cw")
        metadata.salt = "local-salt"
        metadata.wrapped = "lw"
        val sharedDek = ByteArray(32) { 0x42.toByte() }
        crypto.cloudUnwrap = Result.success(sharedDek.copyOf())
        crypto.localUnwrap = Result.success(sharedDek.copyOf())

        val outcome = useCase("password").getOrThrow()

        assertEquals(VaultReconciliationOutcome.AlreadyMatching, outcome)
    }

    @Test
    fun `wrong password against cloud returns PasswordMismatch`() = runTest {
        cloud.metadata = VaultMetadata(salt = "cloud-salt", verification = null, wrappedDek = "cw")
        metadata.salt = "local-salt"
        metadata.wrapped = "lw"
        crypto.cloudUnwrap = Result.failure(javax.crypto.AEADBadTagException("bad"))

        val outcome = useCase("password").getOrThrow()

        assertEquals(VaultReconciliationOutcome.PasswordMismatch, outcome)
    }

    @Test
    fun `different DEKs returns MismatchRequiresClobber with cloud payload`() = runTest {
        val cloudDek = ByteArray(32) { 0xAA.toByte() }
        val localDek = ByteArray(32) { 0xBB.toByte() }
        cloud.metadata = VaultMetadata(salt = "cloud-salt", verification = "cv", wrappedDek = "cw")
        metadata.salt = "local-salt"
        metadata.wrapped = "lw"
        crypto.cloudUnwrap = Result.success(cloudDek)
        crypto.localUnwrap = Result.success(localDek)

        val outcome = useCase("password").getOrThrow() as VaultReconciliationOutcome.MismatchRequiresClobber

        assertArrayEquals(cloudDek, outcome.cloudDek)
        assertEquals("cloud-salt", outcome.cloudSalt)
        assertEquals("cw", outcome.cloudWrappedDek)
        assertEquals("cv", outcome.cloudVerification)
        assertNull(crypto.stashedDek, "Detector must not cache anything")
    }

    @Test
    fun `mismatched DEKs but local unwrap fails returns MismatchRequiresClobber`() = runTest {
        val cloudDek = ByteArray(32) { 0xAA.toByte() }
        cloud.metadata = VaultMetadata(salt = "cloud-salt", verification = null, wrappedDek = "cw")
        metadata.salt = "local-salt"
        metadata.wrapped = "lw"
        crypto.cloudUnwrap = Result.success(cloudDek)
        crypto.localUnwrap = Result.failure(javax.crypto.AEADBadTagException("local bad — orphan"))

        val outcome = useCase("password").getOrThrow()

        assertEquals(
            VaultReconciliationOutcome.MismatchRequiresClobber(
                cloudDek = cloudDek,
                cloudSalt = "cloud-salt",
                cloudWrappedDek = "cw",
                cloudVerification = null,
            ),
            outcome,
        )
        assertFalse(crypto.stashedDek != null, "Detector must not cache anything")
    }

    private class FakeCrypto : CryptoManagerPort {
        var cloudUnwrap: Result<ByteArray> = Result.success(ByteArray(32))
        var localUnwrap: Result<ByteArray> = Result.success(ByteArray(32))
        var stashedDek: ByteArray? = null
        var unwrapInvocationCount = 0

        override fun deriveKek(password: String, salt: ByteArray): Result<ByteArray> =
            Result.success(salt + ByteArray(32 - salt.size))

        override fun generateDek(size: Int): ByteArray = ByteArray(size)
        override fun wrapDek(dek: ByteArray, kek: ByteArray): Result<ByteArray> = Result.success(dek)
        override fun unwrapDek(wrapped: ByteArray, kek: ByteArray): Result<ByteArray> {
            return when (unwrapInvocationCount++) {
                0 -> cloudUnwrap
                1 -> localUnwrap
                else -> error("Unexpected unwrap call #$unwrapInvocationCount")
            }
        }

        override fun cacheDek(dek: ByteArray): Result<Unit> {
            stashedDek = dek
            return Result.success(Unit)
        }

        override fun getCachedDek(): ByteArray? = stashedDek
        override fun clearKey() { stashedDek = null }
        override fun hasCachedKey(): Boolean = stashedDek != null
        override val isVaultUnlocked: StateFlow<Boolean> = MutableStateFlow(true)
        override fun encrypt(plaintext: String, aad: ByteArray?): Result<ByteArray> = error("not used")
        override fun encryptBytes(data: ByteArray, aad: ByteArray?): Result<ByteArray> = error("not used")
        override fun decrypt(ciphertext: ByteArray, aad: ByteArray?): Result<String> = error("not used")
        override fun decryptBytes(ciphertext: ByteArray, aad: ByteArray?): Result<ByteArray> = error("not used")
        override fun generateSalt(): ByteArray = ByteArray(16)
        override fun encodeBase64(data: ByteArray): String = data.joinToString("") { "%02x".format(it) }
        override fun decodeBase64(base64: String): ByteArray = base64.toByteArray()
        override fun generateRandomKey(size: Int): ByteArray = ByteArray(size)
        override fun generateRandomIv(size: Int): ByteArray = ByteArray(size)
        override fun encryptWithKey(data: ByteArray, key: ByteArray, iv: ByteArray): Result<ByteArray> = error("not used")
        override fun decryptWithKey(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): Result<ByteArray> = error("not used")
    }

    private class FakeMetadata : VaultMetadataPort {
        var salt: String? = null
        var verification: String? = null
        var wrapped: String? = null

        override suspend fun getVaultSalt(): String? = salt
        override suspend fun saveVaultSalt(salt: String) { this.salt = salt }
        override suspend fun getVaultVerificationData(): String? = verification
        override suspend fun saveVaultVerificationData(ciphertext: String?) { verification = ciphertext }
        override suspend fun getWrappedDek(): String? = wrapped
        override suspend fun saveWrappedDek(wrappedDek: String?) { wrapped = wrappedDek }
    }

    private class FakeCloud : CloudSyncPort {
        var metadata: VaultMetadata? = null

        override fun startListening() = Unit
        override fun stopListening() = Unit
        override suspend fun syncAllLocalDataToCloud(): Result<Unit> = Result.success(Unit)
        override suspend fun syncEntitiesToCloud(chats: List<Chat>, messages: List<Message>): Result<Unit> = Result.success(Unit)
        override suspend fun syncPendingChanges(): Result<Unit> = Result.success(Unit)
        override suspend fun updateVaultSalt(salt: String): Result<Unit> = Result.success(Unit)
        override suspend fun getVaultSalt(): Result<String?> = Result.success(metadata?.salt)
        override suspend fun updateVaultVerificationData(ciphertext: String): Result<Unit> = Result.success(Unit)
        override suspend fun getVaultVerificationData(): Result<String?> = Result.success(metadata?.verification)
        override suspend fun updateWrappedDek(wrappedDek: String?): Result<Unit> = Result.success(Unit)
        override suspend fun getWrappedDek(): Result<String?> = Result.success(metadata?.wrappedDek)
        override suspend fun uploadVaultMetadata(metadata: VaultMetadata): Result<Unit> = Result.success(Unit)
        override suspend fun fetchVaultMetadata(): Result<VaultMetadata?> = Result.success(metadata)
        override suspend fun deleteUserAccount(): Result<Unit> = Result.success(Unit)
        override fun enqueueEmbeddingRegeneration() = Unit
    }
}
