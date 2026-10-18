package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.repository.SettingsData
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Translated from MockK to hand-written fakes (ENC-28): the settings port
 * is the `:core:testing` fake seeded with the required [SettingsData], and
 * the two search repositories are subclass fakes over the production open
 * seams returning canned payloads.
 */
class SearchToolExecutorImplTest {
    @Test
    fun `execute delegates to Tavily repository when search is enabled and Tavily is selected`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(SettingsData(searchEnabled = true, searchProvider = SearchProvider.TAVILY))
            val tavilySearchRepository = FakeTavilySearchRepository()
            val exaSearchRepository = FakeExaSearchRepository()
            tavilySearchRepository.resultsByQuery =
                mapOf("android 17 local network" to """{"query":"android 17 local network","results":[]}""")

            val executor =
                SearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = settingsRepository,
                    tavilySearchRepository = tavilySearchRepository,
                    exaSearchRepository = exaSearchRepository,
                )

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = "web_search",
                        argumentsJson = """{"query":"android 17 local network"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )

            assertEquals("""{"query":"android 17 local network","results":[]}""", result.resultJson)
        }

    @Test
    fun `execute delegates to Exa repository when search is enabled and Exa is selected`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(SettingsData(searchEnabled = true, searchProvider = SearchProvider.EXA))
            val tavilySearchRepository = FakeTavilySearchRepository()
            val exaSearchRepository = FakeExaSearchRepository()
            exaSearchRepository.resultsByQuery =
                mapOf("android 17 local network" to """{"query":"android 17 local network","results":[]}""")

            val executor =
                SearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = settingsRepository,
                    tavilySearchRepository = tavilySearchRepository,
                    exaSearchRepository = exaSearchRepository,
                )

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = "web_search",
                        argumentsJson = """{"query":"android 17 local network"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )

            assertEquals("""{"query":"android 17 local network","results":[]}""", result.resultJson)
        }

    @Test
    fun `execute throws IllegalStateException when search is disabled`() =
        runTest {
            val settingsRepository = FakeSettingsRepository(SettingsData(searchEnabled = false))

            val executor =
                SearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = settingsRepository,
                    tavilySearchRepository = FakeTavilySearchRepository(),
                    exaSearchRepository = FakeExaSearchRepository(),
                )

            assertFailsWith<IllegalStateException> {
                executor.execute(
                    ToolCallRequest(
                        toolName = "web_search",
                        argumentsJson = """{"query":"test"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )
            }
        }

    @Test
    fun `execute rejects unsupported tool names`() =
        runTest {
            val executor =
                SearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = FakeSettingsRepository(),
                    tavilySearchRepository = FakeTavilySearchRepository(),
                    exaSearchRepository = FakeExaSearchRepository(),
                )
            assertFailsWith<IllegalArgumentException> {
                executor.execute(
                    ToolCallRequest(
                        toolName = "weather_lookup",
                        argumentsJson = """{"query":"Boston"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )
            }
        }

    @Test
    fun `execute rejects missing query arguments`() =
        runTest {
            val executor =
                SearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = FakeSettingsRepository(),
                    tavilySearchRepository = FakeTavilySearchRepository(),
                    exaSearchRepository = FakeExaSearchRepository(),
                )
            assertFailsWith<IllegalArgumentException> {
                executor.execute(
                    ToolCallRequest(
                        toolName = "web_search",
                        argumentsJson = "{}",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )
            }
        }

    @Test
    fun `execute preserves escaped quotes in query arguments`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(SettingsData(searchEnabled = true, searchProvider = SearchProvider.TAVILY))
            val tavilySearchRepository = FakeTavilySearchRepository()
            val exaSearchRepository = FakeExaSearchRepository()
            tavilySearchRepository.resultsByQuery =
                mapOf("""android "agent" news""" to """{"query":"android \"agent\" news","results":[]}""")

            val executor =
                SearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = settingsRepository,
                    tavilySearchRepository = tavilySearchRepository,
                    exaSearchRepository = exaSearchRepository,
                )

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = "web_search",
                        argumentsJson = """{"query":"android \"agent\" news"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )

            assertEquals("""{"query":"android \"agent\" news","results":[]}""", result.resultJson)
        }

    @Test
    fun `execute returns error JSON when Tavily repository throws IOException`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(SettingsData(searchEnabled = true, searchProvider = SearchProvider.TAVILY))
            val tavilySearchRepository = FakeTavilySearchRepository()
            val exaSearchRepository = FakeExaSearchRepository()
            tavilySearchRepository.searchFailure = java.io.IOException("Connection timed out")

            val executor =
                SearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = settingsRepository,
                    tavilySearchRepository = tavilySearchRepository,
                    exaSearchRepository = exaSearchRepository,
                )

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = "web_search",
                        argumentsJson = """{"query":"test"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )

            assertEquals("""{"success":false,"error":"Network error: Connection timed out"}""", result.resultJson)
        }
}
