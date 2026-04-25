package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.TtsProviderDao
import com.browntowndev.pocketcrew.core.data.local.TtsProviderEntity
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TtsProviderRepositoryImplTest {

    private val ttsProviderDao: TtsProviderDao = mockk()
    private val apiKeyManager: ApiKeyManager = mockk(relaxed = true)
    private lateinit var repository: TtsProviderRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = TtsProviderRepositoryImpl(ttsProviderDao, apiKeyManager)
    }

    @Test
    fun `getTtsProviders returns mapped domain models`() = runTest {
        val entities = listOf(
            TtsProviderEntity("1", "OpenAI TTS", ApiProvider.OPENAI, "alloy", "https://api.openai.com", "alias-1")
        )
        coEvery { ttsProviderDao.getTtsProviders() } returns flowOf(entities)

        val result = repository.getTtsProviders().first()

        assertEquals(1, result.size)
        assertEquals("1", result[0].id.value)
        assertEquals("OpenAI TTS", result[0].displayName)
        assertEquals(ApiProvider.OPENAI, result[0].provider)
        assertEquals("alloy", result[0].voiceName)
        assertEquals("https://api.openai.com", result[0].baseUrl)
    }

    @Test
    fun `saveTtsProvider inserts entity and saves API key`() = runTest {
        val asset = TtsProviderAsset(TtsProviderId("1"), "xAI TTS", ApiProvider.XAI, "eve", "https://api.x.ai", "alias-x")
        val apiKey = "sk-test"
        coEvery { ttsProviderDao.insertTtsProvider(any()) } returns Unit

        val resultId = repository.saveTtsProvider(asset, apiKey)

        assertEquals("1", resultId.value)
        coVerify {
            ttsProviderDao.insertTtsProvider(match { 
                it.id == "1" && it.displayName == "xAI TTS" && it.provider == ApiProvider.XAI && it.baseUrl == "https://api.x.ai" && it.credentialAlias == "alias-x"
            })
            apiKeyManager.save("alias-x", apiKey)
        }
    }

    @Test
    fun `deleteTtsProvider deletes entity and removes API key`() = runTest {
        val entity = TtsProviderEntity("1", "OpenAI TTS", ApiProvider.OPENAI, "alloy", "https://api.openai.com", "alias-1")
        coEvery { ttsProviderDao.getTtsProvider("1") } returns entity
        coEvery { ttsProviderDao.deleteTtsProvider("1") } returns Unit

        repository.deleteTtsProvider(TtsProviderId("1"))

        coVerify {
            ttsProviderDao.deleteTtsProvider("1")
            apiKeyManager.delete("alias-1")
        }
    }
}
