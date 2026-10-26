package com.browntowndev.enclave.presentation

import com.browntowndev.enclave.core.testing.FakeAuthManager
import com.browntowndev.enclave.core.testing.FakeBiometricPort
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeEntitlementPort
import com.browntowndev.enclave.core.testing.FakeSyncOrchestrator
import com.browntowndev.enclave.core.testing.FakeVaultMetadataPort
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.auth.UserProfile
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.port.repository.SyncOrchestrator
import com.browntowndev.enclave.domain.port.repository.SyncProgress
import com.browntowndev.enclave.domain.port.security.BiometricPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import com.browntowndev.enclave.domain.security.VaultKeystoreAliases
import com.browntowndev.enclave.domain.usecase.FakeSettingsRepository
import com.browntowndev.enclave.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.enclave.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.enclave.domain.usecase.download.InitializeModelsUseCase
import com.browntowndev.enclave.domain.usecase.security.DecryptBiometricDekUseCase
import com.browntowndev.enclave.domain.usecase.security.GetBiometricDecryptCipherUseCase
import com.browntowndev.enclave.domain.usecase.security.GetBiometricEncryptCipherUseCase
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncResult
import com.browntowndev.enclave.domain.usecase.security.StoreBiometricDekUseCase
import com.browntowndev.enclave.domain.usecase.security.UnlockVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.VaultReconciliationOutcome
import com.browntowndev.enclave.domain.usecase.security.VaultSessionInteractor
import com.browntowndev.enclave.testing.FakeAdoptCloudVaultUseCase
import com.browntowndev.enclave.testing.FakeBiometricCipherPort
import com.browntowndev.enclave.testing.FakeBootstrapCloudVaultFromLocalUseCase
import com.browntowndev.enclave.testing.FakeCreateVaultUseCase
import com.browntowndev.enclave.testing.FakeDetectVaultReconciliationStateUseCase
import com.browntowndev.enclave.testing.FakeInitializeModelsUseCase
import com.browntowndev.enclave.testing.FakeLoggingPort
import com.browntowndev.enclave.testing.FakeStartCloudSyncListeningUseCase
import com.browntowndev.enclave.testing.FakeUnlockVaultUseCase
import com.browntowndev.enclave.testing.FakeViewModelErrorHandler
import javax.crypto.Cipher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * ENC-28: collaborators are :core:testing fakes and recording use-case fakes
 * (app test doubles); the auth use cases are the real ones over FakeAuthManager.
 * Robolectric provides a working android.util.Base64, replacing mockkStatic.
 * Assertions read the fakes' recorded state (ENC-27 state-based style).
 */
