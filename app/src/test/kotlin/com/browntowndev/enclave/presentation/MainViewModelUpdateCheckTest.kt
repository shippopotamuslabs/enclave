package com.browntowndev.enclave.presentation

import com.browntowndev.enclave.core.testing.FakeAuthManager
import com.browntowndev.enclave.core.testing.FakeBiometricPort
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeInstalledAppVersionPort
import com.browntowndev.enclave.core.testing.FakeSyncOrchestrator
import com.browntowndev.enclave.core.testing.FakeUpdateCheckPreferencesPort
import com.browntowndev.enclave.core.testing.FakeUpdateMetadataPort
import com.browntowndev.enclave.core.testing.FakeVaultMetadataPort
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.testing.TestClock
import com.browntowndev.enclave.domain.model.update.LatestRelease
import com.browntowndev.enclave.domain.usecase.FakeSettingsRepository
import com.browntowndev.enclave.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.enclave.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.enclave.domain.usecase.security.DecryptBiometricDekUseCase
import com.browntowndev.enclave.domain.usecase.security.GetBiometricDecryptCipherUseCase
import com.browntowndev.enclave.domain.usecase.security.GetBiometricEncryptCipherUseCase
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncResult
import com.browntowndev.enclave.domain.usecase.security.StoreBiometricDekUseCase
import com.browntowndev.enclave.domain.usecase.security.VaultSessionInteractor
import com.browntowndev.enclave.domain.usecase.update.CheckForAppUpdateUseCase
import com.browntowndev.enclave.domain.usecase.update.DismissAppUpdateUseCase
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
import com.browntowndev.enclave.testing.emptyModelsResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * ENC-250: update-check wiring in [MainViewModel] uses the real update use
 * cases over `:core:testing` fakes. No `verify` — assertions read `UiState`.
 */
