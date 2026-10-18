package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.core.testing.FakeBiometricPort
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.core.testing.FakeSyncOrchestrator
import com.browntowndev.enclave.core.testing.FakeVaultMetadataPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.auth.LoginWithGoogleUseCase.Companion.VAULT_VERIFICATION_MAGIC_STRING
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VaultSessionInteractorTest {
    private val cryptoManager = FakeCryptoManagerPort()
    private val cloudSyncPort = FakeCloudSyncPort()
    private val vaultMetadataPort = FakeVaultMetadataPort()
    private val biometricPort = FakeBiometricPort()
    private val settingsRepository = FakeSettingsRepository()
    private val syncOrchestrator = FakeSyncOrchestrator()
    private val loggingPort = FakeLoggingPort()

    private lateinit var interactor: VaultSessionInteractor

    @BeforeEach
    fun setUp() {
        interactor =
            VaultSessionInteractor(
                cryptoManager = cryptoManager,
                cloudSyncPort = cloudSyncPort,
                vaultMetadataPort = vaultMetadataPort,
                biometricPort = biometricPort,
                settingsRepository = settingsRepository,
                syncOrchestrator = syncOrchestrator,
                loggingPort = loggingPort,
            )
    }

    @Test
    fun `isVaultLocked reflects cached key`() {
        cryptoManager.hasCachedKeyValue = false
        assertTrue(interactor.isVaultLocked())
        cryptoManager.hasCachedKeyValue = true
        assertFalse(interactor.isVaultLocked())
    }

    @Test
    fun `biometric unlock possible requires locked available and secure value`() {
        biometricPort.isAvailable = true
        biometricPort.hasSecureValueValue = true
        assertTrue(interactor.isBiometricUnlockPossible(isLocked = true))
        assertFalse(interactor.isBiometricUnlockPossible(isLocked = false))
    }

    @Test
    fun `resolveOrCreateSalt prefers local salt`() =
        runTest {
            vaultMetadataPort.salt = "c2FsdA=="

            val (base64, bytes) = interactor.resolveOrCreateSalt()

            assertEquals("c2FsdA==", base64)
            assertEquals("salt", bytes.decodeToString())
            assertEquals(0, cloudSyncPort.count("getVaultSalt"))
        }

    @Test
    fun `resolveOrCreateSalt falls back to cloud and persists locally`() =
        runTest {
            vaultMetadataPort.salt = null
            cloudSyncPort.vaultSaltResult = Result.success("Y2xvdWQ=")

            val (base64, bytes) = interactor.resolveOrCreateSalt()

            assertEquals("Y2xvdWQ=", base64)
            assertEquals("cloud", bytes.decodeToString())
            assertEquals(listOf("Y2xvdWQ="), vaultMetadataPort.savedSalts)
        }

    @Test
    fun `resolveOrCreateSalt generates when absent everywhere`() =
        runTest {
            vaultMetadataPort.salt = null
            cloudSyncPort.vaultSaltResult = Result.success(null)
            cryptoManager.generatedSalt = "fresh".encodeToByteArray()

            val (base64, bytes) = interactor.resolveOrCreateSalt()

            assertEquals("fresh", bytes.decodeToString())
            assertEquals(listOf(base64), vaultMetadataPort.savedSalts)
        }

    @Test
    fun `verifyMagic true only when decrypt yields magic string`() {
        cryptoManager.decryptResult = Result.success(VAULT_VERIFICATION_MAGIC_STRING)
        assertTrue(interactor.verifyMagic("Y2lwaGVy"))

        cryptoManager.decryptResult = Result.success("other")
        assertFalse(interactor.verifyMagic("Y2lwaGVy"))

        assertFalse(interactor.verifyMagic(null, requireVerification = true))
        assertTrue(interactor.verifyMagic(null, requireVerification = false))
    }

    @Test
    fun `onboardingCompleted reads first settings emission`() =
        runTest {
            settingsRepository.seed(SettingsData(onboardingCompleted = true))

            assertTrue(interactor.onboardingCompleted())
            assertFalse(
                VaultSessionInteractor(
                    cryptoManager,
                    cloudSyncPort,
                    vaultMetadataPort,
                    biometricPort,
                    FakeSettingsRepository(SettingsData(onboardingCompleted = false)),
                    syncOrchestrator,
                    loggingPort,
                ).onboardingCompleted(),
            )
        }

    @Test
    fun `startInitialSync delegates to orchestrator`() =
        runTest {
            assertTrue(interactor.startInitialSync(initialUpload = true).isSuccess)
            assertEquals(listOf(true), syncOrchestrator.startCalls)
        }

    @Test
    fun `clearKeyAndBiometricValue clears both stores`() {
        interactor.clearKeyAndBiometricValue()

        assertEquals(1, cryptoManager.clearKeyCallCount)
        assertEquals(1, biometricPort.clearSecureValueCalls.size)
    }
}