@ExtendWith(MainDispatcherRule::class, RobolectricExtension::class)
@Config(sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    // ENC-27/28: recorded invocations replace MockK verify/coVerify/coVerifyOrder.
    private val events = mutableListOf<String>()

    private val authManager = FakeAuthManager()
    private val getAuthStateUseCase = GetAuthStateUseCase(authManager)
    private val initializeModelsUseCase = FakeInitializeModelsUseCase(events)
    private val cryptoManager = FakeCryptoManagerPort()
    private val cloudSyncPort = FakeCloudSyncPort()
    private val vaultMetadataPort = FakeVaultMetadataPort()
    private val biometricPort = FakeBiometricPort()
    private val cipherPort = FakeBiometricCipherPort()
    private val createVaultUseCase = FakeCreateVaultUseCase()
    private val unlockVaultUseCase = FakeUnlockVaultUseCase()
    private val detectVaultReconciliationStateUseCase = FakeDetectVaultReconciliationStateUseCase()
    private val adoptCloudVaultUseCase = FakeAdoptCloudVaultUseCase(events)
    private val bootstrapCloudVaultFromLocalUseCase = FakeBootstrapCloudVaultFromLocalUseCase(events)
    private val startCloudSyncListeningUseCase = FakeStartCloudSyncListeningUseCase(events)
    private val loggingPort = FakeLoggingPort()
    private val errorHandler = FakeViewModelErrorHandler()
    private val syncOrchestrator = FakeSyncOrchestrator()
    private val loginWithGoogleUseCase = LoginWithGoogleUseCase(authManager, loggingPort)
    private val initializedModelsResult =
        DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = emptyList(),
            scanResult =
                ModelScanResult(
                    missingModels = emptyList(),
                    partialDownloads = emptyMap(),
                    allValid = true,
                ),
        )

    private val settingsRepository = FakeSettingsRepository()
    private lateinit var viewModel: MainViewModel
    private var workerStartCloudSyncUseCase: StartCloudSyncListeningUseCase = startCloudSyncListeningUseCase

    private fun createViewModel(
        getAuthState: GetAuthStateUseCase = getAuthStateUseCase,
        loginWithGoogle: LoginWithGoogleUseCase = loginWithGoogleUseCase,
        initializeModels: InitializeModelsUseCase = initializeModelsUseCase,
        crypto: CryptoManagerPort = cryptoManager,
        cloudSync: CloudSyncPort = cloudSyncPort,
        vaultMetadata: VaultMetadataPort = vaultMetadataPort,
        biometric: BiometricPort = biometricPort,
        createVault: com.browntowndev.enclave.domain.usecase.security.CreateVaultUseCase = createVaultUseCase,
        unlockVault: UnlockVaultUseCase = unlockVaultUseCase,
        detectReconciliation: com.browntowndev.enclave.domain.usecase.security.DetectVaultReconciliationStateUseCase =
            detectVaultReconciliationStateUseCase,
        adoptCloud: com.browntowndev.enclave.domain.usecase.security.AdoptCloudVaultUseCase = adoptCloudVaultUseCase,
        bootstrapCloud: com.browntowndev.enclave.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase =
            bootstrapCloudVaultFromLocalUseCase,
        logging: LoggingPort = loggingPort,
        errHandler: ViewModelErrorHandler = errorHandler,
        settings: SettingsRepository = settingsRepository,
        syncOrch: SyncOrchestrator = syncOrchestrator,
    ): MainViewModel {
        // ENC-24: the ViewModel depends on VaultSessionInteractor + biometric cipher
        // use cases; tests fake the underlying ports and wrap them here.
        val vaultSession =
            VaultSessionInteractor(
                cryptoManager = crypto,
                cloudSyncPort = cloudSync,
                vaultMetadataPort = vaultMetadata,
                biometricPort = biometric,
                settingsRepository = settings,
                syncOrchestrator = syncOrch,
                loggingPort = logging,
            )
        return MainViewModel(
            getAuthState,
            loginWithGoogle,
            initializeModels,
            vaultSession,
            GetBiometricEncryptCipherUseCase(cipherPort),
            GetBiometricDecryptCipherUseCase(cipherPort),
            StoreBiometricDekUseCase(cipherPort),
            DecryptBiometricDekUseCase(cipherPort),
            createVault,
            unlockVault,
            detectReconciliation,
            adoptCloud,
            bootstrapCloud,
            logging,
            errHandler,
        )
    }

    @BeforeEach
    fun setUp() {
        workerStartCloudSyncUseCase = startCloudSyncListeningUseCase

        cryptoManager.events = events
        vaultMetadataPort.events = events
        cloudSyncPort.events = events

        initializeModelsUseCase.result = initializedModelsResult
        vaultMetadataPort.salt = "c2FsdA=="

        syncOrchestrator.startHandler = { uploadLocalFirst ->
            val result =
                runBlocking {
                    workerStartCloudSyncUseCase(uploadLocalFirst, null)
                }
            // Mirrors InitialSyncWorker: Started and SubscriptionRequired both surface as
            // worker success (the gate stops cleanly instead of retrying); only Failed defers.
            when (result) {
                is StartCloudSyncResult.Started,
                is StartCloudSyncResult.SubscriptionRequired,
                -> Result.success(Unit)
                is StartCloudSyncResult.Failed -> Result.failure(result.cause)
            }
        }

        runBlocking {
            settingsRepository.updateOnboardingCompleted(true)
        }

        viewModel = createViewModel()
    }

    @Test
    fun `unlockVault sets isUnlocking to true then false`() =
        runTest(testDispatcher) {
            vaultMetadataPort.salt = null
            cloudSyncPort.vaultSaltResult = Result.success("c2FsdA==")
            cloudSyncPort.vaultSaltDelayMs = 1000
            cloudSyncPort.wrappedDekResult = Result.success(null)

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
    fun `locked startup exposes biometric unlock when secure value exists`() =
        runTest(testDispatcher) {
            cryptoManager.hasCachedKeyValue = false
            biometricPort.isAvailable = true
            biometricPort.hasSecureValueValue = true

            viewModel =
                createViewModel()
            advanceUntilIdle()

            val state = viewModel.startupState.value
            if (state is AppStartupState.Ready) {
                assertTrue(state.isVaultLocked, "Locked vault should stay locked until biometric unlock")
                assertTrue(state.isBiometricUnlockPossible, "Biometric unlock should be offered when secure value exists")
                assertNull(state.initializationError)
            } else {
                throw AssertionError("State should be Ready")
            }

            assertEquals(0, initializeModelsUseCase.callCount)
        }

    @Test
    fun `finalizeBiometricEnrollment sets isBiometricUnlockPossible when store succeeds`() =
        runTest(testDispatcher) {
            cryptoManager.hasCachedKeyValue = false
            biometricPort.isAvailable = true
            biometricPort.hasSecureValueQueue.addAll(listOf(false, true))

            viewModel =
                createViewModel()
            advanceUntilIdle()

            val before = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(before.isBiometricUnlockPossible, "No secure blob yet at cold start")

            cryptoManager.cachedDekValue = ByteArray(32) { 7 }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            viewModel.finalizeBiometricEnrollment(cipher)
            advanceUntilIdle()

            val after = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(after.isBiometricUnlockPossible)
            assertFalse(after.isVaultLocked)
        }

    @Test
    fun `unlockVault with existing biometric secure value does not request enrollment`() =
        runTest(testDispatcher) {
            cryptoManager.hasCachedKeyValue = false
            biometricPort.isAvailable = true
            biometricPort.hasSecureValueValue = true
            vaultMetadataPort.salt = "c2FsdA=="
            vaultMetadataPort.wrappedDek = "d3JhcA=="

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password", enableBiometric = true)
            advanceUntilIdle()

            val state = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(state.isBiometricEnrollmentPending)
            assertFalse(state.isVaultLocked)
            assertNull(state.vaultErrorMessage)
        }

    @Test
    fun `unlockVault with biometric opt-in runs cloud reconciliation before enrollment`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            biometricPort.isAvailable = true
            biometricPort.hasSecureValueValue = false

            viewModel =
                createViewModel()
            advanceUntilIdle()
            detectVaultReconciliationStateUseCase.invokeHook = {
                events += "detect"
                val ready = viewModel.startupState.value as AppStartupState.Ready
                assertTrue(
                    ready.isVaultLocked,
                    "cloud reconciliation runs before vault gate opens",
                )
                assertFalse(ready.isBiometricEnrollmentPending)
            }
            detectVaultReconciliationStateUseCase.result =
                Result.success(VaultReconciliationOutcome.AlreadyMatching)
            viewModel.unlockVault("password", enableBiometric = true)
            advanceUntilIdle()

            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.isBiometricEnrollmentPending)
            assertTrue(state.isVaultLocked)
            assertEquals(listOf(true), startCloudSyncListeningUseCase.calls)
            assertTrue(events.indexOf("detect") >= 0)
            assertTrue(events.indexOf("detect") < events.indexOf("startSync"))
        }

    @Test
    fun `unlockVault treats blank vault metadata as missing and creates a new vault`() =
        runTest(testDispatcher) {
            val generatedSalt = ByteArray(16) { 0x11.toByte() }
            vaultMetadataPort.salt = ""
            cloudSyncPort.vaultSaltResult = Result.success("")
            vaultMetadataPort.wrappedDek = ""
            cloudSyncPort.wrappedDekResult = Result.success("")
            vaultMetadataPort.verificationData = ""
            cloudSyncPort.verificationResult = Result.success("")
            cryptoManager.generatedSalt = generatedSalt

            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            assertEquals(1, initializeModelsUseCase.callCount)
            assertEquals(1, createVaultUseCase.calls.size)
            assertEquals("password", createVaultUseCase.calls.single().first)
            assertTrue(createVaultUseCase.calls.single().second.contentEquals(generatedSalt))
            assertEquals(0, unlockVaultUseCase.callCount)
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(state.isVaultLocked)
            assertNotNull(state.modelsResult)
            assertNull(state.vaultErrorMessage)
            assertNull(state.initializationError)
        }

    @Test
    fun `finalizeBiometricEnrollment wraps cached DEK not password`() =
        runTest(testDispatcher) {
            val dek = ByteArray(32) { 0x42.toByte() }
            cryptoManager.cachedDekValue = dek
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")

            advanceUntilIdle()
            viewModel.finalizeBiometricEnrollment(cipher = cipher)
            advanceUntilIdle()

            assertTrue(
                cipherPort.storeBytesCalls.any {
                    it.first == "vault_dek" && it.second.contentEquals(dek) && it.third == cipher
                },
            )
            // The cipher port has no value-based store variant to regress to;
            // the byte-based store above is the only enrollment write.
        }

    @Test
    fun `finalizeBiometricEnrollment skips when vault is locked`() =
        runTest(testDispatcher) {
            cryptoManager.cachedDekValue = null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")

            advanceUntilIdle()
            viewModel.finalizeBiometricEnrollment(cipher = cipher)
            advanceUntilIdle()

            assertTrue(cipherPort.storeBytesCalls.isEmpty())
        }

    @Test
    fun `cancelBiometricEnrollment clears pending and unlocks vault`() =
        runTest(testDispatcher) {
            cryptoManager.hasCachedKeyValue = false
            biometricPort.isAvailable = true
            biometricPort.hasSecureValueValue = false

            viewModel = createViewModel()
            advanceUntilIdle()

            stubAuthenticatedExistingVaultUnlock()
            detectVaultReconciliationStateUseCase.result =
                Result.success(VaultReconciliationOutcome.AlreadyMatching)
            viewModel.unlockVault("password", enableBiometric = true)
            advanceUntilIdle()

            val pendingState = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(pendingState.isBiometricEnrollmentPending)
            assertTrue(pendingState.isVaultLocked)

            viewModel.cancelBiometricEnrollment()
            advanceUntilIdle()

            val finalState = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(finalState.isBiometricEnrollmentPending)
            assertFalse(finalState.isVaultLocked)
        }

    @Test
    fun `getBiometricDecryptCipher invalidates stale biometric when cipher is null but has secure value`() =
        runTest(testDispatcher) {
            cryptoManager.hasCachedKeyValue = false
            biometricPort.isAvailable = true
            biometricPort.hasSecureValueValue = true
            cipherPort.decryptCipher = null

            viewModel = createViewModel()
            advanceUntilIdle()

            val before = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(before.isBiometricUnlockPossible)

            val cipher = viewModel.getBiometricDecryptCipher()
            assertNull(cipher)
            advanceUntilIdle()

            assertTrue(biometricPort.clearSecureValueCalls.contains(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY))
            val after = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(after.isBiometricUnlockPossible)
            assertTrue(after.isVaultLocked)
            assertEquals("Biometric key invalidated. Enter your vault password.", after.vaultErrorMessage)
        }

    @Test
    fun `unlockWithBiometricCipher unwraps DEK and caches it`() =
        runTest(testDispatcher) {
            val dek = ByteArray(32) { 0x55.toByte() }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            authManager.setAuthState(AuthState.Authenticated(UserProfile("u", "n", "e", null)))
            initializeModelsUseCase.delayMs = 1000
            cipherPort.decryptResult = dek
            val verification = "dGVzdC12ZXJpZmljYXRpb24="
            cloudSyncPort.verificationResult = Result.success(verification)
            cryptoManager.decryptResult =
                Result.success(LoginWithGoogleUseCase.VAULT_VERIFICATION_MAGIC_STRING)

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockWithBiometricCipher(cipher)

            advanceTimeBy(100)

            val intermediateState = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(intermediateState.isUnlocking, "isUnlocking should be true during biometric unlock")

            advanceTimeBy(1000)
            advanceUntilIdle()

            assertEquals(1, initializeModelsUseCase.callCount)
            assertEquals(1, cryptoManager.cacheDekCalls.size)
            assertTrue(cryptoManager.cacheDekCalls.single().contentEquals(dek))
            assertEquals(listOf(false), startCloudSyncListeningUseCase.calls)
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(state.isUnlocking, "isUnlocking should be false after biometric unlock")
            assertFalse(state.isVaultLocked)
            assertNotNull(state.modelsResult)
            assertNull(state.initializationError)
        }

    @Test
    fun `unlockWithBiometricCipher prefers cloud verification over local`() =
        runTest(testDispatcher) {
            val dek = ByteArray(32) { 0x55.toByte() }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val cloudVerification = "Y2xvdWQtdmVyaWZpY2F0aW9u"
            authManager.setAuthState(AuthState.Authenticated(UserProfile("u", "n", "e", null)))
            cipherPort.decryptResult = dek
            cloudSyncPort.verificationResult = Result.success(cloudVerification)
            vaultMetadataPort.verificationData = "bG9jYWwtdmVyaWZpY2F0aW9u"
            cryptoManager.decryptResult =
                Result.success(LoginWithGoogleUseCase.VAULT_VERIFICATION_MAGIC_STRING)
            val ciphertextBytes = byteArrayOf(1, 2, 3)
            cryptoManager.decodeBase64Override = { ciphertextBytes }

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockWithBiometricCipher(cipher)
            advanceUntilIdle()

            assertEquals(1, cloudSyncPort.getVerificationDataCallCount)
            assertEquals(0, vaultMetadataPort.getVerificationDataCallCount)
            assertEquals(1, cryptoManager.decryptCalls.size)
            assertTrue(cryptoManager.decryptCalls.single().first.contentEquals(ciphertextBytes))
            assertNull(cryptoManager.decryptCalls.single().second)
            assertEquals(listOf(false), startCloudSyncListeningUseCase.calls)
        }

    @Test
    fun `unlockWithBiometricCipher invalidates stale biometric when verification fails`() =
        runTest(testDispatcher) {
            val dek = ByteArray(32) { 0x55.toByte() }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val cloudVerification = "Y2xvdWQtdmVyaWZpY2F0aW9u"
            authManager.setAuthState(AuthState.Authenticated(UserProfile("u", "n", "e", null)))
            cipherPort.decryptResult = dek
            cloudSyncPort.verificationResult = Result.success(cloudVerification)
            cryptoManager.decryptResult = Result.failure(IllegalStateException("stale dek"))

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockWithBiometricCipher(cipher)
            advanceUntilIdle()

            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.isVaultLocked)
            assertFalse(state.isBiometricUnlockPossible)
            assertTrue(state.vaultErrorMessage?.contains("password", ignoreCase = true) == true)
            assertTrue(cryptoManager.clearKeyCallCount >= 1)
            assertTrue(biometricPort.clearSecureValueCalls.contains("vault_dek"))
            assertEquals(0, cloudSyncPort.count("startListening"))
        }

    @Test
    fun `unlockWithBiometricCipher rejects unlock when verification anchor is missing`() =
        runTest(testDispatcher) {
            val dek = ByteArray(32) { 0x55.toByte() }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            authManager.setAuthState(AuthState.Authenticated(UserProfile("u", "n", "e", null)))
            cipherPort.decryptResult = dek
            cloudSyncPort.verificationResult = Result.success(null)
            vaultMetadataPort.verificationData = null
            cryptoManager.decryptResult =
                Result.success(LoginWithGoogleUseCase.VAULT_VERIFICATION_MAGIC_STRING)

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockWithBiometricCipher(cipher)
            advanceUntilIdle()

            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.isVaultLocked)
            assertFalse(state.isBiometricUnlockPossible)
            assertEquals(1, cloudSyncPort.getVerificationDataCallCount)
            assertEquals(1, vaultMetadataPort.getVerificationDataCallCount)
            assertTrue(cryptoManager.decryptCalls.isEmpty())
            assertTrue(cryptoManager.clearKeyCallCount >= 1)
            assertTrue(biometricPort.clearSecureValueCalls.contains("vault_dek"))
            assertEquals(0, cloudSyncPort.count("startListening"))
        }

    @Test
    fun `unlockWithBiometricCipher skips listener when vault reconciliation is pending`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            val payload =
                VaultReconciliationOutcome.MismatchRequiresClobber(
                    cloudDek = ByteArray(32) { 0xCC.toByte() },
                    cloudSalt = "cloud-salt",
                    cloudWrappedDek = "cloud-wrapped",
                    cloudVerification = null,
                )
            detectVaultReconciliationStateUseCase.result = Result.success(payload)

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            val pendingState = viewModel.startupState.value as AppStartupState.Ready
            assertNotNull(pendingState.pendingVaultReconciliation)

            val dek = ByteArray(32) { 0x55.toByte() }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipherPort.decryptResult = dek
            val verification = "dGVzdC12ZXJpZmljYXRpb24="
            cloudSyncPort.verificationResult = Result.success(verification)
            cryptoManager.decryptResult =
                Result.success(LoginWithGoogleUseCase.VAULT_VERIFICATION_MAGIC_STRING)

            val startListeningBaseline = cloudSyncPort.count("startListening")

            viewModel.unlockWithBiometricCipher(cipher)
            advanceUntilIdle()

            assertEquals(startListeningBaseline, cloudSyncPort.count("startListening"))
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(state.isVaultLocked)
            assertNotNull(state.pendingVaultReconciliation)
        }

    @Test
    fun `unlockWithBiometricCipher surfaces error when unwrap fails`() =
        runTest(testDispatcher) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipherPort.decryptResult = null

            advanceUntilIdle()
            viewModel.unlockWithBiometricCipher(cipher)
            advanceUntilIdle()

            assertTrue(cryptoManager.cacheDekCalls.isEmpty())
            assertEquals(0, cloudSyncPort.count("syncAll"))
            assertEquals(0, cloudSyncPort.count("startListening"))
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.isVaultLocked)
            assertFalse(state.isBiometricUnlockPossible)
            assertTrue(state.vaultErrorMessage?.contains("password", ignoreCase = true) == true)
            assertTrue(cryptoManager.clearKeyCallCount >= 1)
            assertTrue(biometricPort.clearSecureValueCalls.contains("vault_dek"))
        }

    @Test
    fun `unlockWithBiometricCipher surfaces error when cache DEK fails`() =
        runTest(testDispatcher) {
            biometricPort.isAvailable = true
            biometricPort.hasSecureValueValue = true
            viewModel =
                createViewModel()
            advanceUntilIdle()

            val dek = ByteArray(32) { 0x55.toByte() }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipherPort.decryptResult = dek
            cryptoManager.cacheDekResult = Result.failure(IllegalStateException("cache failed"))

            val before = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(before.isBiometricUnlockPossible)

            viewModel.unlockWithBiometricCipher(cipher)
            advanceUntilIdle()

            assertEquals(1, cryptoManager.cacheDekCalls.size)
            assertTrue(cryptoManager.cacheDekCalls.single().contentEquals(dek))
            assertEquals(0, cloudSyncPort.count("syncAll"))
            assertEquals(0, cloudSyncPort.count("startListening"))
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.isVaultLocked)
            assertFalse(state.isBiometricUnlockPossible)
            assertTrue(state.vaultErrorMessage?.contains("password", ignoreCase = true) == true)
            assertTrue(cryptoManager.clearKeyCallCount >= 1)
            assertTrue(biometricPort.clearSecureValueCalls.contains("vault_dek"))
        }

    @Test
    fun `loginWithGoogle clears cached dek defers cloud sync and keeps vault locked`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            authManager.signInResult =
                Result.success(UserProfile("uid", "Name", "email@test.com", null))

            viewModel.loginWithGoogle()
            advanceUntilIdle()

            assertTrue(cryptoManager.clearKeyCallCount >= 1)
            assertTrue(cloudSyncPort.count("stopListening") >= 1)
            assertEquals(0, cloudSyncPort.count("startListening"))
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.isVaultLocked)
            assertTrue(state.cloudSyncDeferred)
            assertFalse(state.isBiometricUnlockPossible)
        }

    @Test
    fun `loginWithGoogle preserves unlocked vault when sign-in completes after local unlock`() =
        runTest(testDispatcher) {
            cryptoManager.hasCachedKeyValue = true
            authManager.signInResult =
                Result.success(UserProfile("uid", "Name", "email@test.com", null))
            syncOrchestrator.syncProgressFlow = flowOf(null)
            authManager.setAuthState(
                AuthState.Authenticated(UserProfile("uid", "Name", "email@test.com", null)),
            )

            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.loginWithGoogle()
            advanceUntilIdle()

            assertEquals(0, cryptoManager.clearKeyCallCount)
            assertEquals(0, cloudSyncPort.count("stopListening"))
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(state.isVaultLocked)
            assertFalse(state.cloudSyncDeferred)
        }

    @Test
    fun `unlockVault opens app only after model initialization completes`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            detectVaultReconciliationStateUseCase.result =
                Result.success(VaultReconciliationOutcome.AlreadyMatching)
            syncOrchestrator.startHandler = { Result.success(Unit) }
            initializeModelsUseCase.delayMs = 5_000

            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceTimeBy(100)

            val midUnlock = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(midUnlock.isVaultLocked, "vault gate should stay closed until model scan finishes")
            assertTrue(midUnlock.isUnlocking, "unlock spinner should remain until model scan finishes")

            advanceUntilIdle()
            val finalState = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(finalState.isVaultLocked)
            assertNotNull(finalState.modelsResult)
        }

    @Test
    fun `SCENARIO-16 unlock sync progress is wired and cleared on completion`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            detectVaultReconciliationStateUseCase.result =
                Result.success(VaultReconciliationOutcome.AlreadyMatching)

            syncOrchestrator.syncProgressFlow =
                flowOf(
                    SyncProgress(0.5f, "Syncing updates (100/200)..."),
                    null,
                )
            syncOrchestrator.startHandler = { Result.success(Unit) }

            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            val state = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(state.isVaultLocked)
            assertNull(state.unlockSyncProgress, "unlockSyncProgress must be null after sync completion")
            assertNull(state.unlockSyncStatus, "unlockSyncStatus must be null after sync completion")
        }

    /**
     * Stubs the unlock-existing-vault happy path so a unit test can drive the post-unlock
     * reconciliation branch. Authenticates the user so reconcileWithCloudIfNeeded runs.
     */
    private fun stubAuthenticatedExistingVaultUnlock() {
        authManager.setAuthState(AuthState.Authenticated(UserProfile("u", "n", "e", null)))
        vaultMetadataPort.salt = "c2FsdA=="
        vaultMetadataPort.wrappedDek = "d3JhcA=="
        vaultMetadataPort.verificationData = null
    }

    @Test
    fun `SCN-ENTRY-001 MainViewModel matching vault path starts ordered sync`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            detectVaultReconciliationStateUseCase.result =
                Result.success(VaultReconciliationOutcome.AlreadyMatching)

            // Drive the entry path through a REAL use case so the test fails when the use case
            // does not actually replay before starting the listener.
            val realStartUseCase = StartCloudSyncListeningUseCase(cloudSyncPort, FakeEntitlementPort(EntitlementState.Entitled()))

            // Recreate VM so init picks up the new auth state and the real use case
            workerStartCloudSyncUseCase = realStartUseCase
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            // The entry path must drive upload, then replay, then live listener — in that order.
            assertEquals(
                listOf("syncAll", "syncRemote", "startListening"),
                events.filter { it in listOf("syncAll", "syncRemote", "startListening") },
            )
            assertTrue(adoptCloudVaultUseCase.calls.isEmpty())
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertNull(state.pendingVaultReconciliation)
        }

    @Test
    fun `cloud sync start failure leaves startup sync deferred`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            detectVaultReconciliationStateUseCase.result =
                Result.success(VaultReconciliationOutcome.AlreadyMatching)
            startCloudSyncListeningUseCase.result =
                StartCloudSyncResult.Failed(IllegalStateException("replay failed"))

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.cloudSyncDeferred)
            assertEquals("replay failed", state.vaultErrorMessage)
        }

    @Test
    fun `unlockVault MismatchRequiresClobber sets pendingReconciliation and skips listener`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            val cloudDek = ByteArray(32) { 0xCC.toByte() }
            val payload =
                VaultReconciliationOutcome.MismatchRequiresClobber(
                    cloudDek = cloudDek,
                    cloudSalt = "cloud-salt",
                    cloudWrappedDek = "cloud-wrapped",
                    cloudVerification = null,
                )
            detectVaultReconciliationStateUseCase.result = Result.success(payload)

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            assertEquals(0, cloudSyncPort.count("startListening"))
            assertTrue(adoptCloudVaultUseCase.calls.isEmpty())
            assertEquals(0, initializeModelsUseCase.callCount)
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertNotNull(state.pendingVaultReconciliation)
            assertFalse(state.pendingVaultReconciliation!!.isReconciling)
        }

    @Test
    fun `SCN-ENTRY-002 MainViewModel cloud adoption starts replay without upload`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            val payload =
                VaultReconciliationOutcome.MismatchRequiresClobber(
                    cloudDek = ByteArray(32) { 0xCC.toByte() },
                    cloudSalt = "cloud-salt",
                    cloudWrappedDek = "cloud-wrapped",
                    cloudVerification = null,
                )
            detectVaultReconciliationStateUseCase.result = Result.success(payload)

            val realStartUseCase = StartCloudSyncListeningUseCase(cloudSyncPort, FakeEntitlementPort(EntitlementState.Entitled()))

            workerStartCloudSyncUseCase = realStartUseCase
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            viewModel.confirmAdoptCloud()
            advanceUntilIdle()

            assertEquals(listOf(payload), adoptCloudVaultUseCase.calls)
            // Adoption must skip upload, then drive replay -> listen in that order.
            assertEquals(
                listOf("syncRemote", "startListening"),
                events.filter { it in listOf("syncRemote", "startListening") },
            )
            assertEquals(0, cloudSyncPort.count("syncAll"))
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertNull(state.pendingVaultReconciliation)
        }

    @Test
    fun `cancelReconciliation clears pending and defers sync`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            val payload =
                VaultReconciliationOutcome.MismatchRequiresClobber(
                    cloudDek = ByteArray(32) { 0xCC.toByte() },
                    cloudSalt = "cloud-salt",
                    cloudWrappedDek = "cloud-wrapped",
                    cloudVerification = null,
                )
            detectVaultReconciliationStateUseCase.result = Result.success(payload)

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            viewModel.cancelReconciliation()
            advanceUntilIdle()

            assertTrue(adoptCloudVaultUseCase.calls.isEmpty())
            assertEquals(0, cloudSyncPort.count("startListening"))
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertNull(state.pendingVaultReconciliation)
            assertTrue(state.cloudSyncDeferred)
        }

    @Test
    fun `unlockVault NoCloudVault bootstraps then starts sync and listener`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            detectVaultReconciliationStateUseCase.result =
                Result.success(VaultReconciliationOutcome.NoCloudVault)

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            assertEquals(listOf(true), startCloudSyncListeningUseCase.calls)
            assertTrue(events.indexOf("bootstrap") >= 0)
            assertTrue(events.indexOf("bootstrap") < events.indexOf("startSync"))
        }
}
