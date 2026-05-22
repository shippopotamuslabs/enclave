package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.security.VaultMetadata
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChangePasswordUseCaseTest {
    private lateinit var crypto: FakeCrypto
    private lateinit var metadata: FakeMetadata
    private lateinit var cloud: FakeCloud
    private lateinit var useCase: ChangePasswordUseCase

    private val originalSalt = ByteArray(16) { 0xAA.toByte() }
    private val originalWrappedDek = ByteArray(60) { 0xBB.toByte() }
    private val originalDek = ByteArray(32) { 0xCC.toByte() }

    @BeforeEach
    fun setUp() {
        crypto = FakeCrypto(initialDek = originalDek.copyOf())
        metadata =
            FakeMetadata(
                saltBase64 = "salt-base64",
                wrappedDekBase64 = "wrapped-base64",
                verification = "verification-base64",
            )
        cloud = FakeCloud()
        useCase = ChangePasswordUseCase(crypto, metadata, cloud)

        crypto.base64Decode["salt-base64"] = originalSalt
        crypto.base64Decode["wrapped-base64"] = originalWrappedDek
        crypto.deriveResults[KekRequest("old-password", originalSalt.toHex())] = Result.success(ByteArray(32) { 0x01 })
        crypto.unwrapResults[UnwrapRequest(originalWrappedDek.toHex(), ByteArray(32) { 0x01 }.toHex())] =
            Result.success(originalDek.copyOf())
    }

    @Test
    fun `re-wraps DEK and persists new salt and wrappedDek atomically`() =
        runTest {
            val newDerivedKek = ByteArray(32) { 0xDE.toByte() }
            val newSalt = ByteArray(16) { 0xEF.toByte() }
            val newWrappedDek = ByteArray(60) { 0xFF.toByte() }
            crypto.nextSalt = newSalt
            crypto.deriveResults[KekRequest("new-password", newSalt.toHex())] = Result.success(newDerivedKek)
            crypto.wrapResults[WrapRequest(originalDek.toHex(), newDerivedKek.toHex())] = Result.success(newWrappedDek)
            crypto.base64Encode[newSalt.toHex()] = "new-salt-base64"
            crypto.base64Encode[newWrappedDek.toHex()] = "new-wrapped-base64"

            val result = useCase("old-password", "new-password")

            assertTrue(result.isSuccess)
            assertEquals("new-salt-base64", metadata.saltBase64)
            assertEquals("new-wrapped-base64", metadata.wrappedDekBase64)
            assertEquals("verification-base64", metadata.verification, "verification ciphertext is unchanged")
            assertEquals(1, cloud.uploaded.size)
            val uploaded = cloud.uploaded.first()
            assertEquals("new-salt-base64", uploaded.salt)
            assertEquals("new-wrapped-base64", uploaded.wrappedDek)
            assertEquals("verification-base64", uploaded.verification)
        }

    @Test
    fun `rolls back local metadata when cloud upload fails`() =
        runTest {
            val newDerivedKek = ByteArray(32) { 0xDE.toByte() }
            val newSalt = ByteArray(16) { 0xEF.toByte() }
            val newWrappedDek = ByteArray(60) { 0xFF.toByte() }
            crypto.nextSalt = newSalt
            crypto.deriveResults[KekRequest("new-password", newSalt.toHex())] = Result.success(newDerivedKek)
            crypto.wrapResults[WrapRequest(originalDek.toHex(), newDerivedKek.toHex())] = Result.success(newWrappedDek)
            crypto.base64Encode[newSalt.toHex()] = "new-salt-base64"
            crypto.base64Encode[newWrappedDek.toHex()] = "new-wrapped-base64"
            cloud.uploadResult = Result.failure(RuntimeException("cloud down"))

            val result = useCase("old-password", "new-password")

            assertTrue(result.isFailure)
            assertEquals("salt-base64", metadata.saltBase64)
            assertEquals("wrapped-base64", metadata.wrappedDekBase64)
            assertEquals(1, cloud.uploaded.size)
        }

    @Test
    fun `wrong old password rejects with no state changes`() =
        runTest {
            crypto.unwrapResults[UnwrapRequest(originalWrappedDek.toHex(), ByteArray(32) { 0x01 }.toHex())] =
                Result.failure(javax.crypto.AEADBadTagException("bad tag"))

            val result = useCase("old-password", "new-password")

            assertTrue(result.isFailure)
            assertEquals("salt-base64", metadata.saltBase64)
            assertEquals("wrapped-base64", metadata.wrappedDekBase64)
            assertTrue(cloud.uploaded.isEmpty())
        }

    @Test
    fun `unwrapped DEK that mismatches cached DEK fails (corruption guard)`() =
        runTest {
            val attackerDek = ByteArray(32) { 0xDD.toByte() }
            crypto.unwrapResults[UnwrapRequest(originalWrappedDek.toHex(), ByteArray(32) { 0x01 }.toHex())] = Result.success(attackerDek)

            val result = useCase("old-password", "new-password")

            assertTrue(result.isFailure)
            assertTrue(cloud.uploaded.isEmpty())
        }

    @Test
    fun `vault locked - returns failure without touching anything`() =
        runTest {
            crypto.dek = null

            val result = useCase("old-password", "new-password")

            assertTrue(result.isFailure)
            assertEquals("salt-base64", metadata.saltBase64)
            assertTrue(cloud.uploaded.isEmpty())
        }

    @Test
    fun `blank new password is rejected`() =
        runTest {
            val result = useCase("old-password", "")

            assertTrue(result.isFailure)
        }

    @Test
    fun `does not call any DAO writes - room data untouched`() =
        runTest {
            // Sanity: no Room/database port is even injected into the use case.
            // Composition over inheritance: this test documents the contract.
            val newDerivedKek = ByteArray(32) { 0xDE.toByte() }
            val newSalt = ByteArray(16) { 0xEF.toByte() }
            val newWrappedDek = ByteArray(60) { 0xFF.toByte() }
            crypto.nextSalt = newSalt
            crypto.deriveResults[KekRequest("new-password", newSalt.toHex())] = Result.success(newDerivedKek)
            crypto.wrapResults[WrapRequest(originalDek.toHex(), newDerivedKek.toHex())] = Result.success(newWrappedDek)
            crypto.base64Encode[newSalt.toHex()] = "new-salt-base64"
            crypto.base64Encode[newWrappedDek.toHex()] = "new-wrapped-base64"

            useCase("old-password", "new-password")

            assertNotNull(cloud.uploaded.firstOrNull())
            assertNull(cloud.calledRoomMethod)
        }

    private data class KekRequest(
        val password: String,
        val saltHex: String,
    )

    private data class UnwrapRequest(
        val wrappedHex: String,
        val kekHex: String,
    )

    private data class WrapRequest(
        val dekHex: String,
        val kekHex: String,
    )

    private class FakeCrypto(
        initialDek: ByteArray?,
    ) : CryptoManagerPort {
        var dek: ByteArray? = initialDek
        val base64Decode = mutableMapOf<String, ByteArray>()
        val base64Encode = mutableMapOf<String, String>()
        val deriveResults = mutableMapOf<KekRequest, Result<ByteArray>>()
        val unwrapResults = mutableMapOf<UnwrapRequest, Result<ByteArray>>()
        val wrapResults = mutableMapOf<WrapRequest, Result<ByteArray>>()
        var nextSalt: ByteArray = ByteArray(16)

        override fun deriveKek(
            password: String,
            salt: ByteArray,
        ): Result<ByteArray> {
            val req = KekRequest(password, salt.toHex())
            return deriveResults[req] ?: error("Unexpected deriveKek($req); known=${deriveResults.keys}")
        }

        override fun generateDek(size: Int): ByteArray = ByteArray(size)

        override fun wrapDek(
            dek: ByteArray,
            kek: ByteArray,
        ): Result<ByteArray> {
            val req = WrapRequest(dek.toHex(), kek.toHex())
            return wrapResults[req] ?: error("Unexpected wrapDek($req); known=${wrapResults.keys}")
        }

        override fun unwrapDek(
            wrapped: ByteArray,
            kek: ByteArray,
        ): Result<ByteArray> {
            val req = UnwrapRequest(wrapped.toHex(), kek.toHex())
            return unwrapResults[req] ?: error("Unexpected unwrapDek($req); known=${unwrapResults.keys}")
        }

        override fun cacheDek(dek: ByteArray): Result<Unit> = Result.success(Unit)

        override fun getCachedDek(): ByteArray? = dek

        override fun clearKey() = Unit

        override fun hasCachedKey(): Boolean = dek != null

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

        override fun generateSalt(): ByteArray = nextSalt

        override fun encodeBase64(data: ByteArray): String = base64Encode[data.toHex()] ?: data.joinToString("") { "%02x".format(it) }

        override fun decodeBase64(base64: String): ByteArray =
            base64Decode[base64] ?: error("Unexpected decodeBase64($base64); known=${base64Decode.keys}")

        override fun generateRandomKey(size: Int): ByteArray = ByteArray(size)

        override fun generateRandomIv(size: Int): ByteArray = ByteArray(size)

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

    private class FakeMetadata(
        var saltBase64: String?,
        var wrappedDekBase64: String?,
        var verification: String?,
    ) : VaultMetadataPort {
        override suspend fun getVaultSalt(): String? = saltBase64

        override suspend fun saveVaultSalt(salt: String) {
            saltBase64 = salt
        }

        override suspend fun getVaultVerificationData(): String? = verification

        override suspend fun saveVaultVerificationData(ciphertext: String?) {
            verification = ciphertext
        }

        override suspend fun getWrappedDek(): String? = wrappedDekBase64

        override suspend fun saveWrappedDek(wrappedDek: String?) {
            wrappedDekBase64 = wrappedDek
        }
    }

    private class FakeCloud : CloudSyncPort {
        val uploaded = mutableListOf<VaultMetadata>()
        var calledRoomMethod: String? = null
        var uploadResult: Result<Unit> = Result.success(Unit)

        override fun startListening() {
            calledRoomMethod = "startListening"
        }

        override fun stopListening() = Unit

        override suspend fun syncAllLocalDataToCloud(): Result<Unit> {
            calledRoomMethod = "syncAllLocalDataToCloud"
            return Result.success(Unit)
        }

        override suspend fun syncEntitiesToCloud(
            chats: List<Chat>,
            messages: List<Message>,
        ): Result<Unit> {
            calledRoomMethod = "syncEntitiesToCloud"
            return Result.success(Unit)
        }

        override suspend fun syncPendingChanges(): Result<Unit> {
            calledRoomMethod = "syncPendingChanges"
            return Result.success(Unit)
        }

        override suspend fun syncRemoteChangesFromCloud(): Result<com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult> =
            Result.success(
                com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult(
                    eventsApplied = 0,
                    lastAppliedSequence = 0,
                    hasMore = false,
                ),
            )

        override suspend fun updateVaultSalt(salt: String): Result<Unit> = Result.success(Unit)

        override suspend fun getVaultSalt(): Result<String?> = Result.success(null)

        override suspend fun updateVaultVerificationData(ciphertext: String): Result<Unit> = Result.success(Unit)

        override suspend fun getVaultVerificationData(): Result<String?> = Result.success(null)

        override suspend fun updateWrappedDek(wrappedDek: String?): Result<Unit> = Result.success(Unit)

        override suspend fun getWrappedDek(): Result<String?> = Result.success(null)

        override suspend fun uploadVaultMetadata(metadata: VaultMetadata): Result<Unit> {
            uploaded += metadata
            return uploadResult
        }

        override suspend fun fetchVaultMetadata(): Result<VaultMetadata?> = Result.success(null)

        override suspend fun deleteUserAccount(): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAllChatsFromCloud(): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAllMediaFromCloud(): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAllMemoriesFromCloud(): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAllArtifactsFromCloud(): Result<Unit> = Result.success(Unit)

        override fun enqueueEmbeddingRegeneration() = Unit
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
