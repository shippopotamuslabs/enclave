package com.browntowndev.enclave.app

import com.browntowndev.enclave.core.data.security.AndroidBiometricManager
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.security.BiometricPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import com.browntowndev.enclave.domain.usecase.FakeSettingsRepository
import com.browntowndev.enclave.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.enclave.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.enclave.domain.usecase.download.InitializeModelsUseCase
import com.browntowndev.enclave.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.enclave.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import com.browntowndev.enclave.domain.usecase.security.UnlockVaultUseCase
import com.browntowndev.enclave.presentation.AppStartupState
import com.browntowndev.enclave.presentation.MainViewModel
import com.browntowndev.enclave.testing.FakeLoggingPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

            val initializedModelsResult =
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

            val getAuthStateUseCase = mockk<GetAuthStateUseCase>()
            val loginWithGoogleUseCase = mockk<LoginWithGoogleUseCase>()
            val initializeModelsUseCase = mockk<InitializeModelsUseCase>()
            val cryptoManager = mockk<CryptoManagerPort>()
            val cloudSyncPort = mockk<CloudSyncPort>()
            val vaultMetadataPort = mockk<VaultMetadataPort>()
            val biometricPort = mockk<BiometricPort>(relaxUnitFun = true)
            val biometricManager = mockk<AndroidBiometricManager>(relaxUnitFun = true)
            val createVaultUseCase = mockk<CreateVaultUseCase>()
            val unlockVaultUseCase = mockk<UnlockVaultUseCase>()
            val detectVaultReconciliationStateUseCase = mockk<DetectVaultReconciliationStateUseCase>()
            val adoptCloudVaultUseCase = mockk<AdoptCloudVaultUseCase>()
            val bootstrapCloudVaultFromLocalUseCase = mockk<BootstrapCloudVaultFromLocalUseCase>()
            val startCloudSyncListeningUseCase = mockk<StartCloudSyncListeningUseCase>()
            val loggingPort = FakeLoggingPort()
            val errorHandler = mockk<ViewModelErrorHandler>()

            // Vault is unlocked: hasCachedKey() returns true
            every { cryptoManager.hasCachedKey() } returns true
            every { biometricPort.isBiometricAvailable() } returns false
            every { biometricPort.hasSecureValue(any()) } returns false
            every { biometricManager.getEncryptCipher(any()) } returns null
            every { biometricManager.getDecryptCipher(any()) } returns null
            every { getAuthStateUseCase() } returns MutableStateFlow(AuthState.Unauthenticated)
            every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
                kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }

            // initializeModelsUseCase returns empty modelsToDownload
            coEvery { initializeModelsUseCase() } returns initializedModelsResult

            // When: MainViewModel is constructed with the fake settings (onboardingCompleted=false)
            val viewModel =
                MainViewModel(
                    getAuthStateUseCase = getAuthStateUseCase,
                    loginWithGoogleUseCase = loginWithGoogleUseCase,
                    initializeModelsUseCase = initializeModelsUseCase,
                    cryptoManager = cryptoManager,
                    cloudSyncPort = cloudSyncPort,
                    vaultMetadataPort = vaultMetadataPort,
                    biometricPort = biometricPort,
                    biometricManager = biometricManager,
                    createVaultUseCase = createVaultUseCase,
                    unlockVaultUseCase = unlockVaultUseCase,
                    detectVaultReconciliationStateUseCase = detectVaultReconciliationStateUseCase,
                    adoptCloudVaultUseCase = adoptCloudVaultUseCase,
                    bootstrapCloudVaultFromLocalUseCase = bootstrapCloudVaultFromLocalUseCase,
                    startCloudSyncListeningUseCase = startCloudSyncListeningUseCase,
                    loggingPort = loggingPort,
                    errorHandler = errorHandler,
                    settingsRepository = fakeSettingsRepository,
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
