package com.browntowndev.pocketcrew.presentation

import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.data.security.AndroidBiometricManager
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.port.security.VaultMetadataPort
import com.browntowndev.pocketcrew.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.pocketcrew.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.pocketcrew.domain.usecase.download.InitializeModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.UnlockVaultUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.VaultReconciliationOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.Base64

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val getAuthStateUseCase = mockk<GetAuthStateUseCase>()
    private val loginWithGoogleUseCase = mockk<LoginWithGoogleUseCase>()
    private val initializeModelsUseCase = mockk<InitializeModelsUseCase>()
    private val cryptoManager = mockk<CryptoManagerPort>()
    private val cloudSyncPort = mockk<CloudSyncPort>()
    private val vaultMetadataPort = mockk<VaultMetadataPort>()
    private val biometricManager = mockk<AndroidBiometricManager>()
    private val createVaultUseCase = mockk<CreateVaultUseCase>()
    private val unlockVaultUseCase = mockk<UnlockVaultUseCase>()
    private val detectVaultReconciliationStateUseCase = mockk<DetectVaultReconciliationStateUseCase>()
    private val adoptCloudVaultUseCase = mockk<AdoptCloudVaultUseCase>()
    private val bootstrapCloudVaultFromLocalUseCase = mockk<BootstrapCloudVaultFromLocalUseCase>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()
    private val initializedModelsResult = DownloadModelsResult(
        allModels = emptyMap(),
        modelsToDownload = emptyList(),
        scanResult = ModelScanResult(
            missingModels = emptyList(),
            partialDownloads = emptyMap(),
            allValid = true,
        ),
    )

    private lateinit var viewModel: MainViewModel

    @BeforeEach
    fun setUp() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            Base64.getEncoder().encodeToString(it.invocation.args[0] as ByteArray)
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            Base64.getDecoder().decode(it.invocation.args[0] as String)
        }

        coEvery { initializeModelsUseCase() } returns initializedModelsResult
        every { cryptoManager.hasCachedKey() } returns false
        every { biometricManager.isBiometricAvailable() } returns false
        every { biometricManager.hasSecureValue(any()) } returns false
        every { biometricManager.getEncryptCipher(any()) } returns null
        every { biometricManager.getDecryptCipher(any()) } returns null
        every { getAuthStateUseCase() } returns MutableStateFlow(
            com.browntowndev.pocketcrew.domain.port.auth.AuthState.Unauthenticated,
        )
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
            kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }
        coEvery { vaultMetadataPort.getVaultSalt() } returns "c2FsdA=="
        coEvery { vaultMetadataPort.saveVaultSalt(any()) } returns Unit
        coEvery { vaultMetadataPort.getVaultVerificationData() } returns null
        coEvery { vaultMetadataPort.saveVaultVerificationData(any()) } returns Unit
        coEvery { vaultMetadataPort.getWrappedDek() } returns null
        coEvery { vaultMetadataPort.saveWrappedDek(any()) } returns Unit

        coEvery { detectVaultReconciliationStateUseCase(any()) } returns
            Result.success(VaultReconciliationOutcome.NoCloudVault)
        coEvery { adoptCloudVaultUseCase(any()) } returns Result.success(Unit)
        coEvery { bootstrapCloudVaultFromLocalUseCase() } returns Result.success(Unit)
        coEvery { cloudSyncPort.syncAllLocalDataToCloud() } returns Result.success(Unit)
        every { cloudSyncPort.startListening() } returns Unit

        viewModel = MainViewModel(
            getAuthStateUseCase,
            loginWithGoogleUseCase,
            initializeModelsUseCase,
            cryptoManager,
            cloudSyncPort,
            vaultMetadataPort,
            biometricManager,
            createVaultUseCase,
            unlockVaultUseCase,
            detectVaultReconciliationStateUseCase,
            adoptCloudVaultUseCase,
            bootstrapCloudVaultFromLocalUseCase,
            loggingPort,
            errorHandler,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `unlockVault sets isUnlocking to true then false`() = runTest(testDispatcher) {
        coEvery { vaultMetadataPort.getVaultSalt() } returns null
        coEvery { cloudSyncPort.getVaultSalt() } coAnswers {
            delay(1000)
            Result.success("c2FsdA==")
        }
        coEvery { cloudSyncPort.getWrappedDek() } returns Result.success(null)
        coEvery { cloudSyncPort.getVaultVerificationData() } returns Result.success(null)
        coEvery { cloudSyncPort.updateVaultSalt(any()) } returns Result.success(Unit)
        coEvery { cloudSyncPort.uploadVaultMetadata(any()) } returns Result.success(Unit)
        coEvery { createVaultUseCase(any(), any()) } returns Result.success(byteArrayOf(1, 2, 3))
        every { cryptoManager.encrypt(any()) } returns Result.success(byteArrayOf())
        coEvery { cloudSyncPort.updateVaultVerificationData(any()) } returns Result.success(Unit)
        every { biometricManager.isBiometricAvailable() } returns false

        advanceUntilIdle()
        viewModel.unlockVault("password")

        // Advance slightly to let the coroutine start and reach the first suspension point
        advanceTimeBy(100)

        val intermediateState = viewModel.startupState.value
        if (intermediateState is AppStartupState.Ready) {
            assertTrue(intermediateState.isUnlocking, "isUnlocking should be true during unlock")
        } else {
            throw AssertionError("State should be Ready")
        }

        // Advance past the delay
        advanceTimeBy(1000)
        advanceUntilIdle()

        val finalState = viewModel.startupState.value
        if (finalState is AppStartupState.Ready) {
            assertFalse(finalState.isUnlocking, "isUnlocking should be false after unlock")
        } else {
            throw AssertionError("State should be Ready")
        }
    }

    @Test
    fun `skipLogin does not unlock a locked vault`() = runTest(testDispatcher) {
        every { cryptoManager.hasCachedKey() } returns false

        advanceUntilIdle()
        viewModel.skipLogin()

        val state = viewModel.startupState.value
        if (state is AppStartupState.Ready) {
            assertTrue(state.isVaultLocked, "skipLogin must not bypass the Enclave password")
        } else {
            throw AssertionError("State should be Ready")
        }
    }

    @Test
    fun `locked startup exposes biometric unlock when secure value exists`() = runTest(testDispatcher) {
        every { cryptoManager.hasCachedKey() } returns false
        every { biometricManager.isBiometricAvailable() } returns true
        every { biometricManager.hasSecureValue(any()) } returns true

        viewModel = MainViewModel(
            getAuthStateUseCase,
            loginWithGoogleUseCase,
            initializeModelsUseCase,
            cryptoManager,
            cloudSyncPort,
            vaultMetadataPort,
            biometricManager,
            createVaultUseCase,
            unlockVaultUseCase,
            detectVaultReconciliationStateUseCase,
            adoptCloudVaultUseCase,
            bootstrapCloudVaultFromLocalUseCase,
            loggingPort,
            errorHandler,
        )
        advanceUntilIdle()

        val state = viewModel.startupState.value
        if (state is AppStartupState.Ready) {
            assertTrue(state.isVaultLocked, "Locked vault should stay locked until biometric unlock")
            assertTrue(state.isBiometricUnlockPossible, "Biometric unlock should be offered when secure value exists")
            assertNull(state.initializationError)
        } else {
            throw AssertionError("State should be Ready")
        }

        coVerify(exactly = 0) { initializeModelsUseCase() }
    }

    @Test
    fun `unlockVault treats blank vault metadata as missing and creates a new vault`() = runTest(testDispatcher) {
        val generatedSalt = ByteArray(16) { 0x11.toByte() }
        coEvery { vaultMetadataPort.getVaultSalt() } returns ""
        coEvery { cloudSyncPort.getVaultSalt() } returns Result.success("")
        coEvery { vaultMetadataPort.getWrappedDek() } returns ""
        coEvery { cloudSyncPort.getWrappedDek() } returns Result.success("")
        coEvery { vaultMetadataPort.getVaultVerificationData() } returns ""
        coEvery { cloudSyncPort.getVaultVerificationData() } returns Result.success("")
        every { cryptoManager.generateSalt() } returns generatedSalt
        coEvery { createVaultUseCase("password", generatedSalt) } returns Result.success(byteArrayOf(1, 2, 3))
        every { cryptoManager.encrypt(any()) } returns Result.success(byteArrayOf())
        coEvery { cloudSyncPort.updateVaultSalt(any()) } returns Result.success(Unit)
        coEvery { cloudSyncPort.uploadVaultMetadata(any()) } returns Result.success(Unit)
        coEvery { cloudSyncPort.updateVaultVerificationData(any()) } returns Result.success(Unit)

        advanceUntilIdle()
        viewModel.unlockVault("password")
        advanceUntilIdle()

        coVerify(exactly = 1) { initializeModelsUseCase() }
        coVerify(exactly = 1) { createVaultUseCase("password", generatedSalt) }
        coVerify(exactly = 0) { unlockVaultUseCase(any(), any(), any()) }
        val state = viewModel.startupState.value as AppStartupState.Ready
        assertFalse(state.isVaultLocked)
        assertNotNull(state.modelsResult)
        assertNull(state.vaultErrorMessage)
        assertNull(state.initializationError)
    }

    @Test
    fun `finalizeBiometricEnrollment wraps cached DEK not password`() = runTest(testDispatcher) {
        val dek = ByteArray(32) { 0x42.toByte() }
        every { cryptoManager.getCachedDek() } returns dek
        coEvery { biometricManager.storeSecureBytesWithCipher("vault_dek", dek, any()) } returns Result.success(Unit)
        val cipher = mockk<javax.crypto.Cipher>(relaxed = true)

        advanceUntilIdle()
        viewModel.finalizeBiometricEnrollment(cipher = cipher)
        advanceUntilIdle()

        io.mockk.coVerify { biometricManager.storeSecureBytesWithCipher("vault_dek", dek, cipher) }
        io.mockk.coVerify(exactly = 0) { biometricManager.storeSecureValueWithCipher(any(), any(), any()) }
    }

    @Test
    fun `finalizeBiometricEnrollment skips when vault is locked`() = runTest(testDispatcher) {
        every { cryptoManager.getCachedDek() } returns null
        val cipher = mockk<javax.crypto.Cipher>(relaxed = true)

        advanceUntilIdle()
        viewModel.finalizeBiometricEnrollment(cipher = cipher)
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 0) { biometricManager.storeSecureBytesWithCipher(any(), any(), any()) }
    }

    @Test
    fun `unlockWithBiometricCipher unwraps DEK and caches it`() = runTest(testDispatcher) {
        val dek = ByteArray(32) { 0x55.toByte() }
        val cipher = mockk<javax.crypto.Cipher>(relaxed = true)
        every {
            getAuthStateUseCase()
        } returns MutableStateFlow(
            com.browntowndev.pocketcrew.domain.port.auth.AuthState.Authenticated(
                com.browntowndev.pocketcrew.domain.port.auth.UserProfile("u", "n", "e", null),
            ),
        )
        coEvery { initializeModelsUseCase() } coAnswers {
            delay(1000)
            initializedModelsResult
        }
        every { biometricManager.decryptBytesWithCipher("vault_dek", cipher) } returns dek
        every { cryptoManager.cacheDek(dek) } returns Result.success(Unit)

        viewModel = MainViewModel(
            getAuthStateUseCase,
            loginWithGoogleUseCase,
            initializeModelsUseCase,
            cryptoManager,
            cloudSyncPort,
            vaultMetadataPort,
            biometricManager,
            createVaultUseCase,
            unlockVaultUseCase,
            detectVaultReconciliationStateUseCase,
            adoptCloudVaultUseCase,
            bootstrapCloudVaultFromLocalUseCase,
            loggingPort,
            errorHandler,
        )
        advanceUntilIdle()
        viewModel.unlockWithBiometricCipher(cipher)

        advanceTimeBy(100)

        val intermediateState = viewModel.startupState.value as AppStartupState.Ready
        assertTrue(intermediateState.isUnlocking, "isUnlocking should be true during biometric unlock")

        advanceTimeBy(1000)
        advanceUntilIdle()

        coVerify(exactly = 1) { initializeModelsUseCase() }
        io.mockk.verify { cryptoManager.cacheDek(dek) }
        coVerify(exactly = 0) { cloudSyncPort.syncAllLocalDataToCloud() }
        verify(exactly = 0) { cloudSyncPort.startListening() }
        val state = viewModel.startupState.value as AppStartupState.Ready
        assertFalse(state.isUnlocking, "isUnlocking should be false after biometric unlock")
        assertFalse(state.isVaultLocked)
        assertNotNull(state.modelsResult)
        assertNull(state.initializationError)
    }

    @Test
    fun `unlockWithBiometricCipher surfaces error when unwrap fails`() = runTest(testDispatcher) {
        val cipher = mockk<javax.crypto.Cipher>(relaxed = true)
        every { biometricManager.decryptBytesWithCipher("vault_dek", cipher) } returns null

        advanceUntilIdle()
        viewModel.unlockWithBiometricCipher(cipher)
        advanceUntilIdle()

        io.mockk.verify(exactly = 0) { cryptoManager.cacheDek(any()) }
        coVerify(exactly = 0) { cloudSyncPort.syncAllLocalDataToCloud() }
        verify(exactly = 0) { cloudSyncPort.startListening() }
        val state = viewModel.startupState.value as AppStartupState.Ready
        assertTrue(state.vaultErrorMessage?.contains("biometric", ignoreCase = true) == true)
    }

    /**
     * Stubs the unlock-existing-vault happy path so a unit test can drive the post-unlock
     * reconciliation branch. Authenticates the user so [reconcileWithCloudIfNeeded] runs.
     */
    private fun stubAuthenticatedExistingVaultUnlock() {
        every { getAuthStateUseCase() } returns MutableStateFlow(
            com.browntowndev.pocketcrew.domain.port.auth.AuthState.Authenticated(
                com.browntowndev.pocketcrew.domain.port.auth.UserProfile("u", "n", "e", null),
            ),
        )
        coEvery { vaultMetadataPort.getVaultSalt() } returns "c2FsdA=="
        coEvery { vaultMetadataPort.getWrappedDek() } returns "d3JhcA=="
        coEvery { vaultMetadataPort.getVaultVerificationData() } returns null
        coEvery { unlockVaultUseCase(any(), any(), any()) } returns Result.success(Unit)
        every { cryptoManager.encrypt(any()) } returns Result.success(byteArrayOf())
        coEvery { cloudSyncPort.getVaultVerificationData() } returns Result.success(null)
        coEvery { cloudSyncPort.updateVaultVerificationData(any()) } returns Result.success(Unit)
    }

    @Test
    fun `unlockVault AlreadyMatching starts listener and no pending reconciliation`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            coEvery { detectVaultReconciliationStateUseCase("password") } returns
                Result.success(VaultReconciliationOutcome.AlreadyMatching)

            // Recreate VM so init picks up the new auth state stub
            viewModel = MainViewModel(
                getAuthStateUseCase, loginWithGoogleUseCase, initializeModelsUseCase, cryptoManager,
                cloudSyncPort, vaultMetadataPort, biometricManager, createVaultUseCase, unlockVaultUseCase,
                detectVaultReconciliationStateUseCase, adoptCloudVaultUseCase,
                bootstrapCloudVaultFromLocalUseCase, loggingPort, errorHandler,
            )
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            verify(exactly = 1) { cloudSyncPort.startListening() }
            coVerify(exactly = 1) { cloudSyncPort.syncAllLocalDataToCloud() }
            coVerify(exactly = 0) { adoptCloudVaultUseCase(any()) }
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertNull(state.pendingVaultReconciliation)
        }

    @Test
    fun `unlockVault MismatchRequiresClobber sets pendingReconciliation and skips listener`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            val cloudDek = ByteArray(32) { 0xCC.toByte() }
            val payload = VaultReconciliationOutcome.MismatchRequiresClobber(
                cloudDek = cloudDek,
                cloudSalt = "cloud-salt",
                cloudWrappedDek = "cloud-wrapped",
                cloudVerification = null,
            )
            coEvery { detectVaultReconciliationStateUseCase("password") } returns Result.success(payload)

            viewModel = MainViewModel(
                getAuthStateUseCase, loginWithGoogleUseCase, initializeModelsUseCase, cryptoManager,
                cloudSyncPort, vaultMetadataPort, biometricManager, createVaultUseCase, unlockVaultUseCase,
                detectVaultReconciliationStateUseCase, adoptCloudVaultUseCase,
                bootstrapCloudVaultFromLocalUseCase, loggingPort, errorHandler,
            )
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            verify(exactly = 0) { cloudSyncPort.startListening() }
            coVerify(exactly = 0) { adoptCloudVaultUseCase(any()) }
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertNotNull(state.pendingVaultReconciliation)
            assertFalse(state.pendingVaultReconciliation!!.isReconciling)
        }

    @Test
    fun `confirmAdoptCloud invokes adopter then startListener and clears pending`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            val payload = VaultReconciliationOutcome.MismatchRequiresClobber(
                cloudDek = ByteArray(32) { 0xCC.toByte() },
                cloudSalt = "cloud-salt",
                cloudWrappedDek = "cloud-wrapped",
                cloudVerification = null,
            )
            coEvery { detectVaultReconciliationStateUseCase("password") } returns Result.success(payload)

            viewModel = MainViewModel(
                getAuthStateUseCase, loginWithGoogleUseCase, initializeModelsUseCase, cryptoManager,
                cloudSyncPort, vaultMetadataPort, biometricManager, createVaultUseCase, unlockVaultUseCase,
                detectVaultReconciliationStateUseCase, adoptCloudVaultUseCase,
                bootstrapCloudVaultFromLocalUseCase, loggingPort, errorHandler,
            )
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            viewModel.confirmAdoptCloud()
            advanceUntilIdle()

            coVerify(exactly = 1) { adoptCloudVaultUseCase(payload) }
            verify(exactly = 1) { cloudSyncPort.startListening() }
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertNull(state.pendingVaultReconciliation)
        }

    @Test
    fun `cancelReconciliation clears pending and defers sync`() = runTest(testDispatcher) {
        stubAuthenticatedExistingVaultUnlock()
        val payload = VaultReconciliationOutcome.MismatchRequiresClobber(
            cloudDek = ByteArray(32) { 0xCC.toByte() },
            cloudSalt = "cloud-salt",
            cloudWrappedDek = "cloud-wrapped",
            cloudVerification = null,
        )
        coEvery { detectVaultReconciliationStateUseCase("password") } returns Result.success(payload)

        viewModel = MainViewModel(
            getAuthStateUseCase, loginWithGoogleUseCase, initializeModelsUseCase, cryptoManager,
            cloudSyncPort, vaultMetadataPort, biometricManager, createVaultUseCase, unlockVaultUseCase,
            detectVaultReconciliationStateUseCase, adoptCloudVaultUseCase,
            bootstrapCloudVaultFromLocalUseCase, loggingPort, errorHandler,
        )
        advanceUntilIdle()
        viewModel.unlockVault("password")
        advanceUntilIdle()

        viewModel.cancelReconciliation()
        advanceUntilIdle()

        coVerify(exactly = 0) { adoptCloudVaultUseCase(any()) }
        verify(exactly = 0) { cloudSyncPort.startListening() }
        val state = viewModel.startupState.value as AppStartupState.Ready
        assertNull(state.pendingVaultReconciliation)
        assertTrue(state.cloudSyncDeferred)
    }

    @Test
    fun `unlockVault NoCloudVault bootstraps then starts sync and listener`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            coEvery { detectVaultReconciliationStateUseCase("password") } returns
                Result.success(VaultReconciliationOutcome.NoCloudVault)

            viewModel = MainViewModel(
                getAuthStateUseCase, loginWithGoogleUseCase, initializeModelsUseCase, cryptoManager,
                cloudSyncPort, vaultMetadataPort, biometricManager, createVaultUseCase, unlockVaultUseCase,
                detectVaultReconciliationStateUseCase, adoptCloudVaultUseCase,
                bootstrapCloudVaultFromLocalUseCase, loggingPort, errorHandler,
            )
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            coVerifyOrder {
                bootstrapCloudVaultFromLocalUseCase()
                cloudSyncPort.syncAllLocalDataToCloud()
            }
            verify(exactly = 1) { cloudSyncPort.startListening() }
        }
}
