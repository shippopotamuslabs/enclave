package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.port.repository.SyncOrchestrator
import com.browntowndev.enclave.domain.port.security.BiometricPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import com.browntowndev.enclave.domain.usecase.auth.LoginWithGoogleUseCase.Companion.VAULT_VERIFICATION_MAGIC_STRING
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VaultSessionInteractorTest {
    private val cryptoManager = mockk<CryptoManagerPort>(relaxUnitFun = true)
    private val cloudSyncPort = mockk<CloudSyncPort>(relaxUnitFun = true)
    private val vaultMetadataPort = mockk<VaultMetadataPort>(relaxUnitFun = true)
    private val biometricPort = mockk<BiometricPort>(relaxUnitFun = true)
    private val settingsRepository = mockk<SettingsRepository>()
    private val syncOrchestrator = mockk<SyncOrchestrator>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

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
        every { cryptoManager.encodeBase64(any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { cryptoManager.decodeBase64(any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @Test
    fun `isVaultLocked reflects cached key`() {
        every { cryptoManager.hasCachedKey() } returns false
        assertTrue(interactor.isVaultLocked())
        every { cryptoManager.hasCachedKey() } returns true
        assertFalse(interactor.isVaultLocked())
    }

    @Test
    fun `biometric unlock possible requires locked available and secure value`() {
        every { biometricPort.isBiometricAvailable() } returns true
        every { biometricPort.hasSecureValue(any()) } returns true
        assertTrue(interactor.isBiometricUnlockPossible(isLocked = true))
        assertFalse(interactor.isBiometricUnlockPossible(isLocked = false))
    }

    @Test
    fun `resolveOrCreateSalt prefers local salt`() =
        runTest {
            coEvery { vaultMetadataPort.getVaultSalt() } returns "c2FsdA=="

            val (base64, bytes) = interactor.resolveOrCreateSalt()

            assertEquals("c2FsdA==", base64)
            assertEquals("salt", bytes.decodeToString())
            coVerify(exactly = 0) { cloudSyncPort.getVaultSalt() }
        }

    @Test
    fun `resolveOrCreateSalt falls back to cloud and persists locally`() =
        runTest {
            coEvery { vaultMetadataPort.getVaultSalt() } returns null
            coEvery { cloudSyncPort.getVaultSalt() } returns Result.success("Y2xvdWQ=")

            val (base64, bytes) = interactor.resolveOrCreateSalt()

            assertEquals("Y2xvdWQ=", base64)
            assertEquals("cloud", bytes.decodeToString())
            coVerify { vaultMetadataPort.saveVaultSalt("Y2xvdWQ=") }
        }

    @Test
    fun `resolveOrCreateSalt generates when absent everywhere`() =
        runTest {
            coEvery { vaultMetadataPort.getVaultSalt() } returns null
            coEvery { cloudSyncPort.getVaultSalt() } returns Result.success(null)
            every { cryptoManager.generateSalt() } returns "fresh".encodeToByteArray()
            coEvery { cloudSyncPort.updateVaultSalt(any()) } returns Result.success(Unit)

            val (base64, bytes) = interactor.resolveOrCreateSalt()

            assertEquals("fresh", bytes.decodeToString())
            coVerify { vaultMetadataPort.saveVaultSalt(base64) }
        }

    @Test
    fun `verifyMagic true only when decrypt yields magic string`() {
        every { cryptoManager.decrypt(any(), any()) } returns Result.success(VAULT_VERIFICATION_MAGIC_STRING)
        assertTrue(interactor.verifyMagic("Y2lwaGVy"))

        every { cryptoManager.decrypt(any(), any()) } returns Result.success("other")
        assertFalse(interactor.verifyMagic("Y2lwaGVy"))

        assertFalse(interactor.verifyMagic(null, requireVerification = true))
        assertTrue(interactor.verifyMagic(null, requireVerification = false))
    }

    @Test
    fun `onboardingCompleted reads first settings emission`() =
        runTest {
            every { settingsRepository.settingsFlow } returns
                MutableStateFlow(SettingsData(onboardingCompleted = true))

            assertTrue(interactor.onboardingCompleted())
            assertFalse(
                VaultSessionInteractor(
                    cryptoManager,
                    cloudSyncPort,
                    vaultMetadataPort,
                    biometricPort,
                    mockk<SettingsRepository> {
                        every { settingsFlow } returns flowOf(SettingsData(onboardingCompleted = false))
                    },
                    syncOrchestrator,
                    loggingPort,
                ).onboardingCompleted(),
            )
        }

    @Test
    fun `startInitialSync delegates to orchestrator`() =
        runTest {
            coEvery { syncOrchestrator.startInitialSync(true) } returns Result.success(Unit)

            assertTrue(interactor.startInitialSync(initialUpload = true).isSuccess)
            coVerify { syncOrchestrator.startInitialSync(true) }
        }

    @Test
    fun `clearKeyAndBiometricValue clears both stores`() {
        interactor.clearKeyAndBiometricValue()

        verify { cryptoManager.clearKey() }
        verify { biometricPort.clearSecureValue(any()) }
    }
}
