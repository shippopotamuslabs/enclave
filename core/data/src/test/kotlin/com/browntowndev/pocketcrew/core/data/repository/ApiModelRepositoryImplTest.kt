package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterProviderSort
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class ApiModelRepositoryImplTest {

    private lateinit var repo: ApiModelRepositoryImpl
    private val credentialsDao = mockk<ApiCredentialsDao>(relaxed = true)
    private val configurationsDao = mockk<ApiModelConfigurationsDao>(relaxed = true)
    private val apiKeyManager = mockk<ApiKeyManager>(relaxed = true)

    @Before
    fun setup() {
        repo = ApiModelRepositoryImpl(
            apiCredentialsDao = credentialsDao,
            apiModelConfigurationsDao = configurationsDao,
            apiKeyManager = apiKeyManager
        )
    }

    @Test
    fun `save credentials with API key stores key via credentialAlias and maps all fields`() = runTest {
        val creds = ApiCredentials(
            displayName = "Test",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            baseUrl = "https://api.openai.com/v1",
            isVision = true,
            credentialAlias = "my_key"
        )

        coEvery { credentialsDao.insert(any()) } returns 1L
        coEvery { credentialsDao.getById(1L) } returns ApiCredentialsEntity(
            id = 1L,
            displayName = "Test",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            baseUrl = "https://api.openai.com/v1",
            isVision = true,
            credentialAlias = "my_key",
        )

        val id = repo.saveCredentials(creds, "sk-test")

        assertEquals(1L, id)
        coVerify { 
            credentialsDao.insert(match {
                it.credentialAlias == "my_key" && 
                it.displayName == "Test" &&
                it.provider == ApiProvider.OPENAI &&
                it.modelId == "gpt-4o" &&
                it.baseUrl == "https://api.openai.com/v1" &&
                it.isVision == true
            }) 
        }
        verify { apiKeyManager.save("my_key", "sk-test") }
    }

    @Test
    fun `delete credentials removes both entity and key`() = runTest {
        coEvery { credentialsDao.getById(1L) } returns ApiCredentialsEntity(
            id = 1L,
            displayName = "Test",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            credentialAlias = "my_key",
            isVision = false
        )

        repo.deleteCredentials(1L)

        coVerify { credentialsDao.deleteById(1L) }
        verify { apiKeyManager.delete("my_key") }
    }
    
    @Test
    fun `credential save with blank API key skips ApiKeyManager`() = runTest {
        val creds = ApiCredentials(
            displayName = "Test",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            credentialAlias = "my_key"
        )

        coEvery { credentialsDao.insert(any()) } returns 1L
        coEvery { credentialsDao.getById(1L) } returns ApiCredentialsEntity(
            id = 1L,
            displayName = "Test",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            credentialAlias = "my_key",
            isVision = false,
        )

        val id = repo.saveCredentials(creds, "", null)

        assertEquals(1L, id)
        coVerify { credentialsDao.insert(any()) }
        verify(exactly = 0) { apiKeyManager.save(any(), any()) }
    }

    @Test
    fun `save credentials can clone an existing stored key into a new alias`() = runTest {
        val creds = ApiCredentials(
            displayName = "Cloned",
            provider = ApiProvider.XAI,
            modelId = "grok-4.20",
            credentialAlias = "new_alias"
        )

        coEvery { credentialsDao.insert(any()) } returns 5L
        coEvery { credentialsDao.getById(5L) } returns ApiCredentialsEntity(
            id = 5L,
            displayName = "Cloned",
            provider = ApiProvider.XAI,
            modelId = "grok-4.20",
            credentialAlias = "new_alias",
            isVision = false,
        )
        io.mockk.every { apiKeyManager.get("existing_alias") } returns "stored-key"

        val id = repo.saveCredentials(creds, "", "existing_alias")

        assertEquals(5L, id)
        verify(exactly = 1) { apiKeyManager.get("existing_alias") }
        verify(exactly = 1) { apiKeyManager.save("new_alias", "stored-key") }
    }

    @Test
    fun `save credentials inserts a new row when id is zero`() = runTest {
        val creds = ApiCredentials(
            displayName = "xAI Existing",
            provider = ApiProvider.XAI,
            modelId = "grok-4-fast-reasoning",
            baseUrl = "https://api.x.ai/v1",
            credentialAlias = "xai-existing-2",
        )

        coEvery { credentialsDao.insert(any()) } returns 77L
        coEvery { credentialsDao.getById(77L) } returns ApiCredentialsEntity(
            id = 77L,
            displayName = "xAI Existing",
            provider = ApiProvider.XAI,
            modelId = "grok-4-fast-reasoning",
            baseUrl = "https://api.x.ai/v1",
            credentialAlias = "xai-existing-2",
        )

        val id = repo.saveCredentials(creds, "xai-key")

        assertEquals(77L, id)
        coVerify(exactly = 1) { credentialsDao.insert(any()) }
        coVerify(exactly = 0) { credentialsDao.update(any()) }
        verify { apiKeyManager.save("xai-existing-2", "xai-key") }
    }

    @Test
    fun `save credentials with existing id fails fast when row is missing`() = runTest {
        val creds = ApiCredentials(
            id = 5L,
            displayName = "Edited",
            provider = ApiProvider.XAI,
            modelId = "grok-4-fast-reasoning",
            baseUrl = "https://api.x.ai/v1",
            credentialAlias = "xai-edited",
        )

        coEvery { credentialsDao.getById(5L) } returns null

        assertFailsWith<IllegalArgumentException> {
            repo.saveCredentials(creds, "", null)
        }

        coVerify(exactly = 0) { credentialsDao.insert(any()) }
        coVerify(exactly = 0) { credentialsDao.update(any()) }
    }

    @Test
    fun `save configuration persists openrouter routing settings`() = runTest {
        val config = ApiModelConfiguration(
            apiCredentialsId = 9L,
            displayName = "Router preset",
            openRouterRouting = OpenRouterRoutingConfiguration(
                providerSort = OpenRouterProviderSort.PRICE,
                allowFallbacks = false,
                requireParameters = true,
                dataCollectionPolicy = OpenRouterDataCollectionPolicy.ALLOW,
                zeroDataRetention = true
            )
        )

        coEvery { configurationsDao.upsert(any()) } returns 44L

        val id = repo.saveConfiguration(config)

        assertEquals(44L, id)
        coVerify {
            configurationsDao.upsert(match {
                it.openRouterProviderSort == "price" &&
                    it.openRouterAllowFallbacks == false &&
                    it.openRouterRequireParameters == true &&
                    it.openRouterDataCollectionPolicy == "allow" &&
                    it.openRouterZeroDataRetention == true
            })
        }
    }
}
