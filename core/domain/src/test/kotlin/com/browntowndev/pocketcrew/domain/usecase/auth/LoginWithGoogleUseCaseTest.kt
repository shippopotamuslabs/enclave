package com.browntowndev.pocketcrew.domain.usecase.auth

import com.browntowndev.pocketcrew.domain.port.auth.AuthManager
import com.browntowndev.pocketcrew.domain.port.auth.UserProfile
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import android.util.Log
import android.util.Base64

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

        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "mock-base64"
        
        loginWithGoogleUseCase = LoginWithGoogleUseCase(authManager, cloudSyncPort, cryptoManager, loggingPort)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic(Base64::class)
    }

    @Test
    fun `on first login generates and saves new salt if none exists in cloud`() = runTest {
        val user = UserProfile("uid1", "Name", "email@test.com", null)
        coEvery { authManager.signInWithGoogle() } returns Result.success(user)
        coEvery { cloudSyncPort.getVaultSalt() } returns Result.success(null)
        every { cryptoManager.generateSalt() } returns "new-salt".toByteArray()
        coEvery { cloudSyncPort.updateVaultSalt(any()) } returns Result.success(Unit)

        val result = loginWithGoogleUseCase()

        assertTrue(result.isSuccess)
        verify { cryptoManager.generateSalt() }
        coVerify { cloudSyncPort.updateVaultSalt("mock-base64") }
    }

    @Test
    fun `on subsequent login retrieves existing salt and does not generate new one`() = runTest {
        val user = UserProfile("uid1", "Name", "email@test.com", null)
        coEvery { authManager.signInWithGoogle() } returns Result.success(user)
        coEvery { cloudSyncPort.getVaultSalt() } returns Result.success("existing-salt")

        val result = loginWithGoogleUseCase()

        assertTrue(result.isSuccess)
        verify(exactly = 0) { cryptoManager.generateSalt() }
        coVerify(exactly = 0) { cloudSyncPort.updateVaultSalt(any()) }
    }
}
