package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.inference.BuildXQueryUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Translated from MockK to hand-written fakes (ENC-28): the settings port
 * is the `:core:testing` fake seeded with the required [SettingsData], the
 * X search repository is a subclass fake over the production open seam,
 * and the query builder is the real no-arg [BuildXQueryUseCase].
 */
class XSearchToolExecutorImplTest {
    @Test
    fun `execute delegates to XSearchRepository when X Search is enabled`() =
        runTest {
            val settingsRepository = FakeSettingsRepository(SettingsData(xSearchEnabled = true))
            val xSearchRepository = FakeXSearchRepository()
            xSearchRepository.defaultResult = """{"query":"AI news","result_count":1,"posts":[]}"""

            val executor =
                XSearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = settingsRepository,
                    xSearchRepository = xSearchRepository,
                    buildXQueryUseCase = BuildXQueryUseCase(),
                )

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = "search_x_recent",
                        argumentsJson = """{"query":"AI news"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )

            assertEquals("""{"query":"AI news","result_count":1,"posts":[]}""", result.resultJson)
        }

    @Test
    fun `execute throws IllegalStateException when X Search is disabled`() =
        runTest {
            val settingsRepository = FakeSettingsRepository(SettingsData(xSearchEnabled = false))

            val executor =
                XSearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = settingsRepository,
                    xSearchRepository = FakeXSearchRepository(),
                    buildXQueryUseCase = BuildXQueryUseCase(),
                )

            assertFailsWith<IllegalStateException> {
                executor.execute(
                    ToolCallRequest(
                        toolName = "search_x_recent",
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
                XSearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = FakeSettingsRepository(),
                    xSearchRepository = FakeXSearchRepository(),
                    buildXQueryUseCase = BuildXQueryUseCase(),
                )
            assertFailsWith<IllegalArgumentException> {
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
    fun `execute returns error JSON when search throws IOException`() =
        runTest {
            val settingsRepository = FakeSettingsRepository(SettingsData(xSearchEnabled = true))
            val xSearchRepository = FakeXSearchRepository()
            xSearchRepository.searchFailure = java.io.IOException("Connection refused")

            val executor =
                XSearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = settingsRepository,
                    xSearchRepository = xSearchRepository,
                    buildXQueryUseCase = BuildXQueryUseCase(),
                )

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = "search_x_recent",
                        argumentsJson = """{"query":"AI news"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )

            assertEquals("""{"success":false,"error":"Network error: Connection refused"}""", result.resultJson)
        }
}
