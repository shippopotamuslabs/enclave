package com.browntowndev.pocketcrew.domain.usecase.auth

import android.util.Log
import com.browntowndev.pocketcrew.domain.port.auth.AuthManager
import com.browntowndev.pocketcrew.domain.port.auth.UserProfile
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LoginWithGoogleUseCaseTest {
    private val authManager = mockk<AuthManager>()
    private val cloudSyncPort = mockk<CloudSyncPort>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private lateinit var loginWithGoogleUseCase: LoginWithGoogleUseCase

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { cryptoManager.hasCachedKey() } returns false

        loginWithGoogleUseCase = LoginWithGoogleUseCase(authManager, loggingPort)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `google sign-in does not initialize or sync vault while encryption is locked`() =
        runTest {
            val user = UserProfile("uid1", "Name", "email@test.com", null)
            coEvery { authManager.signInWithGoogle() } answers { Result.success(user) }

            val result = loginWithGoogleUseCase()

            assertTrue(result.isSuccess)
            verify(exactly = 0) { cryptoManager.generateSalt() }
            verify(exactly = 0) { cryptoManager.encodeBase64(any()) }
            coVerify(exactly = 0) { cloudSyncPort.getVaultSalt() }
            coVerify(exactly = 0) { cloudSyncPort.updateVaultSalt(any()) }
            coVerify(exactly = 0) { cloudSyncPort.syncAllLocalDataToCloud() }
            verify(exactly = 0) { cloudSyncPort.startListening() }
        }

    @Test
    fun `google sign-in does NOT auto-sync even when vault is unlocked`() =
        runTest {
            val user = UserProfile("uid1", "Name", "email@test.com", null)
            coEvery { authManager.signInWithGoogle() } answers { Result.success(user) }
            every { cryptoManager.hasCachedKey() } returns true

            val result = loginWithGoogleUseCase()

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { cloudSyncPort.syncAllLocalDataToCloud() }
            coVerify(exactly = 0) { cloudSyncPort.enqueueEmbeddingRegeneration() }
            verify(exactly = 0) { cloudSyncPort.startListening() }
        }

    @Test
    fun `google sign-in failure does not start listener`() =
        runTest {
            coEvery { authManager.signInWithGoogle() } returns Result.failure(IllegalStateException("network"))

            val result = loginWithGoogleUseCase()

            assertTrue(result.isFailure)
            verify(exactly = 0) { cloudSyncPort.startListening() }
            coVerify(exactly = 0) { cloudSyncPort.syncAllLocalDataToCloud() }
        }
}
