package com.browntowndev.pocketcrew.presentation

import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.repository.EmbeddingProgressPort
import com.browntowndev.pocketcrew.domain.port.security.BiometricPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.pocketcrew.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.pocketcrew.domain.usecase.download.InitializeModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.UnlockVaultUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.Base64

@ExtendWith(MainDispatcherRule::class)
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
    private val biometricPort = mockk<BiometricPort>()
    private val unlockVaultUseCase = mockk<UnlockVaultUseCase>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()
    private val embeddingProgressPort = mockk<EmbeddingProgressPort>()

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

        coEvery { initializeModelsUseCase() } returns mockk(relaxed = true)
        every { cryptoManager.hasCachedKey() } returns false
        every { biometricPort.isBiometricAvailable() } returns false
        every { getAuthStateUseCase() } returns MutableStateFlow(AuthState.Unauthenticated)
        every { embeddingProgressPort.isReindexing } returns MutableStateFlow(false)
        every { embeddingProgressPort.progress } returns MutableStateFlow(0f)
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }

        viewModel = MainViewModel(
            getAuthStateUseCase,
            loginWithGoogleUseCase,
            initializeModelsUseCase,
            cryptoManager,
            cloudSyncPort,
            biometricPort,
            unlockVaultUseCase,
            loggingPort,
            errorHandler,
            embeddingProgressPort
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `unlockVault sets isUnlocking to true then false`() = runTest(testDispatcher) {
        coEvery { cloudSyncPort.getVaultSalt() } coAnswers {
            delay(1000)
            Result.success("c2FsdA==")
        }
        coEvery { cloudSyncPort.getVaultVerificationData() } returns Result.success(null)
        coEvery { unlockVaultUseCase(any(), any()) } returns Result.success(Unit)
        every { cryptoManager.encrypt(any()) } returns Result.success(byteArrayOf())
        coEvery { cloudSyncPort.updateVaultVerificationData(any()) } returns Result.success(Unit)

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
}
