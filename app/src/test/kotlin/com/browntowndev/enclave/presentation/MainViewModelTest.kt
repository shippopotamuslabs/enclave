package com.browntowndev.enclave.presentation

import com.browntowndev.enclave.core.data.security.AndroidBiometricManager
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.port.auth.UserProfile
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.port.repository.SyncOrchestrator
import com.browntowndev.enclave.domain.port.repository.SyncProgress
import com.browntowndev.enclave.domain.port.security.BiometricCipherPort
import com.browntowndev.enclave.domain.port.security.BiometricPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import com.browntowndev.enclave.domain.security.VaultKeystoreAliases
import com.browntowndev.enclave.domain.usecase.FakeSettingsRepository
import com.browntowndev.enclave.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.enclave.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.enclave.domain.usecase.download.InitializeModelsUseCase
import com.browntowndev.enclave.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.enclave.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.DecryptBiometricDekUseCase
import com.browntowndev.enclave.domain.usecase.security.GetBiometricDecryptCipherUseCase
import com.browntowndev.enclave.domain.usecase.security.GetBiometricEncryptCipherUseCase
import com.browntowndev.enclave.domain.usecase.security.StoreBiometricDekUseCase
import com.browntowndev.enclave.domain.usecase.security.VaultSessionInteractor
import com.browntowndev.enclave.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import com.browntowndev.enclave.domain.usecase.security.UnlockVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.VaultReconciliationOutcome
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.Base64
import javax.crypto.Cipher

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
    private val biometricPort = mockk<BiometricPort>(relaxUnitFun = true)
    private val biometricManager = mockk<AndroidBiometricManager>(relaxUnitFun = true)
    private val createVaultUseCase = mockk<CreateVaultUseCase>()
    private val unlockVaultUseCase = mockk<UnlockVaultUseCase>()
    private val detectVaultReconciliationStateUseCase = mockk<DetectVaultReconciliationStateUseCase>()
    private val adoptCloudVaultUseCase = mockk<AdoptCloudVaultUseCase>()
    private val bootstrapCloudVaultFromLocalUseCase = mockk<BootstrapCloudVaultFromLocalUseCase>()
    private val startCloudSyncListeningUseCase = mockk<StartCloudSyncListeningUseCase>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()
    private val syncOrchestrator = mockk<SyncOrchestrator>(relaxed = true)
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

    // ENC-27: recorded invocations replace MockK verify/coVerify/coVerifyOrder.
    private val events = mutableListOf<String>()
    private var initModelsCallCount = 0
    private var clearKeyCallCount = 0
    private var vaultMetaVerifCallCount = 0
    private var cloudVerifCallCount = 0
    private var startListeningCallCount = 0
    private var stopListeningCallCount = 0
    private var syncAllCallCount = 0
    private var syncRemoteCallCount = 0
    private var unlockVaultCallCount = 0
    private var storeValueCallCount = 0
    private val adoptCalls = mutableListOf<VaultReconciliationOutcome.MismatchRequiresClobber>()
    private val startSyncCalls = mutableListOf<Boolean>()
    private val createVaultCalls = mutableListOf<Pair<String, ByteArray>>()
    private val cacheDekCalls = mutableListOf<ByteArray>()
    private val decryptCalls = mutableListOf<Pair<ByteArray, ByteArray?>>()
    private val clearSecureValueCalls = mutableListOf<String>()
    private val storeBytesCalls = mutableListOf<Triple<String, ByteArray, Cipher>>()

    private fun createViewModel(
        getAuthState: GetAuthStateUseCase = getAuthStateUseCase,
        loginWithGoogle: LoginWithGoogleUseCase = loginWithGoogleUseCase,
        initializeModels: InitializeModelsUseCase = initializeModelsUseCase,
        crypto: CryptoManagerPort = cryptoManager,
        cloudSync: CloudSyncPort = cloudSyncPort,
        vaultMetadata: VaultMetadataPort = vaultMetadataPort,
        biometric: BiometricPort = biometricPort,
        bioManager: AndroidBiometricManager = biometricManager,
        createVault: CreateVaultUseCase = createVaultUseCase,
        unlockVault: UnlockVaultUseCase = unlockVaultUseCase,
        detectReconciliation: DetectVaultReconciliationStateUseCase = detectVaultReconciliationStateUseCase,
        adoptCloud: AdoptCloudVaultUseCase = adoptCloudVaultUseCase,
        bootstrapCloud: BootstrapCloudVaultFromLocalUseCase = bootstrapCloudVaultFromLocalUseCase,
        logging: LoggingPort = loggingPort,
        errHandler: ViewModelErrorHandler = errorHandler,
        settings: SettingsRepository = settingsRepository,
        syncOrch: SyncOrchestrator = syncOrchestrator,
    ): MainViewModel {
        // ENC-24: the ViewModel now depends on VaultSessionInteractor + biometric cipher
        // use cases; tests keep mocking the underlying ports and wrap them here.
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
        val cipherPort =
            object : BiometricCipherPort {
                override fun getEncryptCipher(key: String): Cipher? = bioManager.getEncryptCipher(key)

                override fun getDecryptCipher(key: String): Cipher? = bioManager.getDecryptCipher(key)

                override suspend fun storeSecureBytesWithCipher(
                    key: String,
                    value: ByteArray,
                    cipher: Cipher,
                ): Result<Unit> = bioManager.storeSecureBytesWithCipher(key, value, cipher)

                override fun decryptBytesWithCipher(
                    key: String,
                    cipher: Cipher,
                ): ByteArray? = bioManager.decryptBytesWithCipher(key, cipher)
            }
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
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            Base64.getEncoder().encodeToString(it.invocation.args[0] as ByteArray)
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            Base64.getDecoder().decode(it.invocation.args[0] as String)
        }

        coEvery { initializeModelsUseCase() } coAnswers {
            initModelsCallCount++
            events += "initModels"
            initializedModelsResult
        }
        every { cryptoManager.encodeBase64(any()) } answers {
            Base64.getEncoder().encodeToString(firstArg())
        }
        every { cryptoManager.decodeBase64(any()) } answers {
            Base64.getDecoder().decode(firstArg<String>())
        }
        every { cryptoManager.hasCachedKey() } returns false
        every { cryptoManager.clearKey() } answers {
            clearKeyCallCount++
            events += "clearKey"
            Unit
        }
        every { biometricPort.isBiometricAvailable() } returns false
        every { biometricPort.hasSecureValue(any()) } returns false
        every { biometricManager.getEncryptCipher(any()) } returns null
        every { biometricManager.getDecryptCipher(any()) } returns null
        every { getAuthStateUseCase() } returns
            MutableStateFlow(
                com.browntowndev.enclave.domain.port.auth.AuthState.Unauthenticated,
            )
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
            kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }
        coEvery { vaultMetadataPort.getVaultSalt() } returns "c2FsdA=="
        coEvery { vaultMetadataPort.saveVaultSalt(any()) } returns Unit
        coEvery { vaultMetadataPort.getVaultVerificationData() } coAnswers {
            vaultMetaVerifCallCount++
            events += "vaultMetaVerif"
            null
        }
        coEvery { vaultMetadataPort.saveVaultVerificationData(any()) } returns Unit
        coEvery { vaultMetadataPort.getWrappedDek() } returns null
        coEvery { vaultMetadataPort.saveWrappedDek(any()) } returns Unit

        coEvery { detectVaultReconciliationStateUseCase(any()) } returns
            Result.success(VaultReconciliationOutcome.NoCloudVault)
        coEvery { adoptCloudVaultUseCase(any()) } coAnswers {
            adoptCalls += firstArg<VaultReconciliationOutcome.MismatchRequiresClobber>()
            events += "adopt"
            Result.success(Unit)
        }
        coEvery { bootstrapCloudVaultFromLocalUseCase() } coAnswers {
            events += "bootstrap"
            Result.success(Unit)
        }
        coEvery { cloudSyncPort.syncAllLocalDataToCloud() } coAnswers {
            syncAllCallCount++
            events += "syncAll"
            Result.success(Unit)
        }
        every { cloudSyncPort.startListening() } answers {
            startListeningCallCount++
            events += "startListening"
            Unit
        }
        every { cloudSyncPort.stopListening() } answers {
            stopListeningCallCount++
            events += "stopListening"
            Unit
        }
        every { biometricPort.clearSecureValue(any()) } answers {
            clearSecureValueCalls += firstArg<String>()
            Unit
        }
        coEvery { startCloudSyncListeningUseCase(any(), any()) } coAnswers {
            startSyncCalls += firstArg<Boolean>()
            events += "startSync"
            Result.success(Unit)
        }
        coEvery { cloudSyncPort.bootstrapFeatureSettings() } returns Result.success(Unit)

        coEvery { syncOrchestrator.startInitialSync(any()) } answers {
            val uploadLocalFirst = firstArg<Boolean>()
            val result =
                runBlocking {
                    workerStartCloudSyncUseCase(uploadLocalFirst, null)
                }
            if (result.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("replay failed"))
            }
        }
        every { syncOrchestrator.syncProgress } returns MutableStateFlow<SyncProgress?>(null)

        runBlocking {
            settingsRepository.updateOnboardingCompleted(true)
        }

        viewModel = createViewModel()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `unlockVault sets isUnlocking to true then false`() =
        runTest(testDispatcher) {
            coEvery { vaultMetadataPort.getVaultSalt() } returns null
            coEvery { cloudSyncPort.getVaultSalt() } coAnswers {
                delay(1000)
                Result.success("c2FsdA==")
            }
            coEvery { cloudSyncPort.getWrappedDek() } returns Result.success(null)
            coEvery { cloudSyncPort.getVaultVerificationData() } coAnswers {
            cloudVerifCallCount++
            Result.success(null)
        }
            coEvery { cloudSyncPort.updateVaultSalt(any()) } returns Result.success(Unit)
            coEvery { cloudSyncPort.uploadVaultMetadata(any()) } returns Result.success(Unit)
            coEvery { createVaultUseCase(any(), any()) } returns Result.success(byteArrayOf(1, 2, 3))
            every { cryptoManager.encrypt(any()) } returns Result.success(byteArrayOf())
            coEvery { cloudSyncPort.updateVaultVerificationData(any()) } returns Result.success(Unit)
            every { biometricPort.isBiometricAvailable() } returns false

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
            every { cryptoManager.hasCachedKey() } returns false
            every { biometricPort.isBiometricAvailable() } returns true
            every { biometricPort.hasSecureValue(any()) } returns true

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

            assertEquals(0, initModelsCallCount)
        }

    @Test
    fun `finalizeBiometricEnrollment sets isBiometricUnlockPossible when store succeeds`() =
        runTest(testDispatcher) {
            every { cryptoManager.hasCachedKey() } returns false
            every { biometricPort.isBiometricAvailable() } returns true
            every { biometricPort.hasSecureValue(any()) } returnsMany listOf(false, true)

            viewModel =
                createViewModel()
            advanceUntilIdle()

            val before = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(before.isBiometricUnlockPossible, "No secure blob yet at cold start")

            every { cryptoManager.getCachedDek() } returns ByteArray(32) { 7 }
            coEvery { biometricManager.storeSecureBytesWithCipher(any(), any(), any()) } returns Result.success(Unit)
            val cipher = mockk<Cipher>(relaxed = true)
            viewModel.finalizeBiometricEnrollment(cipher)
            advanceUntilIdle()

            val after = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(after.isBiometricUnlockPossible)
            assertFalse(after.isVaultLocked)
        }

    @Test
    fun `unlockVault with existing biometric secure value does not request enrollment`() =
        runTest(testDispatcher) {
            every { cryptoManager.hasCachedKey() } returns false
            every { biometricPort.isBiometricAvailable() } returns true
            every { biometricPort.hasSecureValue(any()) } returns true
            coEvery { vaultMetadataPort.getVaultSalt() } returns "c2FsdA=="
            coEvery { vaultMetadataPort.getWrappedDek() } returns "d3JhcA=="
            coEvery { vaultMetadataPort.getVaultVerificationData() } coAnswers {
            vaultMetaVerifCallCount++
            events += "vaultMetaVerif"
            null
        }
            coEvery { cloudSyncPort.getVaultVerificationData() } coAnswers {
            cloudVerifCallCount++
            Result.success(null)
        }
            coEvery { cloudSyncPort.updateVaultVerificationData(any()) } returns Result.success(Unit)
            coEvery { unlockVaultUseCase(any(), any(), any()) } returns Result.success(Unit)
            every { cryptoManager.encrypt(any()) } returns Result.success(byteArrayOf())

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
            every { biometricPort.isBiometricAvailable() } returns true
            every { biometricPort.hasSecureValue(any()) } returns false

            viewModel =
                createViewModel()
            advanceUntilIdle()
            coEvery { detectVaultReconciliationStateUseCase("password") } coAnswers {
                events += "detect"
                val ready = viewModel.startupState.value as AppStartupState.Ready
                assertTrue(
                    ready.isVaultLocked,
                    "cloud reconciliation runs before vault gate opens",
                )
                assertFalse(ready.isBiometricEnrollmentPending)
                Result.success(VaultReconciliationOutcome.AlreadyMatching)
            }
            viewModel.unlockVault("password", enableBiometric = true)
            advanceUntilIdle()

            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.isBiometricEnrollmentPending)
            assertTrue(state.isVaultLocked)
            assertEquals(listOf(true), startSyncCalls)
            assertTrue(events.indexOf("detect") >= 0)
            assertTrue(events.indexOf("detect") < events.indexOf("startSync"))
        }

    @Test
    fun `unlockVault treats blank vault metadata as missing and creates a new vault`() =
        runTest(testDispatcher) {
            val generatedSalt = ByteArray(16) { 0x11.toByte() }
            coEvery { vaultMetadataPort.getVaultSalt() } returns ""
            coEvery { cloudSyncPort.getVaultSalt() } returns Result.success("")
            coEvery { vaultMetadataPort.getWrappedDek() } returns ""
            coEvery { cloudSyncPort.getWrappedDek() } returns Result.success("")
            coEvery { vaultMetadataPort.getVaultVerificationData() } returns ""
            coEvery { cloudSyncPort.getVaultVerificationData() } returns Result.success("")
            every { cryptoManager.generateSalt() } returns generatedSalt
            coEvery { createVaultUseCase("password", generatedSalt) } coAnswers {
                createVaultCalls += firstArg<String>() to secondArg<ByteArray>()
                Result.success(byteArrayOf(1, 2, 3))
            }
            coEvery { unlockVaultUseCase(any(), any(), any()) } coAnswers {
                unlockVaultCallCount++
                Result.success(Unit)
            }
            every { cryptoManager.encrypt(any()) } returns Result.success(byteArrayOf())
            coEvery { cloudSyncPort.updateVaultSalt(any()) } returns Result.success(Unit)
            coEvery { cloudSyncPort.uploadVaultMetadata(any()) } returns Result.success(Unit)
            coEvery { cloudSyncPort.updateVaultVerificationData(any()) } returns Result.success(Unit)

            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            assertEquals(1, initModelsCallCount)
            assertEquals(1, createVaultCalls.size)
            assertEquals("password", createVaultCalls.single().first)
            assertTrue(createVaultCalls.single().second.contentEquals(generatedSalt))
            assertEquals(0, unlockVaultCallCount)
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
            every { cryptoManager.getCachedDek() } returns dek
            coEvery { biometricManager.storeSecureBytesWithCipher("vault_dek", dek, any()) } coAnswers {
                storeBytesCalls += Triple(firstArg<String>(), secondArg<ByteArray>(), thirdArg<Cipher>())
                Result.success(Unit)
            }
            coEvery { biometricManager.storeSecureValueWithCipher(any(), any(), any()) } coAnswers {
                storeValueCallCount++
                Result.success(Unit)
            }
            val cipher = mockk<javax.crypto.Cipher>(relaxed = true)

            advanceUntilIdle()
            viewModel.finalizeBiometricEnrollment(cipher = cipher)
            advanceUntilIdle()

            assertTrue(
                storeBytesCalls.any {
                    it.first == "vault_dek" && it.second.contentEquals(dek) && it.third == cipher
                },
            )
            assertEquals(0, storeValueCallCount)
        }

    @Test
    fun `finalizeBiometricEnrollment skips when vault is locked`() =
        runTest(testDispatcher) {
            every { cryptoManager.getCachedDek() } returns null
            coEvery { biometricManager.storeSecureBytesWithCipher(any(), any(), any()) } coAnswers {
                storeBytesCalls += Triple(firstArg<String>(), secondArg<ByteArray>(), thirdArg<Cipher>())
                Result.success(Unit)
            }
            val cipher = mockk<javax.crypto.Cipher>(relaxed = true)

            advanceUntilIdle()
            viewModel.finalizeBiometricEnrollment(cipher = cipher)
            advanceUntilIdle()

            assertTrue(storeBytesCalls.isEmpty())
        }

    @Test
    fun `cancelBiometricEnrollment clears pending and unlocks vault`() =
        runTest(testDispatcher) {
            every { cryptoManager.hasCachedKey() } returns false
            every { biometricPort.isBiometricAvailable() } returns true
            every { biometricPort.hasSecureValue(any()) } returns false

            viewModel = createViewModel()
            advanceUntilIdle()

            stubAuthenticatedExistingVaultUnlock()
            coEvery { detectVaultReconciliationStateUseCase("password") } returns Result.success(VaultReconciliationOutcome.AlreadyMatching)
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
            every { cryptoManager.hasCachedKey() } returns false
            every { biometricPort.isBiometricAvailable() } returns true
            every { biometricPort.hasSecureValue(any()) } returns true
            every { biometricManager.getDecryptCipher(any()) } returns null

            viewModel = createViewModel()
            advanceUntilIdle()

            val before = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(before.isBiometricUnlockPossible)

            val cipher = viewModel.getBiometricDecryptCipher()
            assertNull(cipher)
            advanceUntilIdle()

            assertTrue(clearSecureValueCalls.contains(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY))
            val after = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(after.isBiometricUnlockPossible)
            assertTrue(after.isVaultLocked)
            assertEquals("Biometric key invalidated. Enter your vault password.", after.vaultErrorMessage)
        }

    @Test
    fun `unlockWithBiometricCipher unwraps DEK and caches it`() =
        runTest(testDispatcher) {
            val dek = ByteArray(32) { 0x55.toByte() }
            val cipher = mockk<javax.crypto.Cipher>(relaxed = true)
            every {
                getAuthStateUseCase()
            } returns
                MutableStateFlow(
                    com.browntowndev.enclave.domain.port.auth.AuthState.Authenticated(
                        com.browntowndev.enclave.domain.port.auth
                            .UserProfile("u", "n", "e", null),
                    ),
                )
            coEvery { initializeModelsUseCase() } coAnswers {
                initModelsCallCount++
                events += "initModels"
                delay(1000)
                initializedModelsResult
            }
            every { biometricManager.decryptBytesWithCipher("vault_dek", cipher) } returns dek
            every { cryptoManager.cacheDek(dek) } answers {
                cacheDekCalls += firstArg<ByteArray>()
                Result.success(Unit)
            }
            val verification = "dGVzdC12ZXJpZmljYXRpb24="
            coEvery { cloudSyncPort.getVaultVerificationData() } returns Result.success(verification)
            coEvery { vaultMetadataPort.getVaultVerificationData() } coAnswers {
            vaultMetaVerifCallCount++
            events += "vaultMetaVerif"
            null
        }
            every { cryptoManager.decrypt(any<ByteArray>(), any()) } answers {
                decryptCalls += firstArg<ByteArray>() to secondArg<ByteArray?>()
                Result.success(LoginWithGoogleUseCase.VAULT_VERIFICATION_MAGIC_STRING)
            }

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockWithBiometricCipher(cipher)

            advanceTimeBy(100)

            val intermediateState = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(intermediateState.isUnlocking, "isUnlocking should be true during biometric unlock")

            advanceTimeBy(1000)
            advanceUntilIdle()

            assertEquals(1, initModelsCallCount)
            assertEquals(1, cacheDekCalls.size)
            assertTrue(cacheDekCalls.single().contentEquals(dek))
            assertEquals(listOf(false), startSyncCalls)
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
            val cipher = mockk<javax.crypto.Cipher>(relaxed = true)
            val cloudVerification = "Y2xvdWQtdmVyaWZpY2F0aW9u"
            every {
                getAuthStateUseCase()
            } returns
                MutableStateFlow(
                    com.browntowndev.enclave.domain.port.auth.AuthState.Authenticated(
                        com.browntowndev.enclave.domain.port.auth
                            .UserProfile("u", "n", "e", null),
                    ),
                )
            every { biometricManager.decryptBytesWithCipher("vault_dek", cipher) } returns dek
            every { cryptoManager.cacheDek(dek) } answers {
                cacheDekCalls += firstArg<ByteArray>()
                Result.success(Unit)
            }
            coEvery { cloudSyncPort.getVaultVerificationData() } coAnswers {
            cloudVerifCallCount++
            Result.success(cloudVerification)
        }
            coEvery { vaultMetadataPort.getVaultVerificationData() } coAnswers {
            vaultMetaVerifCallCount++
            events += "vaultMetaVerif"
            "bG9jYWwtdmVyaWZpY2F0aW9u"
        }
            coEvery { vaultMetadataPort.saveVaultVerificationData(cloudVerification) } returns Unit
            val ciphertextBytes = byteArrayOf(1, 2, 3)
            every { cryptoManager.decrypt(any<ByteArray>(), any()) } answers {
                decryptCalls += firstArg<ByteArray>() to secondArg<ByteArray?>()
                Result.success(LoginWithGoogleUseCase.VAULT_VERIFICATION_MAGIC_STRING)
            }
            every { cryptoManager.decodeBase64(cloudVerification) } returns ciphertextBytes

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockWithBiometricCipher(cipher)
            advanceUntilIdle()

            assertEquals(1, cloudVerifCallCount)
            assertEquals(0, vaultMetaVerifCallCount)
            assertEquals(1, decryptCalls.size)
            assertTrue(decryptCalls.single().first.contentEquals(ciphertextBytes))
            assertNull(decryptCalls.single().second)
            assertEquals(listOf(false), startSyncCalls)
        }

    @Test
    fun `unlockWithBiometricCipher invalidates stale biometric when verification fails`() =
        runTest(testDispatcher) {
            val dek = ByteArray(32) { 0x55.toByte() }
            val cipher = mockk<Cipher>(relaxed = true)
            val cloudVerification = "Y2xvdWQtdmVyaWZpY2F0aW9u"
            every {
                getAuthStateUseCase()
            } returns
                MutableStateFlow(
                    com.browntowndev.enclave.domain.port.auth.AuthState.Authenticated(
                        com.browntowndev.enclave.domain.port.auth
                            .UserProfile("u", "n", "e", null),
                    ),
                )
            every { biometricManager.decryptBytesWithCipher("vault_dek", cipher) } returns dek
            every { cryptoManager.cacheDek(dek) } answers {
                cacheDekCalls += firstArg<ByteArray>()
                Result.success(Unit)
            }
            coEvery { cloudSyncPort.getVaultVerificationData() } coAnswers {
            cloudVerifCallCount++
            Result.success(cloudVerification)
        }
            every { android.util.Base64.decode(cloudVerification, android.util.Base64.DEFAULT) } returns byteArrayOf(1, 2, 3)
            every { cryptoManager.decrypt(any<ByteArray>(), any()) } returns
                Result.failure(IllegalStateException("stale dek"))

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockWithBiometricCipher(cipher)
            advanceUntilIdle()

            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.isVaultLocked)
            assertFalse(state.isBiometricUnlockPossible)
            assertTrue(state.vaultErrorMessage?.contains("password", ignoreCase = true) == true)
            assertTrue(clearKeyCallCount >= 1)
            assertTrue(clearSecureValueCalls.contains("vault_dek"))
            assertEquals(0, startListeningCallCount)
        }

    @Test
    fun `unlockWithBiometricCipher rejects unlock when verification anchor is missing`() =
        runTest(testDispatcher) {
            val dek = ByteArray(32) { 0x55.toByte() }
            val cipher = mockk<Cipher>(relaxed = true)
            every {
                getAuthStateUseCase()
            } returns
                MutableStateFlow(
                    com.browntowndev.enclave.domain.port.auth.AuthState.Authenticated(
                        com.browntowndev.enclave.domain.port.auth
                            .UserProfile("u", "n", "e", null),
                    ),
                )
            every { biometricManager.decryptBytesWithCipher("vault_dek", cipher) } returns dek
            every { cryptoManager.cacheDek(dek) } answers {
                cacheDekCalls += firstArg<ByteArray>()
                Result.success(Unit)
            }
            coEvery { cloudSyncPort.getVaultVerificationData() } coAnswers {
            cloudVerifCallCount++
            Result.success(null)
        }
            coEvery { vaultMetadataPort.getVaultVerificationData() } coAnswers {
            vaultMetaVerifCallCount++
            events += "vaultMetaVerif"
            null
        }
            every { cryptoManager.decrypt(any<ByteArray>(), any()) } answers {
                decryptCalls += firstArg<ByteArray>() to secondArg<ByteArray?>()
                Result.success(LoginWithGoogleUseCase.VAULT_VERIFICATION_MAGIC_STRING)
            }

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockWithBiometricCipher(cipher)
            advanceUntilIdle()

            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.isVaultLocked)
            assertFalse(state.isBiometricUnlockPossible)
            assertEquals(1, cloudVerifCallCount)
            assertEquals(1, vaultMetaVerifCallCount)
            assertTrue(decryptCalls.isEmpty())
            assertTrue(clearKeyCallCount >= 1)
            assertTrue(clearSecureValueCalls.contains("vault_dek"))
            assertEquals(0, startListeningCallCount)
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
            coEvery { detectVaultReconciliationStateUseCase("password") } returns Result.success(payload)

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            val pendingState = viewModel.startupState.value as AppStartupState.Ready
            assertNotNull(pendingState.pendingVaultReconciliation)

            val dek = ByteArray(32) { 0x55.toByte() }
            val cipher = mockk<Cipher>(relaxed = true)
            every { biometricManager.decryptBytesWithCipher("vault_dek", cipher) } returns dek
            every { cryptoManager.cacheDek(dek) } answers {
                cacheDekCalls += firstArg<ByteArray>()
                Result.success(Unit)
            }
            val verification = "dGVzdC12ZXJpZmljYXRpb24="
            coEvery { cloudSyncPort.getVaultVerificationData() } returns Result.success(verification)
            every { cryptoManager.decrypt(any<ByteArray>(), any()) } answers {
                decryptCalls += firstArg<ByteArray>() to secondArg<ByteArray?>()
                Result.success(LoginWithGoogleUseCase.VAULT_VERIFICATION_MAGIC_STRING)
            }

            clearMocks(cloudSyncPort, answers = false, recordedCalls = true)
            val startListeningBaseline = startListeningCallCount

            viewModel.unlockWithBiometricCipher(cipher)
            advanceUntilIdle()

            assertEquals(startListeningBaseline, startListeningCallCount)
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(state.isVaultLocked)
            assertNotNull(state.pendingVaultReconciliation)
        }

    @Test
    fun `unlockWithBiometricCipher surfaces error when unwrap fails`() =
        runTest(testDispatcher) {
            val cipher = mockk<javax.crypto.Cipher>(relaxed = true)
            every { biometricManager.decryptBytesWithCipher("vault_dek", cipher) } returns null

            advanceUntilIdle()
            viewModel.unlockWithBiometricCipher(cipher)
            advanceUntilIdle()

            assertTrue(cacheDekCalls.isEmpty())
            assertEquals(0, syncAllCallCount)
            assertEquals(0, startListeningCallCount)
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.isVaultLocked)
            assertFalse(state.isBiometricUnlockPossible)
            assertTrue(state.vaultErrorMessage?.contains("password", ignoreCase = true) == true)
            assertTrue(clearKeyCallCount >= 1)
            assertTrue(clearSecureValueCalls.contains("vault_dek"))
            assertTrue(cacheDekCalls.isEmpty())
        }

    @Test
    fun `unlockWithBiometricCipher surfaces error when cache DEK fails`() =
        runTest(testDispatcher) {
            every { biometricPort.isBiometricAvailable() } returns true
            every { biometricPort.hasSecureValue(any()) } returns true
            viewModel =
                createViewModel()
            advanceUntilIdle()

            val dek = ByteArray(32) { 0x55.toByte() }
            val cipher = mockk<Cipher>(relaxed = true)
            every { biometricManager.decryptBytesWithCipher("vault_dek", cipher) } returns dek
            every { cryptoManager.cacheDek(dek) } answers {
                cacheDekCalls += firstArg<ByteArray>()
                Result.failure(IllegalStateException("cache failed"))
            }

            val before = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(before.isBiometricUnlockPossible)

            viewModel.unlockWithBiometricCipher(cipher)
            advanceUntilIdle()

            assertEquals(1, cacheDekCalls.size)
            assertTrue(cacheDekCalls.single().contentEquals(dek))
            assertEquals(0, syncAllCallCount)
            assertEquals(0, startListeningCallCount)
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.isVaultLocked)
            assertFalse(state.isBiometricUnlockPossible)
            assertTrue(state.vaultErrorMessage?.contains("password", ignoreCase = true) == true)
            assertTrue(clearKeyCallCount >= 1)
            assertTrue(clearSecureValueCalls.contains("vault_dek"))
        }

    @Test
    fun `loginWithGoogle clears cached dek defers cloud sync and keeps vault locked`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            coEvery { loginWithGoogleUseCase() } returns
                Result.success(UserProfile("uid", "Name", "email@test.com", null))
            every { cloudSyncPort.stopListening() } answers {
                stopListeningCallCount++
                events += "stopListening"
                Unit
            }

            viewModel.loginWithGoogle()
            advanceUntilIdle()

            assertTrue(clearKeyCallCount >= 1)
            assertTrue(stopListeningCallCount >= 1)
            assertEquals(0, startListeningCallCount)
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertTrue(state.isVaultLocked)
            assertTrue(state.cloudSyncDeferred)
            assertFalse(state.isBiometricUnlockPossible)
        }

    @Test
    fun `loginWithGoogle preserves unlocked vault when sign-in completes after local unlock`() =
        runTest(testDispatcher) {
            every { cryptoManager.hasCachedKey() } returns true
            coEvery { loginWithGoogleUseCase() } returns
                Result.success(UserProfile("uid", "Name", "email@test.com", null))
            every { syncOrchestrator.syncProgress } returns kotlinx.coroutines.flow.flowOf(null)
            coEvery { syncOrchestrator.startInitialSync(any()) } returns Result.success(Unit)
            every {
                getAuthStateUseCase()
            } returns
                MutableStateFlow(
                    com.browntowndev.enclave.domain.port.auth.AuthState.Authenticated(
                        UserProfile("uid", "Name", "email@test.com", null),
                    ),
                )

            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.loginWithGoogle()
            advanceUntilIdle()

            assertEquals(0, clearKeyCallCount)
            assertEquals(0, stopListeningCallCount)
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(state.isVaultLocked)
            assertFalse(state.cloudSyncDeferred)
        }

    @Test
    fun `unlockVault opens app only after model initialization completes`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            coEvery { detectVaultReconciliationStateUseCase("password") } returns
                Result.success(VaultReconciliationOutcome.AlreadyMatching)
            coEvery { syncOrchestrator.startInitialSync(any()) } returns Result.success(Unit)
            coEvery { initializeModelsUseCase() } coAnswers {
                delay(5_000)
                initializedModelsResult
            }

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
    @Suppress("UNCHECKED_CAST")
    fun `SCENARIO-16 unlock sync progress is wired and cleared on completion`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            coEvery { detectVaultReconciliationStateUseCase("password") } returns
                Result.success(VaultReconciliationOutcome.AlreadyMatching)

            val flow =
                kotlinx.coroutines.flow.flowOf(
                    SyncProgress(0.5f, "Syncing updates (100/200)..."),
                    null,
                )
            every { syncOrchestrator.syncProgress } returns flow
            coEvery { syncOrchestrator.startInitialSync(any()) } returns Result.success(Unit)

            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            val state = viewModel.startupState.value as AppStartupState.Ready
            assertFalse(state.isVaultLocked)
            assertNull(state.unlockSyncProgress, "unlockSyncProgress must be null after sync completion")
            assertNull(state.unlockSyncStatus, "unlockSyncStatus must be null after sync completion")
            assertNull(state.unlockSyncStatus, "unlockSyncStatus must be null after sync completion")
        }

    /**
     * Stubs the unlock-existing-vault happy path so a unit test can drive the post-unlock
     * reconciliation branch. Authenticates the user so [reconcileWithCloudIfNeeded] runs.
     */
    private fun stubAuthenticatedExistingVaultUnlock() {
        every { getAuthStateUseCase() } returns
            MutableStateFlow(
                com.browntowndev.enclave.domain.port.auth.AuthState.Authenticated(
                    com.browntowndev.enclave.domain.port.auth
                        .UserProfile("u", "n", "e", null),
                ),
            )
        coEvery { vaultMetadataPort.getVaultSalt() } returns "c2FsdA=="
        coEvery { vaultMetadataPort.getWrappedDek() } returns "d3JhcA=="
        coEvery { vaultMetadataPort.getVaultVerificationData() } coAnswers {
            vaultMetaVerifCallCount++
            events += "vaultMetaVerif"
            null
        }
        coEvery { unlockVaultUseCase(any(), any(), any()) } returns Result.success(Unit)
        every { cryptoManager.encrypt(any()) } returns Result.success(byteArrayOf())
        coEvery { cloudSyncPort.getVaultVerificationData() } coAnswers {
            cloudVerifCallCount++
            Result.success(null)
        }
        coEvery { cloudSyncPort.updateVaultVerificationData(any()) } returns Result.success(Unit)
    }

    @Test
    fun `SCN-ENTRY-001 MainViewModel matching vault path starts ordered sync`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            coEvery { detectVaultReconciliationStateUseCase("password") } returns
                Result.success(VaultReconciliationOutcome.AlreadyMatching)

            // Drive the entry path through a REAL use case so the test fails when the use case
            // does not actually replay before starting the listener.
            coEvery { cloudSyncPort.syncAllLocalDataToCloud() } coAnswers {
            syncAllCallCount++
            events += "syncAll"
            Result.success(Unit)
        }
            coEvery { cloudSyncPort.syncRemoteChangesFromCloud() } coAnswers {
                syncRemoteCallCount++
                events += "syncRemote"
                Result.success(
                    com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult(
                        eventsApplied = 0,
                        lastAppliedSequence = 0,
                        hasMore = false,
                    ),
                )
            }
            val realStartUseCase = StartCloudSyncListeningUseCase(cloudSyncPort)

            // Recreate VM so init picks up the new auth state stub and the real use case
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
            assertTrue(adoptCalls.isEmpty())
            val state = viewModel.startupState.value as AppStartupState.Ready
            assertNull(state.pendingVaultReconciliation)
        }

    @Test
    fun `cloud sync start failure leaves startup sync deferred`() =
        runTest(testDispatcher) {
            stubAuthenticatedExistingVaultUnlock()
            coEvery { detectVaultReconciliationStateUseCase("password") } returns
                Result.success(VaultReconciliationOutcome.AlreadyMatching)
            coEvery { startCloudSyncListeningUseCase(eq(true), any()) } returns
                Result.failure(IllegalStateException("replay failed"))

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
            coEvery { detectVaultReconciliationStateUseCase("password") } returns Result.success(payload)

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            assertEquals(0, startListeningCallCount)
            assertTrue(adoptCalls.isEmpty())
            assertEquals(0, initModelsCallCount)
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
            coEvery { detectVaultReconciliationStateUseCase("password") } returns Result.success(payload)

            coEvery { cloudSyncPort.syncRemoteChangesFromCloud() } coAnswers {
                syncRemoteCallCount++
                events += "syncRemote"
                Result.success(
                    com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult(
                        eventsApplied = 0,
                        lastAppliedSequence = 0,
                        hasMore = false,
                    ),
                )
            }
            val realStartUseCase = StartCloudSyncListeningUseCase(cloudSyncPort)

            workerStartCloudSyncUseCase = realStartUseCase
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            viewModel.confirmAdoptCloud()
            advanceUntilIdle()

            assertEquals(listOf(payload), adoptCalls)
            // Adoption must skip upload, then drive replay -> listen in that order.
            assertEquals(
                listOf("syncRemote", "startListening"),
                events.filter { it in listOf("syncRemote", "startListening") },
            )
            assertEquals(0, syncAllCallCount)
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
            coEvery { detectVaultReconciliationStateUseCase("password") } returns Result.success(payload)

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            viewModel.cancelReconciliation()
            advanceUntilIdle()

            assertTrue(adoptCalls.isEmpty())
            assertEquals(0, startListeningCallCount)
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

            viewModel =
                createViewModel()
            advanceUntilIdle()
            viewModel.unlockVault("password")
            advanceUntilIdle()

            assertEquals(listOf(true), startSyncCalls)
            assertTrue(events.indexOf("bootstrap") >= 0)
            assertTrue(events.indexOf("bootstrap") < events.indexOf("startSync"))
        }
}