@ExtendWith(MainDispatcherRule::class, RobolectricExtension::class)
@Config(sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelUpdateCheckTest {
    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val events = mutableListOf<String>()

    private val authManager = FakeAuthManager()
    private val initializeModelsUseCase = FakeInitializeModelsUseCase(events)
    private val cryptoManager = FakeCryptoManagerPort()
    private val cloudSyncPort = FakeCloudSyncPort()
    private val vaultMetadataPort = FakeVaultMetadataPort()
    private val biometricPort = FakeBiometricPort()
    private val cipherPort = FakeBiometricCipherPort()
    private val loggingPort = FakeLoggingPort()
    private val errorHandler = FakeViewModelErrorHandler()
    private val syncOrchestrator = FakeSyncOrchestrator()
    private val startCloudSyncListeningUseCase = FakeStartCloudSyncListeningUseCase(events)

    private val updateMetadata = FakeUpdateMetadataPort()
    private val installedVersion = FakeInstalledAppVersionPort()
    private val updatePrefs = FakeUpdateCheckPreferencesPort()
    private val clock = TestClock(1_000_000_000L)

    private val settingsRepository = FakeSettingsRepository()

    private lateinit var viewModel: MainViewModel

    private fun createViewModel(): MainViewModel {
        val vaultSession =
            VaultSessionInteractor(
                cryptoManager = cryptoManager,
                cloudSyncPort = cloudSyncPort,
                vaultMetadataPort = vaultMetadataPort,
                biometricPort = biometricPort,
                settingsRepository = settingsRepository,
                syncOrchestrator = syncOrchestrator,
                loggingPort = loggingPort,
            )
        return MainViewModel(
            GetAuthStateUseCase(authManager),
            LoginWithGoogleUseCase(authManager, loggingPort),
            initializeModelsUseCase,
            vaultSession,
            GetBiometricEncryptCipherUseCase(cipherPort),
            GetBiometricDecryptCipherUseCase(cipherPort),
            StoreBiometricDekUseCase(cipherPort),
            DecryptBiometricDekUseCase(cipherPort),
            FakeCreateVaultUseCase(),
            FakeUnlockVaultUseCase(),
            FakeDetectVaultReconciliationStateUseCase(),
            FakeAdoptCloudVaultUseCase(events),
            FakeBootstrapCloudVaultFromLocalUseCase(events),
            loggingPort,
            errorHandler,
            CheckForAppUpdateUseCase(updateMetadata, installedVersion, updatePrefs, clock),
            DismissAppUpdateUseCase(updatePrefs),
        )
    }

    @BeforeEach
    fun setUp() {
        initializeModelsUseCase.result = emptyModelsResult()
        vaultMetadataPort.salt = "c2FsdA=="

        syncOrchestrator.startHandler = { uploadLocalFirst ->
            val result =
                runBlocking {
                    startCloudSyncListeningUseCase(uploadLocalFirst, null)
                }
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
    }

    private fun release(versionCode: Int): LatestRelease =
        LatestRelease(
            versionCode = versionCode,
            versionName = "1.1.0",
            apkFileName = "enclave-v1.1.0.apk",
            downloadUrl =
                "https://github.com/shippopotamuslabs/enclave/releases/latest/download/enclave-v1.1.0.apk",
        )

    private fun readyState(): AppStartupState.Ready {
        val state = viewModel.startupState.value
        assertTrue(state is AppStartupState.Ready, "State should be Ready")
        return state as AppStartupState.Ready
    }

    @Test
    fun `update available sets presentation state`() =
        runTest(testDispatcher) {
            installedVersion.installedVersionCode = 1
            updateMetadata.release = release(2)

            viewModel = createViewModel()
            advanceUntilIdle()

            val ready = readyState()
            assertEquals("1.1.0", ready.availableVersionName)
            assertEquals(
                "https://github.com/shippopotamuslabs/enclave/releases/latest/download/enclave-v1.1.0.apk",
                ready.updateDownloadUrl,
            )
        }

    @Test
    fun `dismiss clears state and persists dismissed version`() =
        runTest(testDispatcher) {
            installedVersion.installedVersionCode = 1
            updateMetadata.release = release(2)

            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.dismissAppUpdate()
            advanceUntilIdle()

            val ready = readyState()
            assertNull(ready.availableVersionName)
            assertNull(ready.updateDownloadUrl)
            assertEquals(2, updatePrefs.dismissedVersionCode())
        }

    @Test
    fun `update resolving before startup completes is not lost`() =
        runTest(testDispatcher) {
            installedVersion.installedVersionCode = 1
            updateMetadata.release = release(2)
            // Gate startup on the slow models fetch so the update check
            // (immediate fake) resolves while startup is still Loading.
            // Unlocked vault is required: a locked vault skips the models
            // fetch and reaches Ready without hitting the delay.
            cryptoManager.hasCachedKeyValue = true
            initializeModelsUseCase.delayMs = 5_000

            viewModel = createViewModel()
            advanceTimeBy(1_000)
            runCurrent()

            assertTrue(
                viewModel.startupState.value is AppStartupState.Loading,
                "Startup should still be Loading while models fetch is gated",
            )
            assertEquals(1, updateMetadata.fetchCount, "Update check should have resolved already")

            advanceTimeBy(5_000)
            advanceUntilIdle()

            val ready = readyState()
            assertEquals("1.1.0", ready.availableVersionName)
            assertEquals(
                "https://github.com/shippopotamuslabs/enclave/releases/latest/download/enclave-v1.1.0.apk",
                ready.updateDownloadUrl,
            )
        }

    @Test
    fun `no update leaves state empty`() =
        runTest(testDispatcher) {
            installedVersion.installedVersionCode = 2
            updateMetadata.release = null

            viewModel = createViewModel()
            advanceUntilIdle()

            val ready = readyState()
            assertNull(ready.availableVersionName)
            assertNull(ready.updateDownloadUrl)
        }
}
