package com.browntowndev.enclave.app

import com.browntowndev.enclave.core.testing.FakeAuthManager
import com.browntowndev.enclave.core.testing.FakeBiometricPort
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeSyncOrchestrator
import com.browntowndev.enclave.core.testing.FakeVaultMetadataPort
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.usecase.FakeSettingsRepository
import com.browntowndev.enclave.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.enclave.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.enclave.domain.usecase.security.DecryptBiometricDekUseCase
import com.browntowndev.enclave.domain.usecase.security.GetBiometricDecryptCipherUseCase
import com.browntowndev.enclave.domain.usecase.security.GetBiometricEncryptCipherUseCase
import com.browntowndev.enclave.domain.usecase.security.StoreBiometricDekUseCase
import com.browntowndev.enclave.domain.usecase.security.VaultSessionInteractor
import com.browntowndev.enclave.presentation.AppStartupState
import com.browntowndev.enclave.presentation.MainViewModel
import com.browntowndev.enclave.testing.FakeAdoptCloudVaultUseCase
import com.browntowndev.enclave.testing.FakeBiometricCipherPort
import com.browntowndev.enclave.testing.FakeBootstrapCloudVaultFromLocalUseCase
import com.browntowndev.enclave.testing.FakeCreateVaultUseCase
import com.browntowndev.enclave.testing.FakeDetectVaultReconciliationStateUseCase
import com.browntowndev.enclave.testing.FakeInitializeModelsUseCase
import com.browntowndev.enclave.testing.FakeLoggingPort
import com.browntowndev.enclave.testing.FakeUnlockVaultUseCase
import com.browntowndev.enclave.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.testing.emptyModelsResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelOnboardingTest {
    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    // --------------- OB-01 ---------------

    @Test
    @DisplayName("OB-01 — First-launch routes to onboarding when onboardingCompleted = false")
    fun `OB-01 when onboardingCompleted is false initialRoute equals onboarding`() =
        runTest(testDispatcher) {
            // Given: FakeSettingsRepository that emits SettingsData(onboardingCompleted = false)
            // The default FakeSettingsRepository already emits onboardingCompleted = false
            val fakeSettingsRepository = FakeSettingsRepository()

            val initializeModelsUseCase =
                FakeInitializeModelsUseCase().apply { result = emptyModelsResult() }

            // Default FakeAuthManager is Unauthenticated.
            val authManager = FakeAuthManager()
            val cryptoManager = FakeCryptoManagerPort().apply { hasCachedKeyValue = true }
            val cloudSyncPort = FakeCloudSyncPort()
            val vaultMetadataPort = FakeVaultMetadataPort()
            val biometricPort = FakeBiometricPort()
            val cipherPort = FakeBiometricCipherPort()
            val loggingPort = FakeLoggingPort()
            val errorHandler = FakeViewModelErrorHandler()

            // When: MainViewModel is constructed with the fake settings (onboardingCompleted=false)
            // ENC-24: ports are wrapped in VaultSessionInteractor + cipher use cases.
            val viewModel =
                MainViewModel(
                    getAuthStateUseCase = GetAuthStateUseCase(authManager),
                    loginWithGoogleUseCase = LoginWithGoogleUseCase(authManager, loggingPort),
                    initializeModelsUseCase = initializeModelsUseCase,
                    vaultSession =
                        VaultSessionInteractor(
                            cryptoManager = cryptoManager,
                            cloudSyncPort = cloudSyncPort,
                            vaultMetadataPort = vaultMetadataPort,
                            biometricPort = biometricPort,
                            settingsRepository = fakeSettingsRepository,
                            syncOrchestrator = FakeSyncOrchestrator(),
                            loggingPort = loggingPort,
                        ),
                    getBiometricEncryptCipherUseCase = GetBiometricEncryptCipherUseCase(cipherPort),
                    getBiometricDecryptCipherUseCase = GetBiometricDecryptCipherUseCase(cipherPort),
                    storeBiometricDekUseCase = StoreBiometricDekUseCase(cipherPort),
                    decryptBiometricDekUseCase = DecryptBiometricDekUseCase(cipherPort),
                    createVaultUseCase = FakeCreateVaultUseCase(),
                    unlockVaultUseCase = FakeUnlockVaultUseCase(),
                    detectVaultReconciliationStateUseCase = FakeDetectVaultReconciliationStateUseCase(),
                    adoptCloudVaultUseCase = FakeAdoptCloudVaultUseCase(),
                    bootstrapCloudVaultFromLocalUseCase = FakeBootstrapCloudVaultFromLocalUseCase(),
                    loggingPort = loggingPort,
                    errorHandler = errorHandler,
                )

            advanceUntilIdle()

            // Then: startupState is Ready and initialRoute is "onboarding"
            val state = viewModel.startupState.value
            assertTrue(state is AppStartupState.Ready, "startupState should be Ready but was $state")
            val readyState = state as AppStartupState.Ready
            assertEquals(
                "onboarding",
                readyState.initialRoute,
                "initialRoute should be 'onboarding' when settingsRepository reports onboardingCompleted = false",
            )
        }
}
