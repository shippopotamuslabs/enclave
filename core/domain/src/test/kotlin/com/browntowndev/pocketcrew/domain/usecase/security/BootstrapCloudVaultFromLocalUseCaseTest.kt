package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.security.VaultMetadata
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.VaultMetadataPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BootstrapCloudVaultFromLocalUseCaseTest {
    private lateinit var metadata: FakeMetadata
    private lateinit var cloud: FakeCloud
    private lateinit var useCase: BootstrapCloudVaultFromLocalUseCase

    @BeforeEach
    fun setUp() {
        metadata = FakeMetadata()
        cloud = FakeCloud()
        useCase = BootstrapCloudVaultFromLocalUseCase(metadata, cloud)
    }

    @Test
    fun `bootstrap uploads local salt wrappedDek and verification`() =
        runTest {
            metadata.salt = "local-salt"
            metadata.wrapped = "local-wrapped"
            metadata.verification = "local-verification"

            useCase().getOrThrow()

            assertEquals(
                VaultMetadata(
                    salt = "local-salt",
                    verification = "local-verification",
                    wrappedDek = "local-wrapped",
                ),
                cloud.lastUploadedMetadata,
            )
        }

    @Test
    fun `bootstrap fails when local salt missing`() =
        runTest {
            metadata.salt = null
            metadata.wrapped = "local-wrapped"

            val result = useCase()

            assertTrue(result.isFailure)
            assertNull(cloud.lastUploadedMetadata)
        }

    @Test
    fun `bootstrap fails when local wrappedDek missing`() =
        runTest {
            metadata.salt = "local-salt"
            metadata.wrapped = null

            val result = useCase()

            assertTrue(result.isFailure)
            assertNull(cloud.lastUploadedMetadata)
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

    private class FakeCloud : CloudSyncPort {
        var lastUploadedMetadata: VaultMetadata? = null

        override fun startListening() = Unit

        override fun stopListening() = Unit

        override suspend fun syncAllLocalDataToCloud(): Result<Unit> = Result.success(Unit)

        override suspend fun syncEntitiesToCloud(
            chats: List<Chat>,
            messages: List<Message>,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun syncPendingChanges(): Result<Unit> = Result.success(Unit)

        override suspend fun updateVaultSalt(salt: String): Result<Unit> = Result.success(Unit)

        override suspend fun getVaultSalt(): Result<String?> = Result.success(null)

        override suspend fun updateVaultVerificationData(ciphertext: String): Result<Unit> = Result.success(Unit)

        override suspend fun getVaultVerificationData(): Result<String?> = Result.success(null)

        override suspend fun updateWrappedDek(wrappedDek: String?): Result<Unit> = Result.success(Unit)

        override suspend fun getWrappedDek(): Result<String?> = Result.success(null)

        override suspend fun uploadVaultMetadata(metadata: VaultMetadata): Result<Unit> {
            lastUploadedMetadata = metadata
            return Result.success(Unit)
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
