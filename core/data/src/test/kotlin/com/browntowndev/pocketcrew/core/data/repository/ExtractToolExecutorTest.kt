package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.TavilySourceDao
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ExtractToolExecutorTest {

    @Test
    fun `execute delegates to TavilySearchRepository extract when search is enabled`() = runTest {
        val settingsRepository = mockk<SettingsRepository>()
        val tavilySearchRepository = mockk<TavilySearchRepository>()
        val tavilySourceDao = mockk<TavilySourceDao>(relaxed = true)
        val eventBus = mockk<ToolExecutionEventBus>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(SettingsData(searchEnabled = true))
        coEvery {
            tavilySearchRepository.extract(
                urls = listOf("https://example.com"),
                extractDepth = "basic",
                format = "markdown",
            )
        } returns """{"results":[{"url":"https://example.com","raw_content":"Content"}]}"""

        val executor = ExtractToolExecutorImpl(
            loggingPort = mockk<LoggingPort>(relaxed = true),
            settingsRepository = settingsRepository,
            tavilySearchRepository = tavilySearchRepository,
            tavilySourceDao = tavilySourceDao,
            eventBus = eventBus,
        )

        val result = executor.execute(
            ToolCallRequest(
                toolName = ToolDefinition.TAVILY_EXTRACT.name,
                argumentsJson = """{"urls":["https://example.com"]}""",
                provider = "OPENAI",
                modelType = ModelType.FAST,
            )
        )

        assertTrue(result.resultJson.contains("https://example.com"))
        assertEquals(ToolDefinition.TAVILY_EXTRACT.name, result.toolName)
    }

    @Test
    fun `execute emits Extracting event per URL`() = runTest {
        val settingsRepository = mockk<SettingsRepository>()
        val tavilySearchRepository = mockk<TavilySearchRepository>()
        val tavilySourceDao = mockk<TavilySourceDao>(relaxed = true)
        val eventBus = mockk<ToolExecutionEventBus>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(SettingsData(searchEnabled = true))
        coEvery {
            tavilySearchRepository.extract(
                urls = any(),
                extractDepth = any(),
                format = any(),
            )
        } returns """{"results":[]}"""

        val eventSlot = mutableListOf<ToolExecutionEvent>()
        every { eventBus.emit(capture(eventSlot)) } answers { }

        val executor = ExtractToolExecutorImpl(
            loggingPort = mockk<LoggingPort>(relaxed = true),
            settingsRepository = settingsRepository,
            tavilySearchRepository = tavilySearchRepository,
            tavilySourceDao = tavilySourceDao,
            eventBus = eventBus,
        )

        executor.execute(
            ToolCallRequest(
                toolName = ToolDefinition.TAVILY_EXTRACT.name,
                argumentsJson = """{"urls":["https://a.com","https://b.com"]}""",
                provider = "OPENAI",
                modelType = ModelType.FAST,
                chatId = ChatId("chat-1"),
                userMessageId = MessageId("msg-1"),
            )
        )

        val extractingEvents = eventSlot.filterIsInstance<ToolExecutionEvent.Extracting>()
        assertEquals(2, extractingEvents.size, "Should emit one Extracting event per URL")
        assertEquals("https://a.com", extractingEvents[0].url)
        assertEquals("https://b.com", extractingEvents[1].url)
    }

    @Test
    fun `execute marks sources as extracted in DAO`() = runTest {
        val settingsRepository = mockk<SettingsRepository>()
        val tavilySearchRepository = mockk<TavilySearchRepository>()
        val tavilySourceDao = mockk<TavilySourceDao>(relaxed = true)
        val eventBus = mockk<ToolExecutionEventBus>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(SettingsData(searchEnabled = true))
        coEvery {
            tavilySearchRepository.extract(
                urls = any(),
                extractDepth = any(),
                format = any(),
            )
        } returns """{"results":[]}"""

        val executor = ExtractToolExecutorImpl(
            loggingPort = mockk<LoggingPort>(relaxed = true),
            settingsRepository = settingsRepository,
            tavilySearchRepository = tavilySearchRepository,
            tavilySourceDao = tavilySourceDao,
            eventBus = eventBus,
        )

        executor.execute(
            ToolCallRequest(
                toolName = ToolDefinition.TAVILY_EXTRACT.name,
                argumentsJson = """{"urls":["https://a.com","https://b.com"]}""",
                provider = "OPENAI",
                modelType = ModelType.FAST,
                chatId = ChatId("chat-1"),
                userMessageId = MessageId("msg-1"),
            )
        )

        coVerify { tavilySourceDao.markExtracted("https://a.com") }
        coVerify { tavilySourceDao.markExtracted("https://b.com") }
    }

    @Test
    fun `execute throws IllegalStateException when search is disabled`() = runTest {
        val settingsRepository = mockk<SettingsRepository>()
        val tavilySearchRepository = mockk<TavilySearchRepository>()
        val tavilySourceDao = mockk<TavilySourceDao>(relaxed = true)
        val eventBus = mockk<ToolExecutionEventBus>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(SettingsData(searchEnabled = false))

        val executor = ExtractToolExecutorImpl(
            loggingPort = mockk<LoggingPort>(relaxed = true),
            settingsRepository = settingsRepository,
            tavilySearchRepository = tavilySearchRepository,
            tavilySourceDao = tavilySourceDao,
            eventBus = eventBus,
        )

        assertFailsWith<IllegalStateException> {
            executor.execute(
                ToolCallRequest(
                    toolName = ToolDefinition.TAVILY_EXTRACT.name,
                    argumentsJson = """{"urls":["https://example.com"]}""",
                    provider = "OPENAI",
                    modelType = ModelType.FAST,
                )
            )
        }
    }

    @Test
    fun `execute rejects unsupported tool names`() = runTest {
        val executor = ExtractToolExecutorImpl(
            loggingPort = mockk<LoggingPort>(relaxed = true),
            settingsRepository = mockk(),
            tavilySearchRepository = mockk(),
            tavilySourceDao = mockk(),
            eventBus = mockk(),
        )

        assertFailsWith<IllegalArgumentException> {
            executor.execute(
                ToolCallRequest(
                    toolName = "weather_lookup",
                    argumentsJson = """{"urls":["https://example.com"]}""",
                    provider = "OPENAI",
                    modelType = ModelType.FAST,
                )
            )
        }
    }

    @Test
    fun `execute rejects missing urls argument`() = runTest {
        val executor = ExtractToolExecutorImpl(
            loggingPort = mockk<LoggingPort>(relaxed = true),
            settingsRepository = mockk(),
            tavilySearchRepository = mockk(),
            tavilySourceDao = mockk(),
            eventBus = mockk(),
        )

        assertFailsWith<IllegalArgumentException> {
            executor.execute(
                ToolCallRequest(
                    toolName = ToolDefinition.TAVILY_EXTRACT.name,
                    argumentsJson = "{}",
                    provider = "OPENAI",
                    modelType = ModelType.FAST,
                )
            )
        }
    }

    @Test
    fun `execute defaults extract_depth to basic and format to markdown`() = runTest {
        val settingsRepository = mockk<SettingsRepository>()
        val tavilySearchRepository = mockk<TavilySearchRepository>()
        val tavilySourceDao = mockk<TavilySourceDao>(relaxed = true)
        val eventBus = mockk<ToolExecutionEventBus>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(SettingsData(searchEnabled = true))
        coEvery {
            tavilySearchRepository.extract(
                urls = any(),
                extractDepth = "basic",
                format = "markdown",
            )
        } returns """{"results":[]}"""

        val executor = ExtractToolExecutorImpl(
            loggingPort = mockk<LoggingPort>(relaxed = true),
            settingsRepository = settingsRepository,
            tavilySearchRepository = tavilySearchRepository,
            tavilySourceDao = tavilySourceDao,
            eventBus = eventBus,
        )

        executor.execute(
            ToolCallRequest(
                toolName = ToolDefinition.TAVILY_EXTRACT.name,
                argumentsJson = """{"urls":["https://example.com"]}""",
                provider = "OPENAI",
                modelType = ModelType.FAST,
            )
        )

        coVerify {
            tavilySearchRepository.extract(
                urls = listOf("https://example.com"),
                extractDepth = "basic",
                format = "markdown",
            )
        }
    }
}