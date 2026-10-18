package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeVaultMetadataPort
import com.browntowndev.enclave.domain.model.security.VaultMetadata
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Recording [UnlockVaultUseCase] fake (ENC-28): captures (password, salt, wrappedDek) per call. */
private class FakeUnlockVaultUseCase(
    cryptoManager: FakeCryptoManagerPort,
) : UnlockVaultUseCase(cryptoManager, FakeApiModelRepository(), FakeApiKeyProvider()) {
    data class UnlockCall(
        val password: String,
        val salt: ByteArray,
        val wrappedDek: ByteArray,
    )

    val calls = mutableListOf<UnlockCall>()
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun invoke(
        password: String,
        salt: ByteArray,
        wrappedDek: ByteArray,
    ): Result<Unit> {
        calls += UnlockCall(password, salt, wrappedDek)
        return result
    }
}

class UnlockVaultFromStoredMetadataUseCaseTest {
    private val cryptoManager = FakeCryptoManagerPort()
    private val unlockVaultUseCase = FakeUnlockVaultUseCase(cryptoManager)
    private val vaultMetadataPort = FakeVaultMetadataPort()
    private val cloudSyncPort = FakeCloudSyncPort()

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
            vaultMetadataPort.salt = "salt-b64"
            vaultMetadataPort.wrappedDek = "wrapped-b64"
            cryptoManager.decodeBase64Override = { encoded ->
                when (encoded) {
                    "salt-b64" -> salt
                    "wrapped-b64" -> wrapped
                    else -> error("Unexpected base64 input: $encoded")
                }
            }
            unlockVaultUseCase.result = Result.success(Unit)

            val result = useCase("pw")

            assertTrue(result.isSuccess)
            assertEquals(1, unlockVaultUseCase.calls.size)
            val call = unlockVaultUseCase.calls.single()
            assertEquals("pw", call.password)
            assertTrue(salt.contentEquals(call.salt))
            assertTrue(wrapped.contentEquals(call.wrappedDek))
            assertEquals(0, cloudSyncPort.count("fetchVaultMetadata"))
        }

    @Test
    fun `SC-ARCH-01-02 cloud metadata unlock fetches persists and unlocks`() =
        runTest {
            val salt = ByteArray(16) { 0x11 }
            val wrapped = ByteArray(32) { 0x22 }
            vaultMetadataPort.salt = null
            vaultMetadataPort.wrappedDek = null
            cloudSyncPort.fetchVaultMetadataResult =
                Result.success(
                    VaultMetadata(
                        salt = "cloud-salt",
                        verification = "verification-b64",
                        wrappedDek = "cloud-wrapped",
                    ),
                )
            cryptoManager.decodeBase64Override = { encoded ->
                when (encoded) {
                    "cloud-salt" -> salt
                    "cloud-wrapped" -> wrapped
                    else -> error("Unexpected base64 input: $encoded")
                }
            }
            unlockVaultUseCase.result = Result.success(Unit)

            val result = useCase("pw")

            assertTrue(result.isSuccess)
            assertEquals(listOf("cloud-salt"), vaultMetadataPort.savedSalts)
            assertEquals(listOf("cloud-wrapped"), vaultMetadataPort.savedWrappedDeks)
        }

    @Test
    fun `SC-ARCH-01-03 cloud metadata missing returns failure`() =
        runTest {
            vaultMetadataPort.salt = null
            vaultMetadataPort.wrappedDek = null
            cloudSyncPort.fetchVaultMetadataResult = Result.success(null)

            val result = useCase("pw")

            assertTrue(result.isFailure)
            assertEquals("Cloud vault metadata missing", result.exceptionOrNull()?.message)
        }
}
