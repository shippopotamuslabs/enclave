package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UnlockVaultUseCaseTest {

    private lateinit var cryptoManager: FakeCryptoManager
    private lateinit var apiModelRepository: FakeApiModelRepository
    private lateinit var apiKeyProvider: FakeApiKeyProvider
    private lateinit var useCase: UnlockVaultUseCase

    @BeforeEach
    fun setup() {
        cryptoManager = FakeCryptoManager()
        apiModelRepository = FakeApiModelRepository()
        apiKeyProvider = FakeApiKeyProvider()
        useCase = UnlockVaultUseCase(cryptoManager, apiModelRepository, apiKeyProvider)
    }

    @Test
    fun `returns failure and does not cache key when KEK derivation fails`() = runTest {
        cryptoManager.deriveKekResult = Result.failure(IllegalStateException("derive failure"))

        val result = useCase("password", ByteArray(16), wrappedDekStub())

        assertTrue(result.isFailure)
        assertFalse(cryptoManager.dekCached)
        assertTrue(apiKeyProvider.savedKeys.isEmpty())
    }

    @Test
    fun `returns failure and does not cache key when DEK unwrap fails (wrong password)`() = runTest {
        cryptoManager.unwrapResult = Result.failure(javax.crypto.AEADBadTagException("bad tag"))

        val result = useCase("wrongpass", ByteArray(16), wrappedDekStub())

        assertTrue(result.isFailure)
        assertFalse(cryptoManager.dekCached)
        assertTrue(apiKeyProvider.savedKeys.isEmpty())
    }

    @Test
    fun `caches DEK and returns success when credentials list is empty`() = runTest {
        apiModelRepository.credentials = emptyList()

        val result = useCase("password", ByteArray(16), wrappedDekStub())

        assertTrue(result.isSuccess)
        assertTrue(cryptoManager.dekCached)
        assertTrue(apiKeyProvider.savedKeys.isEmpty())
    }

    @Test
    fun `decrypts and saves api key for credential with encrypted key`() = runTest {
        val ciphertext = byteArrayOf(1, 2, 3)
        val plaintext = "sk-real-key"
        cryptoManager.decryptResult = { Result.success(plaintext) }
        apiModelRepository.credentials = listOf(credential(alias = "alias-1", encryptedKey = ciphertext))

        val result = useCase("password", ByteArray(16), wrappedDekStub())

        assertTrue(result.isSuccess)
        assertTrue(apiKeyProvider.savedKeys.containsKey("alias-1"))
        assertTrue(apiKeyProvider.savedKeys["alias-1"] == plaintext)
    }

    @Test
    fun `does not save api key when decryption fails but still succeeds overall`() = runTest {
        cryptoManager.decryptResult = { Result.failure(IllegalStateException("bad key")) }
        apiModelRepository.credentials = listOf(credential(alias = "alias-1", encryptedKey = byteArrayOf(9)))

        val result = useCase("password", ByteArray(16), wrappedDekStub())

        assertTrue(result.isSuccess)
        assertTrue(apiKeyProvider.savedKeys.isEmpty())
    }

    @Test
    fun `skips credential with null encrypted key`() = runTest {
        apiModelRepository.credentials = listOf(credential(alias = "alias-1", encryptedKey = null))

        val result = useCase("password", ByteArray(16), wrappedDekStub())

        assertTrue(result.isSuccess)
        assertFalse(cryptoManager.decryptCalled)
        assertTrue(apiKeyProvider.savedKeys.isEmpty())
    }

    @Test
    fun `handles mix of null, success, and failing decryptions across multiple credentials`() = runTest {
        val successCiphertext = byteArrayOf(1)
        val failCiphertext = byteArrayOf(2)
        cryptoManager.decryptResult = { ciphertext ->
            when {
                ciphertext.contentEquals(successCiphertext) -> Result.success("plaintext-key")
                else -> Result.failure(IllegalStateException("bad key"))
            }
        }
        apiModelRepository.credentials = listOf(
            credential(alias = "has-null", encryptedKey = null),
            credential(alias = "decrypts-ok", encryptedKey = successCiphertext),
            credential(alias = "decrypts-bad", encryptedKey = failCiphertext),
        )

        val result = useCase("password", ByteArray(16), wrappedDekStub())

        assertTrue(result.isSuccess)
        assertFalse(apiKeyProvider.savedKeys.containsKey("has-null"))
        assertTrue(apiKeyProvider.savedKeys["decrypts-ok"] == "plaintext-key")
        assertFalse(apiKeyProvider.savedKeys.containsKey("decrypts-bad"))
    }

    @Test
    fun `returns failure when getAllCredentials throws`() = runTest {
        apiModelRepository.throwOnGetAll = RuntimeException("db error")

        val result = useCase("password", ByteArray(16), wrappedDekStub())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message == "db error")
        assertFalse(cryptoManager.dekCached)
    }

    private fun wrappedDekStub(): ByteArray = ByteArray(60)

    private fun credential(alias: String, encryptedKey: ByteArray?) = ApiCredentials(
        id = ApiCredentialsId(alias),
        displayName = alias,
        provider = ApiProvider.ANTHROPIC,
        modelId = "claude-opus-4-7",
        credentialAlias = alias,
        encryptedApiKey = encryptedKey,
    )

    private class FakeCryptoManager : CryptoManagerPort {
        var deriveKekResult: Result<ByteArray> = Result.success(ByteArray(32))
        var unwrapResult: Result<ByteArray> = Result.success(ByteArray(32))
        var dekCached = false
        var decryptCalled = false
        var decryptResult: (ByteArray) -> Result<String> = { Result.success("decrypted") }

        override fun deriveKek(password: String, salt: ByteArray): Result<ByteArray> = deriveKekResult
        override fun generateDek(size: Int): ByteArray = ByteArray(size)
        override fun wrapDek(dek: ByteArray, kek: ByteArray): Result<ByteArray> = Result.success(dek)
        override fun unwrapDek(wrapped: ByteArray, kek: ByteArray): Result<ByteArray> = unwrapResult
        override fun cacheDek(dek: ByteArray): Result<Unit> {
            dekCached = true
            return Result.success(Unit)
        }
        override fun getCachedDek(): ByteArray? = if (dekCached) ByteArray(32) else null
        override fun clearKey() {
            dekCached = false
        }
        override fun hasCachedKey(): Boolean = dekCached
        override val isVaultUnlocked: StateFlow<Boolean> get() = error("not used")
        override fun encrypt(plaintext: String): Result<ByteArray> = error("not used")
        override fun encryptBytes(data: ByteArray): Result<ByteArray> = error("not used")
        override fun decrypt(ciphertext: ByteArray): Result<String> {
            decryptCalled = true
            return decryptResult(ciphertext)
        }
        override fun decryptBytes(ciphertext: ByteArray): Result<ByteArray> = error("not used")
        override fun generateSalt(): ByteArray = ByteArray(16)
        override fun encodeBase64(data: ByteArray): String = error("not used")
        override fun decodeBase64(base64: String): ByteArray = error("not used")
        override fun generateRandomKey(size: Int): ByteArray = error("not used")
        override fun generateRandomIv(size: Int): ByteArray = error("not used")
        override fun encryptWithKey(data: ByteArray, key: ByteArray, iv: ByteArray): Result<ByteArray> = error("not used")
        override fun decryptWithKey(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): Result<ByteArray> = error("not used")
    }

    private class FakeApiModelRepository : ApiModelRepositoryPort {
        var credentials: List<ApiCredentials> = emptyList()
        var throwOnGetAll: Exception? = null

        override suspend fun getAllCredentials(): List<ApiCredentials> {
            throwOnGetAll?.let { throw it }
            return credentials
        }

        override fun observeAllCredentials(): Flow<List<ApiCredentials>> = emptyFlow()
        override fun observeAllConfigurations() = emptyFlow<List<com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration>>()
        override suspend fun getCredentialsById(id: ApiCredentialsId) = null
        override suspend fun saveCredentials(credentials: ApiCredentials, apiKey: String, sourceCredentialAlias: String?) = ApiCredentialsId("")
        override suspend fun findMatchingCredentials(provider: ApiProvider, modelId: String, baseUrl: String?, apiKey: String, sourceCredentialAlias: String?) = null
        override suspend fun deleteCredentials(id: ApiCredentialsId) = Unit
        override suspend fun getConfigurationsForCredentials(credentialsId: ApiCredentialsId) = emptyList<com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration>()
        override suspend fun getConfigurationById(id: com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId) = null
        override suspend fun saveConfiguration(config: com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration) = com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId("")
        override suspend fun deleteConfiguration(id: com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId) = Unit
        override suspend fun deleteConfigurationsForCredentials(credentialsId: ApiCredentialsId) = Unit
    }

    private class FakeApiKeyProvider : ApiKeyProviderPort {
        val savedKeys = mutableMapOf<String, String>()

        override suspend fun getApiKey(credentialAlias: String): String? = savedKeys[credentialAlias]
        override fun saveApiKey(credentialAlias: String, apiKey: String) {
            savedKeys[credentialAlias] = apiKey
        }
    }
}
