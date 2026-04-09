package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchToolExecutorImplTest {

    @Test
    fun `execute delegates to Tavily repository when search is enabled`() = runTest {
        val settingsRepository = mockk<SettingsRepository>()
        val tavilySearchRepository = mockk<TavilySearchRepository>()
        every { settingsRepository.settingsFlow } returns flowOf(SettingsData(searchEnabled = true))
        every { tavilySearchRepository.search("android 17 local network") } returns """{"query":"android 17 local network","results":[]}"""

        val executor = SearchToolExecutorImpl(
            loggingPort = mockk<LoggingPort>(relaxed = true),
            settingsRepository = settingsRepository,
            tavilySearchRepository = tavilySearchRepository,
        )

        val result = executor.execute(
            ToolCallRequest(
                toolName = "tavily_web_search",
                argumentsJson = """{"query":"android 17 local network"}""",
                provider = "OPENAI",
                modelType = ModelType.FAST,
            )
        )

        assertEquals("""{"query":"android 17 local network","results":[]}""", result.resultJson)
    }

    @Test
    fun `execute throws IllegalStateException when search is disabled`() = runTest {
        val settingsRepository = mockk<SettingsRepository>()
        val tavilySearchRepository = mockk<TavilySearchRepository>()
        every { settingsRepository.settingsFlow } returns flowOf(SettingsData(searchEnabled = false))

        val executor = SearchToolExecutorImpl(
            loggingPort = mockk<LoggingPort>(relaxed = true),
            settingsRepository = settingsRepository,
            tavilySearchRepository = tavilySearchRepository,
        )

        assertFailsWith<IllegalStateException> {
            executor.execute(
                ToolCallRequest(
                    toolName = "tavily_web_search",
                    argumentsJson = """{"query":"test"}""",
                    provider = "OPENAI",
                    modelType = ModelType.FAST,
                )
            )
        }
    }

    @Test
    fun `execute rejects unsupported tool names`() = runTest {
        val executor = SearchToolExecutorImpl(
            loggingPort = mockk<LoggingPort>(relaxed = true),
            settingsRepository = mockk(),
            tavilySearchRepository = mockk(),
        )
        assertFailsWith<IllegalArgumentException> {
            executor.execute(
                ToolCallRequest(
                    toolName = "weather_lookup",
                    argumentsJson = """{"query":"Boston"}""",
                    provider = "OPENAI",
                    modelType = ModelType.FAST,
                )
            )
        }
    }

    @Test
    fun `execute rejects missing query arguments`() = runTest {
        val executor = SearchToolExecutorImpl(
            loggingPort = mockk<LoggingPort>(relaxed = true),
            settingsRepository = mockk(),
            tavilySearchRepository = mockk(),
        )
        assertFailsWith<IllegalArgumentException> {
            executor.execute(
                ToolCallRequest(
                    toolName = "tavily_web_search",
                    argumentsJson = "{}",
                    provider = "OPENAI",
                    modelType = ModelType.FAST,
                )
            )
        }
    }
}
