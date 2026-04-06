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
        
        coEvery { credentialsDao.upsert(any()) } returns 1L

        val id = repo.saveCredentials(creds, "sk-test")

        assertEquals(1L, id)
        coVerify { 
            credentialsDao.upsert(match { 
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
        
        coEvery { credentialsDao.upsert(any()) } returns 1L

        val id = repo.saveCredentials(creds, "")

        assertEquals(1L, id)
        coVerify { credentialsDao.upsert(any()) }
        verify(exactly = 0) { apiKeyManager.save(any(), any()) }
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
                it.customHeaders.contains("__openrouter_provider_sort") &&
                    it.customHeaders.contains("\"price\"") &&
                    it.customHeaders.contains("__openrouter_allow_fallbacks") &&
                    it.customHeaders.contains("false") &&
                    it.customHeaders.contains("__openrouter_require_parameters") &&
                    it.customHeaders.contains("__openrouter_data_collection") &&
                    it.customHeaders.contains("\"allow\"") &&
                    it.customHeaders.contains("__openrouter_zdr")
            })
        }
    }
}
