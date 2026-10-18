package com.browntowndev.enclave.domain.usecase.auth

import com.browntowndev.enclave.core.testing.FakeAuthManager
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.port.auth.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LoginWithGoogleUseCaseTest {
    private val authManager = FakeAuthManager()
    private val cloudSyncPort = FakeCloudSyncPort()
    private val cryptoManager = FakeCryptoManagerPort()
    private val loggingPort = FakeLoggingPort()
    private lateinit var loginWithGoogleUseCase: LoginWithGoogleUseCase

    @BeforeEach
    fun setup() {
        cryptoManager.hasCachedKeyValue = false

        loginWithGoogleUseCase = LoginWithGoogleUseCase(authManager, loggingPort)
    }

    @Test
    fun `google sign-in does not initialize or sync vault while encryption is locked`() =
        runTest {
            val user = UserProfile("uid1", "Name", "email@test.com", null)
            authManager.signInResult = Result.success(user)

            val result = loginWithGoogleUseCase()

            assertTrue(result.isSuccess)
            assertEquals(0, cryptoManager.generateSaltCallCount)
            assertEquals(0, cryptoManager.encodeBase64CallCount)
            assertEquals(0, cloudSyncPort.count("getVaultSalt"))
            assertEquals(0, cloudSyncPort.count("updateVaultSalt"))
            assertEquals(0, cloudSyncPort.count("syncAll"))
            assertEquals(0, cloudSyncPort.count("startListening"))
        }

    @Test
    fun `google sign-in does NOT auto-sync even when vault is unlocked`() =
        runTest {
            val user = UserProfile("uid1", "Name", "email@test.com", null)
            authManager.signInResult = Result.success(user)
            cryptoManager.hasCachedKeyValue = true

            val result = loginWithGoogleUseCase()

            assertTrue(result.isSuccess)
            assertEquals(0, cloudSyncPort.count("syncAll"))
            assertEquals(0, cloudSyncPort.count("enqueueEmbeddingRegeneration"))
            assertEquals(0, cloudSyncPort.count("startListening"))
        }

    @Test
    fun `google sign-in failure does not start listener`() =
        runTest {
            authManager.signInResult = Result.failure(IllegalStateException("network"))

            val result = loginWithGoogleUseCase()

            assertTrue(result.isFailure)
            assertEquals(0, cloudSyncPort.count("startListening"))
            assertEquals(0, cloudSyncPort.count("syncAll"))
        }
}
