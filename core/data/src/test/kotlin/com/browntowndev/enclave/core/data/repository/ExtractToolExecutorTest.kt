package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ExtractedUrlTrackerPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ExtractToolExecutorTest {
    @Test
    fun `execute delegates to TavilySearchRepository extract when search is enabled and Tavily is selected`() =
        runTest {
            val settingsRepository = mockk<SettingsRepository>()
            val tavilySearchRepository = mockk<TavilySearchRepository>()
            val exaSearchRepository = mockk<ExaSearchRepository>()
            val webSearchSourceDao = mockk<WebSearchSourceDao>(relaxed = true)
            val eventBus = mockk<ToolExecutionEventBus>(relaxed = true)
            val extractedUrlTracker = mockk<ExtractedUrlTrackerPort>(relaxed = true)

            every { settingsRepository.settingsFlow } returns
                flowOf(SettingsData(searchEnabled = true, searchProvider = SearchProvider.TAVILY))
            every { tavilySearchRepository.extract(any(), any(), any()) } returns """{"results":[]}"""

            val executor =
                ExtractToolExecutorImpl(
                    loggingPort = mockk<LoggingPort>(relaxed = true),
                    settingsRepository = settingsRepository,
                    tavilySearchRepository = tavilySearchRepository,
                    exaSearchRepository = exaSearchRepository,
                    webSearchSourceDao = webSearchSourceDao,
                    eventBus = eventBus,
                    extractedUrlTracker = extractedUrlTracker,
                )

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = "web_extract",
                        argumentsJson = """{"urls":["https://example.com"]}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )

            assertEquals("""{"results":[]}""", result.resultJson)
            coVerify { webSearchSourceDao.markExtracted("https://example.com", any()) }
        }

    @Test
    fun `execute delegates to ExaSearchRepository extract when search is enabled and Exa is selected`() =
        runTest {
            val settingsRepository = mockk<SettingsRepository>()
            val tavilySearchRepository = mockk<TavilySearchRepository>()
            val exaSearchRepository = mockk<ExaSearchRepository>()
            val webSearchSourceDao = mockk<WebSearchSourceDao>(relaxed = true)
            val eventBus = mockk<ToolExecutionEventBus>(relaxed = true)
            val extractedUrlTracker = mockk<ExtractedUrlTrackerPort>(relaxed = true)

            every { settingsRepository.settingsFlow } returns
                flowOf(SettingsData(searchEnabled = true, searchProvider = SearchProvider.EXA))
            every { exaSearchRepository.extract(any()) } returns """{"results":[]}"""

            val executor =
                ExtractToolExecutorImpl(
                    loggingPort = mockk<LoggingPort>(relaxed = true),
                    settingsRepository = settingsRepository,
                    tavilySearchRepository = tavilySearchRepository,
                    exaSearchRepository = exaSearchRepository,
                    webSearchSourceDao = webSearchSourceDao,
                    eventBus = eventBus,
                    extractedUrlTracker = extractedUrlTracker,
                )

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = "web_extract",
                        argumentsJson = """{"urls":["https://example.com"]}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )

            assertEquals("""{"results":[]}""", result.resultJson)
            coVerify { webSearchSourceDao.markExtracted("https://example.com", any()) }
        }

    @Test
    fun `execute throws IllegalStateException when search is disabled`() =
        runTest {
            val settingsRepository = mockk<SettingsRepository>()
            every { settingsRepository.settingsFlow } returns flowOf(SettingsData(searchEnabled = false))

            val executor =
                ExtractToolExecutorImpl(
                    loggingPort = mockk<LoggingPort>(relaxed = true),
                    settingsRepository = settingsRepository,
                    tavilySearchRepository = mockk(),
                    exaSearchRepository = mockk(),
                    webSearchSourceDao = mockk(),
                    eventBus = mockk(),
                    extractedUrlTracker = mockk(),
                )

            assertFailsWith<IllegalStateException> {
                executor.execute(
                    ToolCallRequest(
                        toolName = "web_extract",
                        argumentsJson = """{"urls":["https://example.com"]}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )
            }
        }
}
