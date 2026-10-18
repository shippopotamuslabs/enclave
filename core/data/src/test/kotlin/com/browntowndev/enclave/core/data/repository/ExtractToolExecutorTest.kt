package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceEntity
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.testing.FakeExtractedUrlTracker
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.repository.SettingsData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date
import kotlin.test.assertFailsWith

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ExtractToolExecutorTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var webSearchSourceDao: WebSearchSourceDao
    private lateinit var apiKeyManager: ApiKeyManager

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        webSearchSourceDao = database.webSearchSourceDao()
        apiKeyManager =
            testApiKeyManager(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                "extract_tool_executor_test_prefs",
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    private fun executor(
        settingsRepository: FakeSettingsRepository,
        tavilySearchRepository: TavilySearchRepository,
        exaSearchRepository: ExaSearchRepository,
    ) = ExtractToolExecutorImpl(
        loggingPort = FakeLoggingPort(),
        settingsRepository = settingsRepository,
        tavilySearchRepository = tavilySearchRepository,
        exaSearchRepository = exaSearchRepository,
        webSearchSourceDao = webSearchSourceDao,
        eventBus = ToolExecutionEventBus(),
        extractedUrlTracker = FakeExtractedUrlTracker(),
    )

    private suspend fun seedSource(url: String) {
        val chatId = ChatId("chat-1")
        database.chatDao().upsert(
            ChatEntity(id = chatId, name = "Chat", created = Date(), lastModified = Date(), pinned = false),
        )
        database.messageDao().upsert(
            MessageEntity(
                id = MessageId("msg-1"),
                chatId = chatId,
                role = Role.USER,
                messageState = MessageState.COMPLETE,
                content = "hi",
                createdAt = 1000L,
            ),
        )
        webSearchSourceDao.upsert(
            WebSearchSourceEntity(
                id = "src-1",
                messageId = MessageId("msg-1"),
                title = "Example",
                url = url,
                content = "",
                score = 0.5,
            ),
        )
    }

    @Test
    fun `execute delegates to TavilySearchRepository extract when search is enabled and Tavily is selected`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(SettingsData(searchEnabled = true, searchProvider = SearchProvider.TAVILY))
            val tavilySearchRepository = FakeTavilySearchRepository(apiKeyManager)
            tavilySearchRepository.extractResult = """{"results":[]}"""
            val exaSearchRepository = FakeExaSearchRepository(apiKeyManager)
            seedSource("https://example.com")

            val executor = executor(settingsRepository, tavilySearchRepository, exaSearchRepository)

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
            assertTrue(webSearchSourceDao.getById("src-1")!!.extracted)
        }

    @Test
    fun `execute delegates to ExaSearchRepository extract when search is enabled and Exa is selected`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(SettingsData(searchEnabled = true, searchProvider = SearchProvider.EXA))
            val tavilySearchRepository = FakeTavilySearchRepository(apiKeyManager)
            val exaSearchRepository = FakeExaSearchRepository(apiKeyManager)
            exaSearchRepository.extractResult = """{"results":[]}"""
            seedSource("https://example.com")

            val executor = executor(settingsRepository, tavilySearchRepository, exaSearchRepository)

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
            assertTrue(webSearchSourceDao.getById("src-1")!!.extracted)
        }

    @Test
    fun `execute throws IllegalStateException when search is disabled`() =
        runTest {
            val settingsRepository = FakeSettingsRepository(SettingsData(searchEnabled = false))

            val executor =
                executor(
                    settingsRepository,
                    FakeTavilySearchRepository(apiKeyManager),
                    FakeExaSearchRepository(apiKeyManager),
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

    @Test
    fun `execute returns error JSON when Tavily repository throws IOException`() =
        runTest {
            val settingsRepository =
                FakeSettingsRepository(SettingsData(searchEnabled = true, searchProvider = SearchProvider.TAVILY))
            val tavilySearchRepository = FakeTavilySearchRepository(apiKeyManager)
            tavilySearchRepository.extractFailure = java.io.IOException("DNS resolution failed")
            val exaSearchRepository = FakeExaSearchRepository(apiKeyManager)

            val executor = executor(settingsRepository, tavilySearchRepository, exaSearchRepository)

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = "web_extract",
                        argumentsJson = """{"urls":["https://example.com"]}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )

            assertEquals("""{"success":false,"error":"Network error: DNS resolution failed"}""", result.resultJson)
        }

    /** Scripted [TavilySearchRepository] over the behavior-preserving `open` seam (ENC-28). */
    private class FakeTavilySearchRepository(
        apiKeyManager: ApiKeyManager,
    ) : TavilySearchRepository(OkHttpClient(), apiKeyManager) {
        var extractResult: String = ""
        var extractFailure: Throwable? = null

        override fun extract(
            urls: List<String>,
            extractDepth: String,
            format: String,
        ): String {
            extractFailure?.let { throw it }
            return extractResult
        }
    }

    /** Scripted [ExaSearchRepository] over the behavior-preserving `open` seam (ENC-28). */
    private class FakeExaSearchRepository(
        apiKeyManager: ApiKeyManager,
    ) : ExaSearchRepository(OkHttpClient(), apiKeyManager) {
        var extractResult: String = ""
        var extractFailure: Throwable? = null

        override fun extract(urls: List<String>): String {
            extractFailure?.let { throw it }
            return extractResult
        }
    }
}
