package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.model.security.VaultMetadata
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.port.security.VaultMetadataPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UnlockVaultFromStoredMetadataUseCaseTest {
    private val unlockVaultUseCase = mockk<UnlockVaultUseCase>()
    private val cryptoManager = mockk<CryptoManagerPort>()
    private val vaultMetadataPort = mockk<VaultMetadataPort>()
    private val cloudSyncPort = mockk<CloudSyncPort>()

    private lateinit var useCase: UnlockVaultFromStoredMetadataUseCase

    @BeforeEach
    fun setUp() {
        useCase =
            UnlockVaultFromStoredMetadataUseCase(
                unlockVaultUseCase = unlockVaultUseCase,
                cryptoManager = cryptoManager,
                vaultMetadataPort = vaultMetadataPort,
                cloudSyncPort = cloudSyncPort,
            )
    }

    @Test
    fun `SC-ARCH-01-01 local metadata unlock delegates to UnlockVaultUseCase`() =
        runTest {
            val salt = ByteArray(16) { 0x11 }
            val wrapped = ByteArray(32) { 0x22 }
            coEvery { vaultMetadataPort.getVaultSalt() } returns "salt-b64"
            coEvery { vaultMetadataPort.getWrappedDek() } returns "wrapped-b64"
            every { cryptoManager.decodeBase64("salt-b64") } returns salt
            every { cryptoManager.decodeBase64("wrapped-b64") } returns wrapped
            coEvery { unlockVaultUseCase("pw", salt, wrapped) } returns Result.success(Unit)

            val result = useCase("pw")

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { unlockVaultUseCase("pw", salt, wrapped) }
            coVerify(exactly = 0) { cloudSyncPort.fetchVaultMetadata() }
        }

    @Test
    fun `SC-ARCH-01-02 cloud metadata unlock fetches persists and unlocks`() =
        runTest {
            val salt = ByteArray(16) { 0x11 }
            val wrapped = ByteArray(32) { 0x22 }
            coEvery { vaultMetadataPort.getVaultSalt() } returns null
            coEvery { vaultMetadataPort.getWrappedDek() } returns null
            coEvery { cloudSyncPort.fetchVaultMetadata() } returns
                Result.success(
                    VaultMetadata(
                        salt = "cloud-salt",
                        verification = "verification-b64",
                        wrappedDek = "cloud-wrapped",
                    ),
                )
            every { cryptoManager.decodeBase64("cloud-salt") } returns salt
            every { cryptoManager.decodeBase64("cloud-wrapped") } returns wrapped
            coEvery { unlockVaultUseCase("pw", salt, wrapped) } returns Result.success(Unit)
            coEvery { vaultMetadataPort.saveVaultSalt("cloud-salt") } returns Unit
            coEvery { vaultMetadataPort.saveWrappedDek("cloud-wrapped") } returns Unit
            coEvery { vaultMetadataPort.saveVaultVerificationData("verification-b64") } returns Unit

            val result = useCase("pw")

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { vaultMetadataPort.saveVaultSalt("cloud-salt") }
            coVerify(exactly = 1) { vaultMetadataPort.saveWrappedDek("cloud-wrapped") }
        }

    @Test
    fun `SC-ARCH-01-03 cloud metadata missing returns failure`() =
        runTest {
            coEvery { vaultMetadataPort.getVaultSalt() } returns null
            coEvery { vaultMetadataPort.getWrappedDek() } returns null
            coEvery { cloudSyncPort.fetchVaultMetadata() } returns Result.success(null)

            val result = useCase("pw")

            assertTrue(result.isFailure)
            assertEquals("Cloud vault metadata missing", result.exceptionOrNull()?.message)
        }
}
