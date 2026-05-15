package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import com.browntowndev.pocketcrew.domain.usecase.inference.BuildXQueryUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class XSearchToolExecutorImplTest {
    @Test
    fun `execute delegates to XSearchRepository when X Search is enabled`() =
        runTest {
            val settingsRepository = mockk<SettingsRepository>()
            val xSearchRepository = mockk<XSearchRepository>()
            val buildXQueryUseCase = mockk<BuildXQueryUseCase>()

            every { settingsRepository.settingsFlow } returns flowOf(SettingsData(xSearchEnabled = true))
            every { buildXQueryUseCase(any()) } returns "AI news"
            coEvery { xSearchRepository.search("AI news", any()) } returns """{"query":"AI news","result_count":1,"posts":[]}"""

            val executor =
                XSearchToolExecutorImpl(
                    loggingPort = mockk<LoggingPort>(relaxed = true),
                    settingsRepository = settingsRepository,
                    xSearchRepository = xSearchRepository,
                    buildXQueryUseCase = buildXQueryUseCase,
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
            val settingsRepository = mockk<SettingsRepository>()
            val xSearchRepository = mockk<XSearchRepository>()
            val buildXQueryUseCase = mockk<BuildXQueryUseCase>()

            every { settingsRepository.settingsFlow } returns flowOf(SettingsData(xSearchEnabled = false))
            every { buildXQueryUseCase(any()) } returns "test"

            val executor =
                XSearchToolExecutorImpl(
                    loggingPort = mockk<LoggingPort>(relaxed = true),
                    settingsRepository = settingsRepository,
                    xSearchRepository = xSearchRepository,
                    buildXQueryUseCase = buildXQueryUseCase,
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
                    loggingPort = mockk<LoggingPort>(relaxed = true),
                    settingsRepository = mockk(),
                    xSearchRepository = mockk(),
                    buildXQueryUseCase = mockk(),
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
}
