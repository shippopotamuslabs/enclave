package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.artifact.ArtifactToolExecutor
import com.browntowndev.enclave.core.data.artifact.SearchArtifactToolExecutor
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeExtractedUrlTracker
import com.browntowndev.enclave.core.testing.FakeGpuCapability
import com.browntowndev.enclave.core.testing.FakeImageGenerationPort
import com.browntowndev.enclave.core.testing.FakeInferenceFactory
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.FakeMusicGenerationPort
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTelemetryEventPort
import com.browntowndev.enclave.core.testing.FakeUtilityModelFile
import com.browntowndev.enclave.core.testing.FakeVideoGenerationPort
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.docgen.DocumentValidationPort
import com.browntowndev.enclave.domain.usecase.chat.AnalyzeImageUseCase
import com.browntowndev.enclave.domain.usecase.inference.BuildXQueryUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateImageUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateMediaUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateMusicUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateVideoUseCase
import com.browntowndev.enclave.domain.usecase.media.LocalStableDiffusionTargetResolver
import com.browntowndev.enclave.domain.usecase.media.ResolveAssignedImageGenerationTargetUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveAssignedMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaModelTypeUseCase
import com.browntowndev.enclave.domain.usecase.media.SelectLocalImageGenerationBackendUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.IOException
import com.browntowndev.enclave.core.testing.TestClock
import javax.inject.Provider

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class CompositeToolExecutorTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var searchToolExecutor: FakeSearchToolExecutor
    private lateinit var imageInspectToolExecutor: FakeImageInspectToolExecutor
    private lateinit var extractToolExecutor: FakeExtractToolExecutor
    private lateinit var searchChatHistoryToolExecutor: FakeSearchChatHistoryToolExecutor
    private lateinit var searchChatToolExecutor: FakeSearchChatToolExecutor
    private lateinit var getMessageContextToolExecutor: FakeGetMessageContextToolExecutor
    private lateinit var manageMemoriesToolExecutor: FakeManageMemoriesToolExecutor
    private lateinit var artifactToolExecutor: FakeArtifactToolExecutor
    private lateinit var searchArtifactToolExecutor: FakeSearchArtifactToolExecutor
    private lateinit var xSearchToolExecutor: FakeXSearchToolExecutor
    private lateinit var xGetThreadToolExecutor: FakeXGetThreadToolExecutor
    private lateinit var xTrendingToolExecutor: FakeXTrendingToolExecutor
    private lateinit var xUserTweetsToolExecutor: FakeXUserTweetsToolExecutor
    private lateinit var xMentionSearchToolExecutor: FakeXMentionSearchToolExecutor
    private lateinit var mediaToolExecutor: FakeMediaToolExecutor
    private lateinit var eventBus: ToolExecutionEventBus
    private lateinit var compositeToolExecutor: CompositeToolExecutor

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val apiKeyManager =
            testApiKeyManager(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                "composite_tool_executor_test_prefs",
            )
        val deps = InertDeps(database, apiKeyManager)

        searchToolExecutor = FakeSearchToolExecutor(deps)
        imageInspectToolExecutor = FakeImageInspectToolExecutor(deps)
        extractToolExecutor = FakeExtractToolExecutor(deps)
        searchChatHistoryToolExecutor = FakeSearchChatHistoryToolExecutor(deps)
        searchChatToolExecutor = FakeSearchChatToolExecutor(deps)
        getMessageContextToolExecutor = FakeGetMessageContextToolExecutor(deps)
        manageMemoriesToolExecutor = FakeManageMemoriesToolExecutor(deps)
        artifactToolExecutor = FakeArtifactToolExecutor(deps)
        searchArtifactToolExecutor = FakeSearchArtifactToolExecutor(deps)
        xSearchToolExecutor = FakeXSearchToolExecutor(deps)
        xGetThreadToolExecutor = FakeXGetThreadToolExecutor(deps)
        xTrendingToolExecutor = FakeXTrendingToolExecutor(deps)
        xUserTweetsToolExecutor = FakeXUserTweetsToolExecutor(deps)
        xMentionSearchToolExecutor = FakeXMentionSearchToolExecutor(deps)
        mediaToolExecutor = FakeMediaToolExecutor(deps)
        eventBus = ToolExecutionEventBus()
        compositeToolExecutor =
            CompositeToolExecutor(
                searchToolExecutor = searchToolExecutor,
                imageInspectToolExecutor = imageInspectToolExecutor,
                extractToolExecutor = extractToolExecutor,
                searchChatHistoryToolExecutor = searchChatHistoryToolExecutor,
                searchChatToolExecutor = searchChatToolExecutor,
                getMessageContextToolExecutor = getMessageContextToolExecutor,
                manageMemoriesToolExecutor = manageMemoriesToolExecutor,
                artifactToolExecutor = artifactToolExecutor,
                searchArtifactToolExecutor = searchArtifactToolExecutor,
                xSearchToolExecutor = xSearchToolExecutor,
                xGetThreadToolExecutor = xGetThreadToolExecutor,
                xTrendingToolExecutor = xTrendingToolExecutor,
                xUserTweetsToolExecutor = xUserTweetsToolExecutor,
                xMentionSearchToolExecutor = xMentionSearchToolExecutor,
                mediaToolExecutor = mediaToolExecutor,
                eventBus = eventBus,
                telemetryEventPort = FakeTelemetryEventPort(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `execute emits started and finished events for successful execution`() =
        runTest {
            // Given
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-123"),
                    userMessageId = MessageId("msg-123"),
                    toolName = ToolDefinition.WEB_SEARCH.name,
                    argumentsJson = "{\"query\": \"test\"}",
                    provider = "tavily",
                    modelType = ModelType.FAST,
                )
            val expectedResult = ToolExecutionResult(toolName = request.toolName, resultJson = "{\"status\": \"ok\"}")
            searchToolExecutor.script.nextResult = expectedResult

            // When
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { eventBus.events.take(2).toList() }
            compositeToolExecutor.execute(request)
            val events = eventsDeferred.await()

            // Then: Started first, then Finished without error, in order
            val started = events[0] as ToolExecutionEvent.Started
            assertEquals(request.toolName, started.toolName)
            assertEquals(listOf(request), searchToolExecutor.script.calls)
            val finished = events[1] as ToolExecutionEvent.Finished
            assertNull(finished.error)
        }

    @Test
    fun `execute emits started and finished with error for failed execution`() =
        runTest {
            // Given
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-123"),
                    userMessageId = MessageId("msg-123"),
                    toolName = ToolDefinition.WEB_SEARCH.name,
                    argumentsJson = "{\"query\": \"test\"}",
                    provider = "tavily",
                    modelType = ModelType.FAST,
                )
            val errorMessage = "API error"
            searchToolExecutor.script.nextFailure = IOException(errorMessage)

            // When
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { eventBus.events.take(2).toList() }
            try {
                compositeToolExecutor.execute(request)
            } catch (e: Exception) {
                // Expected
            }
            val events = eventsDeferred.await()

            // Then: Started first, then Finished with the error, in order
            val started = events[0] as ToolExecutionEvent.Started
            assertEquals(request.toolName, started.toolName)
            assertEquals(listOf(request), searchToolExecutor.script.calls)
            val finished = events[1] as ToolExecutionEvent.Finished
            assertEquals(errorMessage, finished.error)
        }

    @Test
    fun `execute routes tavily_extract to extractToolExecutor`() =
        runTest {
            // Given
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-456"),
                    userMessageId = MessageId("msg-456"),
                    toolName = ToolDefinition.WEB_EXTRACT.name,
                    argumentsJson = "{\"urls\":[\"https://example.com\"]}",
                    provider = "tavily",
                    modelType = ModelType.FAST,
                )
            val expectedResult = ToolExecutionResult(toolName = request.toolName, resultJson = "{\"status\": \"ok\"}")
            extractToolExecutor.script.nextResult = expectedResult

            // When
            val result = compositeToolExecutor.execute(request)

            // Then
            assertEquals(listOf(request), extractToolExecutor.script.calls)
            assertEquals(expectedResult, result)
        }

    @Test
    fun `execute emits started and finished for tavily_extract`() =
        runTest {
            // Given
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-789"),
                    userMessageId = MessageId("msg-789"),
                    toolName = ToolDefinition.WEB_EXTRACT.name,
                    argumentsJson = "{\"urls\":[\"https://example.com\"]}",
                    provider = "tavily",
                    modelType = ModelType.FAST,
                )
            val expectedResult = ToolExecutionResult(toolName = request.toolName, resultJson = "{\"status\": \"ok\"}")
            extractToolExecutor.script.nextResult = expectedResult

            // When
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { eventBus.events.take(2).toList() }
            compositeToolExecutor.execute(request)
            val events = eventsDeferred.await()

            // Then
            val started = events[0] as ToolExecutionEvent.Started
            assertEquals(request.toolName, started.toolName)
            assertEquals(listOf(request), extractToolExecutor.script.calls)
            val finished = events[1] as ToolExecutionEvent.Finished
            assertNull(finished.error)
        }

    @Test
    fun `execute routes search_x_recent to xSearchToolExecutor`() =
        runTest {
            // Given
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-xyz"),
                    userMessageId = MessageId("msg-xyz"),
                    toolName = ToolDefinition.X_SEARCH.name,
                    argumentsJson = "{\"query\":\"test\"}",
                    provider = "x",
                    modelType = ModelType.FAST,
                )
            val expectedResult = ToolExecutionResult(toolName = request.toolName, resultJson = "{\"status\": \"success\"}")
            xSearchToolExecutor.script.nextResult = expectedResult

            // When
            val result = compositeToolExecutor.execute(request)

            // Then
            assertEquals(listOf(request), xSearchToolExecutor.script.calls)
            assertEquals(expectedResult, result)
        }

    @Test
    fun `execute routes generate_image to mediaToolExecutor`() =
        runTest {
            // Given
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-media"),
                    userMessageId = MessageId("msg-media"),
                    toolName = ToolDefinition.GENERATE_IMAGE.name,
                    argumentsJson = "{\"prompt\":\"test\"}",
                    provider = "replicate",
                    modelType = ModelType.FAST,
                )
            val expectedResult = ToolExecutionResult(toolName = request.toolName, resultJson = "{\"status\": \"success\"}")
            mediaToolExecutor.script.nextResult = expectedResult

            // When
            val result = compositeToolExecutor.execute(request)

            // Then
            assertEquals(listOf(request), mediaToolExecutor.script.calls)
            assertEquals(expectedResult, result)
        }

    @Test
    fun `all tools in ToolDefinition ALL_TOOLS are supported`() =
        runTest {
            // Setup all executor fakes to handle any request
            val scripts =
                listOf(
                    searchToolExecutor.script,
                    extractToolExecutor.script,
                    imageInspectToolExecutor.script,
                    searchChatHistoryToolExecutor.script,
                    searchChatToolExecutor.script,
                    getMessageContextToolExecutor.script,
                    manageMemoriesToolExecutor.script,
                    artifactToolExecutor.script,
                    xSearchToolExecutor.script,
                    xGetThreadToolExecutor.script,
                    xTrendingToolExecutor.script,
                    xUserTweetsToolExecutor.script,
                    xMentionSearchToolExecutor.script,
                    mediaToolExecutor.script,
                )
            scripts.forEach {
                it.nextResult = ToolExecutionResult("any", "{}")
            }

            ToolDefinition.ALL_TOOLS.forEach { tool ->
                val request =
                    ToolCallRequest(
                        toolName = tool.name,
                        argumentsJson = "{}",
                        provider = "test",
                        modelType = ModelType.FAST,
                    )

                try {
                    compositeToolExecutor.execute(request)
                } catch (e: IllegalArgumentException) {
                    if (e.message?.startsWith("Unsupported tool") == true) {
                        org.junit.jupiter.api.Assertions.fail<Unit>(
                            "Tool ${tool.name} is defined in ToolDefinition.ALL_TOOLS but not supported by CompositeToolExecutor",
                        )
                    }
                    // Other IllegalArgumentExceptions are okay (e.g. from ToolCallRequest decoding)
                } catch (e: Exception) {
                    // Other exceptions are fine, they mean the routing worked but execution failed (expected)
                }
            }
        }

    @Test
    fun `SCENARIO_COMPOSITE_EXECUTOR_ROUTING`() =
        runTest {
            val getThreadRequest =
                ToolCallRequest(toolName = "x_get_thread", argumentsJson = "{}", provider = "x", modelType = ModelType.FAST)
            val trendingRequest = ToolCallRequest(toolName = "x_trending", argumentsJson = "{}", provider = "x", modelType = ModelType.FAST)
            val userTweetsRequest =
                ToolCallRequest(toolName = "x_user_tweets", argumentsJson = "{}", provider = "x", modelType = ModelType.FAST)
            val mentionSearchRequest =
                ToolCallRequest(toolName = "x_mention_search", argumentsJson = "{}", provider = "x", modelType = ModelType.FAST)

            xGetThreadToolExecutor.script.nextResult = ToolExecutionResult("x_get_thread", "thread_result")
            xTrendingToolExecutor.script.nextResult = ToolExecutionResult("x_trending", "trending_result")
            xUserTweetsToolExecutor.script.nextResult = ToolExecutionResult("x_user_tweets", "user_tweets_result")
            xMentionSearchToolExecutor.script.nextResult = ToolExecutionResult("x_mention_search", "mention_search_result")

            assertEquals("thread_result", compositeToolExecutor.execute(getThreadRequest).resultJson)
            assertEquals("trending_result", compositeToolExecutor.execute(trendingRequest).resultJson)
            assertEquals("user_tweets_result", compositeToolExecutor.execute(userTweetsRequest).resultJson)
            assertEquals("mention_search_result", compositeToolExecutor.execute(mentionSearchRequest).resultJson)
        }

    /** Scripted behavior shared by every recording executor fake below. */
    private class ExecutorScript {
        val calls = mutableListOf<ToolCallRequest>()
        var nextResult: ToolExecutionResult = ToolExecutionResult("any", "{}")
        var nextFailure: Throwable? = null

        fun run(request: ToolCallRequest): ToolExecutionResult {
            calls += request
            nextFailure?.let { throw it }
            return nextResult
        }
    }

    /** Inert real collaborators used only to satisfy the executor fakes' super constructors. */
    private class InertDeps(
        database: EnclaveDatabase,
        apiKeyManager: ApiKeyManager,
    ) {
        val loggingPort = FakeLoggingPort()
        val settingsRepository = FakeSettingsRepository()
        val messageRepository = FakeMessageRepository()
        val tavilySearchRepository = TavilySearchRepository(OkHttpClient(), apiKeyManager)
        val exaSearchRepository = ExaSearchRepository(OkHttpClient(), apiKeyManager)
        val xSearchRepository =
            XSearchRepository(OkHttpClient(), apiKeyManager, XUserTimelineFetcher(OkHttpClient(), apiKeyManager))
        val webSearchSourceDao = database.webSearchSourceDao()
        val documentDao = database.documentDao()
        val embeddingDao = database.embeddingDao()
        val toolExecutionEventBus = ToolExecutionEventBus()
        val analyzeImageUseCase =
            AnalyzeImageUseCase(
                inferenceFactoryProvider = Provider { FakeInferenceFactory() },
                loggingPort = FakeLoggingPort(),
            )
        val generateMediaUseCase: GenerateMediaUseCase

        init {
            val defaultModelRepository = FakeDefaultModelRepository()
            val mediaProviderRepository = FakeMediaProviderRepository()
            val resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase()
            val resolveAssignedImageGenerationTargetUseCase =
                ResolveAssignedImageGenerationTargetUseCase(
                    defaultModelRepository = defaultModelRepository,
                    mediaProviderRepository = mediaProviderRepository,
                    localStableDiffusionTargetResolver =
                        LocalStableDiffusionTargetResolver(
                            localModelRepository = FakeLocalModelRepository(),
                            utilityModelFilePort = FakeUtilityModelFile(),
                            gpuCapabilityPort = FakeGpuCapability(),
                            capabilityPort = FakeLocalImageGenerationCapability(),
                            selectBackend = SelectLocalImageGenerationBackendUseCase(),
                        ),
                )
            generateMediaUseCase =
                GenerateMediaUseCase(
                    generateImageUseCase =
                        GenerateImageUseCase(
                            resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                            resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                            imageGenerationPort = FakeImageGenerationPort(),
                        ),
                    generateVideoUseCase =
                        GenerateVideoUseCase(
                            resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                            resolveAssignedMediaProviderUseCase =
                                ResolveAssignedMediaProviderUseCase(
                                    defaultModelRepository = defaultModelRepository,
                                    mediaProviderRepository = mediaProviderRepository,
                                ),
                            videoGenerationPort = FakeVideoGenerationPort(),
                        ),
                    generateMusicUseCase =
                        GenerateMusicUseCase(
                            defaultModelRepository = defaultModelRepository,
                            mediaProviderRepository = mediaProviderRepository,
                            musicGenerationPort = FakeMusicGenerationPort(),
                        ),
                    resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                    resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                    studioRepository = FakeStudioRepository(),
                    clock = TestClock(),
                )
        }
    }

    private class PassThroughDocumentValidation : DocumentValidationPort {
        override fun validate(json: String): Result<Unit> = Result.success(Unit)

        override fun validateStartArtifactArgs(json: String): Result<Unit> = Result.success(Unit)

        override fun validateAppendBlocksArgs(json: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeSearchToolExecutor(
        deps: InertDeps,
    ) : SearchToolExecutorImpl(
            loggingPort = deps.loggingPort,
            settingsRepository = deps.settingsRepository,
            tavilySearchRepository = deps.tavilySearchRepository,
            exaSearchRepository = deps.exaSearchRepository,
            telemetryEventPort = null,
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeExtractToolExecutor(
        deps: InertDeps,
    ) : ExtractToolExecutorImpl(
            loggingPort = deps.loggingPort,
            settingsRepository = deps.settingsRepository,
            tavilySearchRepository = deps.tavilySearchRepository,
            exaSearchRepository = deps.exaSearchRepository,
            webSearchSourceDao = deps.webSearchSourceDao,
            eventBus = deps.toolExecutionEventBus,
            extractedUrlTracker = FakeExtractedUrlTracker(),
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeImageInspectToolExecutor(
        deps: InertDeps,
    ) : ImageInspectToolExecutor(
            loggingPort = deps.loggingPort,
            messageRepository = deps.messageRepository,
            analyzeImageUseCase = deps.analyzeImageUseCase,
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeSearchChatHistoryToolExecutor(
        deps: InertDeps,
    ) : SearchChatHistoryToolExecutor(
            loggingPort = deps.loggingPort,
            messageRepository = deps.messageRepository,
            chatRepository = FakeChatRepository(),
            embeddingEngine = FakeEmbeddingEngine(),
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeSearchChatToolExecutor(
        deps: InertDeps,
    ) : SearchChatToolExecutor(
            loggingPort = deps.loggingPort,
            messageRepository = deps.messageRepository,
            embeddingEngine = FakeEmbeddingEngine(),
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeGetMessageContextToolExecutor(
        deps: InertDeps,
    ) : GetMessageContextToolExecutor(
            loggingPort = deps.loggingPort,
            messageRepository = deps.messageRepository,
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeManageMemoriesToolExecutor(
        deps: InertDeps,
    ) : ManageMemoriesToolExecutor(
            memoriesRepository = FakeMemoriesRepository(),
            embeddingEnginePort = FakeEmbeddingEngine(),
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeArtifactToolExecutor(
        deps: InertDeps,
    ) : ArtifactToolExecutor(
            documentDao = deps.documentDao,
            embeddingDao = deps.embeddingDao,
            embeddingEngine = FakeEmbeddingEngine(),
            documentValidation = PassThroughDocumentValidation(),
            localEncryption = FakeLocalDataEncryptionService(),
            externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeSearchArtifactToolExecutor(
        deps: InertDeps,
    ) : SearchArtifactToolExecutor(
            documentDao = deps.documentDao,
            embeddingEngine = FakeEmbeddingEngine(),
            localEncryption = FakeLocalDataEncryptionService(),
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeXSearchToolExecutor(
        deps: InertDeps,
    ) : XSearchToolExecutorImpl(
            loggingPort = deps.loggingPort,
            settingsRepository = deps.settingsRepository,
            xSearchRepository = deps.xSearchRepository,
            buildXQueryUseCase = BuildXQueryUseCase(),
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeXGetThreadToolExecutor(
        deps: InertDeps,
    ) : XGetThreadToolExecutor(
            loggingPort = deps.loggingPort,
            settingsRepository = deps.settingsRepository,
            xSearchRepository = deps.xSearchRepository,
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeXTrendingToolExecutor(
        deps: InertDeps,
    ) : XTrendingToolExecutor(
            loggingPort = deps.loggingPort,
            settingsRepository = deps.settingsRepository,
            xSearchRepository = deps.xSearchRepository,
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeXUserTweetsToolExecutor(
        deps: InertDeps,
    ) : XUserTweetsToolExecutor(
            loggingPort = deps.loggingPort,
            settingsRepository = deps.settingsRepository,
            xSearchRepository = deps.xSearchRepository,
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeXMentionSearchToolExecutor(
        deps: InertDeps,
    ) : XMentionSearchToolExecutor(
            loggingPort = deps.loggingPort,
            settingsRepository = deps.settingsRepository,
            xSearchRepository = deps.xSearchRepository,
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }

    private class FakeMediaToolExecutor(
        deps: InertDeps,
    ) : MediaToolExecutor(
            generateMediaUseCase = deps.generateMediaUseCase,
            loggingPort = deps.loggingPort,
        ) {
        val script = ExecutorScript()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = script.run(request)
    }
}
