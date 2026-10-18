package com.browntowndev.enclave.feature.inference

import android.content.Context
import com.anthropic.client.AnthropicClient
import com.browntowndev.enclave.core.testing.FakeApiModelCatalogPort
import com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.feature.inference.fal.FalOutputSchemaResolver
import com.browntowndev.enclave.feature.inference.fal.FalService
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTurnTiming
import com.browntowndev.enclave.feature.inference.telemetry.ProviderCostEstimator
import com.browntowndev.enclave.feature.inference.telemetry.ProviderTelemetryNormalizer
import com.browntowndev.enclave.feature.inference.telemetry.SavedApiModelPricingEstimator
import com.browntowndev.enclave.feature.inference.telemetry.TelemetryClientBuildProvider
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.openai.client.OpenAIClient
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

/**
 * Hand-written fakes for feature/inference tests (ENC-28). Owned ports and
 * owned concrete collaborators live here; raw third-party SDK types
 * (OpenAI/Anthropic/Google genai/OkHttp) stay MockK in the files that need
 * them under a ForbiddenMockKInTests suppression, per contracts.
 */

/** Fake for the owned [GoogleGenAiSdkClient] interface. */
class FakeGoogleGenAiSdkClient : GoogleGenAiSdkClient {
    var streamEvents: List<InferenceEvent> = emptyList()
    var streamResult: GoogleSdkResult =
        GoogleSdkResult(emittedAny = false, functionCall = null, assistantContent = null, finishReason = null)
    var streamFailure: Throwable? = null
    var createCachedContentResult: String = "cached-content"

    /** When non-empty, successive createCachedContent calls pop results in order. */
    val createCachedContentResultQueue = ArrayDeque<String>()

    /** Full-argument records of generateContentStream calls (PromptCaching assertions). */
    data class GenerateRequest(
        val modelId: String,
        val contents: List<Content>,
        val config: GenerateContentConfig,
        val allowToolCall: Boolean,
    )

    val generateRequests = mutableListOf<GenerateRequest>()

    /** Full-argument records of createCachedContent calls (PromptCaching assertions). */
    data class CreateCachedContentRequest(
        val modelId: String,
        val contents: List<Content>,
        val systemInstruction: Content?,
        val ttlSeconds: Long,
    )

    val createCachedContentRequests = mutableListOf<CreateCachedContentRequest>()

    /** Queued results for successive createCachedContent calls (PC_06 sequence). */
    val createCachedContentResults = ArrayDeque<String>()
    val generateCalls = mutableListOf<String>()
    val generateContentsHistory = mutableListOf<List<Content>>()
    val generateConfigsHistory = mutableListOf<GenerateContentConfig>()
    val createCachedContentCalls = mutableListOf<String>()
    val createCachedContentTtls = mutableListOf<Long>()
    val deleteCachedContentCalls = mutableListOf<String>()

    /** Contents argument of the most recent generateContentStream call. */
    val lastGenerateContents: List<Content>? get() = generateContentsHistory.lastOrNull()

    /** Config argument of the most recent generateContentStream call. */
    val lastGenerateConfig: GenerateContentConfig? get() = generateConfigsHistory.lastOrNull()

    override suspend fun generateContentStream(
        modelId: String,
        contents: List<Content>,
        config: GenerateContentConfig,
        allowToolCall: Boolean,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): GoogleSdkResult {
        generateCalls += modelId
        generateContentsHistory += contents
        generateConfigsHistory += config
        generateRequests += GenerateRequest(modelId, contents, config, allowToolCall)
        streamFailure?.let { throw it }
        streamEvents.forEach { emitEvent(it) }
        return streamResult
    }

    override suspend fun createCachedContent(
        modelId: String,
        contents: List<Content>,
        systemInstruction: Content?,
        tools: List<com.google.genai.types.Tool>?,
        ttlSeconds: Long,
    ): String {
        createCachedContentCalls += modelId
        createCachedContentTtls += ttlSeconds
        createCachedContentRequests += CreateCachedContentRequest(modelId, contents, systemInstruction, ttlSeconds)
        return if (createCachedContentResults.isNotEmpty()) {
            createCachedContentResults.removeFirst()
        } else if (createCachedContentResultQueue.isNotEmpty()) {
            createCachedContentResultQueue.removeFirst()
        } else {
            createCachedContentResult
        }
    }

    override suspend fun deleteCachedContent(name: String) {
        deleteCachedContentCalls += name
    }
}

/** Recording subclass fake for the owned [FalService]. */
open class FakeFalService : FalService(OkHttpClient()) {
    var runSyncResult: JsonObject? = null
    var submitQueueResult: com.browntowndev.enclave.feature.inference.fal.FalRequest =
        com.browntowndev.enclave.feature.inference.fal.FalRequest(
            requestId = "request-1",
            statusUrl = "https://queue.test/status",
            responseUrl = "https://queue.test/result",
            cancelUrl = null,
        )
    var awaitStatusStreamResult: JsonObject? = null
    var statusResult: JsonObject? = null
    var resultResult: JsonObject? = null
    var downloadBytesResult: ByteArray = ByteArray(0)
    var resolveMediaBytesResult: ByteArray = ByteArray(0)
    var failure: Throwable? = null
    val runSyncCalls = mutableListOf<String>()
    val submitQueueCalls = mutableListOf<String>()
    val statusCalls = mutableListOf<String>()
    val resultCalls = mutableListOf<String>()
    val cancelCalls = mutableListOf<String>()
    val downloadBytesCalls = mutableListOf<String>()

    override fun runSync(apiKey: String, modelId: String, body: JsonObject, baseUrl: String?): JsonObject {
        runSyncCalls += modelId
        failure?.let { throw it }
        return runSyncResult ?: error("FakeFalService.runSyncResult not set")
    }

    override fun submitQueue(
        apiKey: String,
        modelId: String,
        body: JsonObject,
        baseUrl: String?,
    ): com.browntowndev.enclave.feature.inference.fal.FalRequest {
        submitQueueCalls += modelId
        failure?.let { throw it }
        return submitQueueResult
    }

    override fun awaitStatusStream(apiKey: String, statusUrl: String): JsonObject {
        failure?.let { throw it }
        return awaitStatusStreamResult ?: error("FakeFalService.awaitStatusStreamResult not set")
    }

    override fun status(apiKey: String, statusUrl: String): JsonObject {
        statusCalls += statusUrl
        failure?.let { throw it }
        return statusResult ?: error("FakeFalService.statusResult not set")
    }

    override fun result(apiKey: String, responseUrl: String): JsonObject {
        resultCalls += responseUrl
        failure?.let { throw it }
        return resultResult ?: error("FakeFalService.resultResult not set")
    }

    override fun cancel(apiKey: String, statusUrl: String) {
        cancelCalls += statusUrl
    }

    override fun downloadBytes(url: String, apiKey: String?): ByteArray {
        downloadBytesCalls += url
        failure?.let { throw it }
        return downloadBytesResult
    }

    override fun resolveMediaBytes(url: String, apiKey: String?): ByteArray = resolveMediaBytesResult

    var mediaUrlsOverride: List<String>? = null
    var primaryMediaUrlsOverride: List<String>? = null
    var primaryVideoUrlsOverride: List<String>? = null

    override fun mediaUrls(
        response: JsonObject,
        outputSchema: com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema?,
    ): List<String> = mediaUrlsOverride ?: super.mediaUrls(response, outputSchema)

    override fun primaryMediaUrls(
        response: JsonObject,
        outputSchema: com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema?,
    ): List<String> = primaryMediaUrlsOverride ?: super.primaryMediaUrls(response, outputSchema)

    override fun primaryVideoUrls(
        response: JsonObject,
        outputSchema: com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema?,
    ): List<String> = primaryVideoUrlsOverride ?: super.primaryVideoUrls(response, outputSchema)
}

/** Recording subclass fake for the owned [FalOutputSchemaResolver]. */
class FakeFalOutputSchemaResolver : FalOutputSchemaResolver(FakeApiModelCatalogPort()) {
    var schema: ModelGenerationOutputSchema? = null
    val resolveCalls = mutableListOf<String>()

    override suspend fun resolve(modelId: String, apiKey: String, baseUrl: String?): ModelGenerationOutputSchema? {
        resolveCalls += modelId
        return schema
    }
}

/**
 * Recording subclass fake for [InferenceTelemetrySupport]: every builder is
 * knob-driven so capture tests assert on the support's outputs, not stubs.
 * Construct with a Robolectric application context. Set a result var for a
 * fixed return, or a builder lambda to derive the [TurnTelemetry] from the
 * recorded call arguments (the MockK `answers {}` replacement).
 */
class FakeInferenceTelemetrySupport(context: Context) :
    InferenceTelemetrySupport(
        ProviderTelemetryNormalizer(ProviderCostEstimator(SavedApiModelPricingEstimator())),
        TelemetryClientBuildProvider(context),
    ) {
    data class OpenAiTelemetryCall(
        val context: CaptureContext,
        val providerLabel: String,
        val modelId: String,
        val modelLabel: String,
        val inputTokens: Int?,
        val outputTokens: Int?,
        val reasoningTokens: Int?,
        val cacheReadInputTokens: Int?,
        val cacheWriteInputTokens: Int?,
        val costUsd: java.math.BigDecimal? = null,
        val savedPricing: com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing? = null,
    )

    data class AnthropicTelemetryCall(
        val context: CaptureContext,
        val providerLabel: String,
        val modelId: String,
        val modelLabel: String,
        val inputTokens: Int?,
        val outputTokens: Int?,
        val reasoningTokens: Int?,
        val cacheCreationInputTokens: Int?,
        val cacheReadInputTokens: Int?,
        val savedPricing: com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing? = null,
    )

    data class LocalTelemetryCall(
        val context: CaptureContext,
        val providerLabel: String,
        val modelLabel: String,
        val outputTokens: Int?,
        val inputTokens: Int?,
        val cacheReadInputTokens: Int?,
        val reasoningTokens: Int?,
    )

    var captureContextResult: CaptureContext? = null
    val captureContextCalls = mutableListOf<GenerationOptions>()

    var openAiTelemetryResult: TurnTelemetry? = null
    var openAiTelemetryBuilder: ((OpenAiTelemetryCall) -> TurnTelemetry)? = null
    val openAiCalls = mutableListOf<OpenAiTelemetryCall>()

    var anthropicTelemetryResult: TurnTelemetry? = null
    var anthropicTelemetryBuilder: ((AnthropicTelemetryCall) -> TurnTelemetry)? = null
    val anthropicCalls = mutableListOf<AnthropicTelemetryCall>()

    var localTelemetryResult: TurnTelemetry? = null
    var localTelemetryBuilder: ((LocalTelemetryCall) -> TurnTelemetry)? = null
    val localCalls = mutableListOf<LocalTelemetryCall>()

    val emitCapturedCalls = mutableListOf<Pair<TurnTelemetry?, ModelType>>()

    override fun captureContext(options: GenerationOptions): CaptureContext? {
        captureContextCalls += options
        return captureContextResult
    }

    override suspend fun emitCaptured(
        telemetry: TurnTelemetry?,
        modelType: ModelType,
        emit: suspend (InferenceEvent) -> Unit,
    ) {
        emitCapturedCalls += telemetry to modelType
        if (telemetry != null) emit(InferenceEvent.TelemetryCaptured(telemetry, modelType))
    }

    override fun openAiTurnTelemetry(
        context: CaptureContext,
        providerLabel: String,
        modelId: String,
        modelLabel: String,
        timing: InferenceTurnTiming,
        inputTokens: Int?,
        outputTokens: Int?,
        reasoningTokens: Int?,
        cacheReadInputTokens: Int?,
        cacheWriteInputTokens: Int?,
        costUsd: java.math.BigDecimal?,
        savedPricing: com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing?,
    ): TurnTelemetry {
        val call =
            OpenAiTelemetryCall(
                context = context,
                providerLabel = providerLabel,
                modelId = modelId,
                modelLabel = modelLabel,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                reasoningTokens = reasoningTokens,
                cacheReadInputTokens = cacheReadInputTokens,
                cacheWriteInputTokens = cacheWriteInputTokens,
                costUsd = costUsd,
                savedPricing = savedPricing,
            )
        openAiCalls += call
        return openAiTelemetryBuilder?.invoke(call)
            ?: openAiTelemetryResult
            ?: error("FakeInferenceTelemetrySupport.openAiTelemetryResult not set")
    }

    override fun anthropicTurnTelemetry(
        context: CaptureContext,
        providerLabel: String,
        modelId: String,
        modelLabel: String,
        timing: InferenceTurnTiming,
        inputTokens: Int?,
        outputTokens: Int?,
        reasoningTokens: Int?,
        cacheCreationInputTokens: Int?,
        cacheReadInputTokens: Int?,
        savedPricing: com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing?,
    ): TurnTelemetry {
        val call =
            AnthropicTelemetryCall(
                context = context,
                providerLabel = providerLabel,
                modelId = modelId,
                modelLabel = modelLabel,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                reasoningTokens = reasoningTokens,
                cacheCreationInputTokens = cacheCreationInputTokens,
                cacheReadInputTokens = cacheReadInputTokens,
                savedPricing = savedPricing,
            )
        anthropicCalls += call
        return anthropicTelemetryBuilder?.invoke(call)
            ?: anthropicTelemetryResult
            ?: error("FakeInferenceTelemetrySupport.anthropicTelemetryResult not set")
    }

    override fun localTurnTelemetry(
        context: CaptureContext,
        providerLabel: String,
        modelLabel: String,
        timing: InferenceTurnTiming,
        outputTokens: Int?,
        inputTokens: Int?,
        cacheReadInputTokens: Int?,
        reasoningTokens: Int?,
    ): TurnTelemetry {
        val call =
            LocalTelemetryCall(
                context = context,
                providerLabel = providerLabel,
                modelLabel = modelLabel,
                outputTokens = outputTokens,
                inputTokens = inputTokens,
                cacheReadInputTokens = cacheReadInputTokens,
                reasoningTokens = reasoningTokens,
            )
        localCalls += call
        return localTelemetryBuilder?.invoke(call)
            ?: localTelemetryResult
            ?: error("FakeInferenceTelemetrySupport.localTelemetryResult not set")
    }
}

/** Provider-port fake: returns whatever SDK client the test installs. */
class FakeOpenAiClientProviderPort : OpenAiClientProviderPort {
    lateinit var client: OpenAIClient
    var getClientCalls = 0

    /** When set, getClient throws this instead of returning [client]. */
    var failure: Throwable? = null

    data class GetClientRequest(
        val apiKey: String,
        val baseUrl: String?,
        val organizationId: String?,
        val projectId: String?,
        val headers: Map<String, String>,
    )

    val getClientRequests = mutableListOf<GetClientRequest>()

    override fun getClient(
        apiKey: String,
        baseUrl: String?,
        organizationId: String?,
        projectId: String?,
        headers: Map<String, String>,
    ): OpenAIClient {
        getClientCalls++
        getClientRequests += GetClientRequest(apiKey, baseUrl, organizationId, projectId, headers)
        failure?.let { throw it }
        return client
    }
}

/** Provider-port fake: returns whatever SDK client the test installs. */
class FakeAnthropicClientProviderPort : AnthropicClientProviderPort {
    lateinit var client: AnthropicClient
    var getClientCalls = 0

    data class GetClientRequest(
        val apiKey: String,
        val baseUrl: String?,
        val headers: Map<String, String>,
    )

    val getClientRequests = mutableListOf<GetClientRequest>()

    override fun getClient(apiKey: String, baseUrl: String?, headers: Map<String, String>): AnthropicClient {
        getClientCalls++
        getClientRequests += GetClientRequest(apiKey, baseUrl, headers)
        return client
    }
}

/** Provider-port fake: returns whatever SDK client the test installs. */
class FakeGoogleGenAiClientProviderPort : GoogleGenAiClientProviderPort {
    lateinit var client: Client
    var getClientCalls = 0

    data class GetClientRequest(
        val apiKey: String,
        val baseUrl: String?,
        val headers: Map<String, String>,
        val apiVersion: String,
    )

    val getClientRequests = mutableListOf<GetClientRequest>()

    override fun getClient(
        apiKey: String,
        baseUrl: String?,
        headers: Map<String, String>,
        apiVersion: String,
    ): Client {
        getClientCalls++
        getClientRequests += GetClientRequest(apiKey, baseUrl, headers, apiVersion)
        return client
    }
}

/** Fake inference lock (same semantics as the feature/chat fake). */
class FakeInferenceLockManager : com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager {
    override val isInferenceBlocked = kotlinx.coroutines.flow.MutableStateFlow(false)
    var acquireResult = true
    val acquiredTypes = mutableListOf<com.browntowndev.enclave.domain.usecase.inference.InferenceType>()
    val releasedTypes = mutableListOf<com.browntowndev.enclave.domain.usecase.inference.InferenceType>()
    var forceReleaseCount = 0

    override fun acquireLock(inferenceType: com.browntowndev.enclave.domain.usecase.inference.InferenceType): Boolean {
        acquiredTypes += inferenceType
        return acquireResult
    }

    override fun releaseLock(inferenceType: com.browntowndev.enclave.domain.usecase.inference.InferenceType) {
        releasedTypes += inferenceType
    }

    override fun forceReleaseOnDeviceLock() {
        forceReleaseCount++
    }
}

/**
 * Subclass fake for the owned [com.browntowndev.enclave.feature.inference.llama.LlamaChatSessionManager]:
 * all behavior is driven through [engineFake], which records every engine call.
 */
class FakeLlamaChatSessionManager(
    val engineFake: FakeLlamaEnginePort = FakeLlamaEnginePort(),
) : com.browntowndev.enclave.feature.inference.llama.LlamaChatSessionManager(engineFake)

/** Fake device-environment repository. */
class FakeDeviceEnvironmentRepositoryPort :
    com.browntowndev.enclave.domain.port.repository.DeviceEnvironmentRepositoryPort {
    var wifiConnected = true
    var storageAvailable = true
    var networkAvailable = true

    /** When non-empty, isNetworkAvailable drains this queue before falling back to [networkAvailable]. */
    val networkAvailableSequence = ArrayDeque<Boolean>()

    override suspend fun isWifiConnected(): Boolean = wifiConnected

    override suspend fun hasRequiredStorage(requiredBytes: Long): Boolean = storageAvailable

    override suspend fun isNetworkAvailable(): Boolean =
        if (networkAvailableSequence.isNotEmpty()) networkAvailableSequence.removeFirst() else networkAvailable
}

/**
 * Subclass fake for the open [com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase]:
 * returns [resultOverride] when set, otherwise runs the real parser.
 */
class FakeProcessThinkingTokensUseCase :
    com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase() {
    var resultOverride: ThinkingState? = null
    var invocationCount = 0

    override fun invoke(
        currentBuffer: String,
        newChunk: String,
        isThinking: Boolean,
        accumulatedThinkingText: String,
        accumulatedVisibleText: String,
    ): ThinkingState {
        invocationCount++
        return resultOverride ?: super.invoke(
            currentBuffer,
            newChunk,
            isThinking,
            accumulatedThinkingText,
            accumulatedVisibleText,
        )
    }
}

/** Fake [com.browntowndev.enclave.domain.port.inference.AgentLoopPort]: execute replays [executeFlow]. */
class FakeAgentLoopPort : com.browntowndev.enclave.domain.port.inference.AgentLoopPort {
    var executeFlow: kotlinx.coroutines.flow.Flow<com.browntowndev.enclave.domain.port.inference.AgentLoopEvent> =
        kotlinx.coroutines.flow.emptyFlow()
    val executeCalls = mutableListOf<List<com.browntowndev.enclave.domain.model.chat.ChatMessage>>()

    override fun execute(
        initialMessages: List<com.browntowndev.enclave.domain.model.chat.ChatMessage>,
        config: com.browntowndev.enclave.domain.model.inference.AgentLoopConfig,
        sessionContext: Map<String, String>,
        host: com.browntowndev.enclave.domain.model.inference.AgentLoopHostCallbacks,
    ): kotlinx.coroutines.flow.Flow<com.browntowndev.enclave.domain.port.inference.AgentLoopEvent> {
        executeCalls += initialMessages
        return executeFlow
    }
}

/** Fake [LiteRtConversation]: sendMessageAsync replays [responseFlow]. */
class FakeLiteRtConversation : LiteRtConversation {
    var responseFlow: kotlinx.coroutines.flow.Flow<ConversationResponse> = kotlinx.coroutines.flow.emptyFlow()
    var benchmarkSnapshot: LiteRtBenchmarkSnapshot? = null
    val sentMessages = mutableListOf<String>()
    val sendMessageCalls = mutableListOf<Pair<String, GenerationOptions?>>()
    var cancelProcessCount = 0

    override fun sendMessageAsync(message: String, options: GenerationOptions?): kotlinx.coroutines.flow.Flow<ConversationResponse> {
        sentMessages += message
        sendMessageCalls += message to options
        return responseFlow
    }

    override fun cancelProcess() {
        cancelProcessCount++
    }

    override fun benchmarkInfo(): LiteRtBenchmarkSnapshot? = benchmarkSnapshot
}

/** Fake [ConversationManager]: hands out [conversation] and records lifecycle calls. */
class FakeConversationManager : ConversationManager {
    var conversation: LiteRtConversation = FakeLiteRtConversation()
    var telemetryContextResult: LiteRtTelemetryContext =
        LiteRtTelemetryContext(history = emptyList(), systemPrompt = "")
    val getConversationCalls = mutableListOf<ModelType>()
    val historyCalls = mutableListOf<List<com.browntowndev.enclave.domain.model.chat.ChatMessage>>()
    var getConversationFailure: Throwable? = null
    var closeEngineFailure: Throwable? = null
    val endedRequestIds = mutableListOf<Int>()
    var closeConversationCount = 0
    var closeEngineCount = 0
    var cancelCurrentGenerationCount = 0
    var cancelProcessCount = 0
    private var nextRequestId = 0

    override suspend fun getConversation(
        modelType: ModelType,
        options: GenerationOptions?,
        onLoadingStarted: suspend () -> Unit,
    ): LiteRtConversation {
        getConversationCalls += modelType
        getConversationFailure?.let { throw it }
        return conversation
    }

    override suspend fun closeConversation() {
        closeConversationCount++
    }

    override suspend fun setHistory(messages: List<com.browntowndev.enclave.domain.model.chat.ChatMessage>) {
        historyCalls += messages
    }

    override fun telemetryContext(): LiteRtTelemetryContext = telemetryContextResult

    override suspend fun closeEngine() {
        closeEngineCount++
        closeEngineFailure?.let { throw it }
    }

    override fun beginGenerationRequest(): Int = ++nextRequestId

    override fun endGenerationRequest(requestId: Int) {
        endedRequestIds += requestId
    }

    override fun cancelCurrentGeneration() {
        cancelCurrentGenerationCount++
    }

    override fun cancelProcess() {
        cancelProcessCount++
    }
}

/** Fake [com.browntowndev.enclave.domain.port.inference.LlamaEnginePort]: no-op engine with knobs. */
class FakeLlamaEnginePort : com.browntowndev.enclave.domain.port.inference.LlamaEnginePort {
    data class GenerateWithOptionsCall(
        val options: GenerationOptions,
        val continuation: Boolean,
        val prevMessageCount: Int,
    )

    var generateFlow: kotlinx.coroutines.flow.Flow<com.browntowndev.enclave.domain.model.inference.GenerationEvent> =
        kotlinx.coroutines.flow.emptyFlow()

    /** Per-call override for [generateWithOptions]; takes precedence over [generateFlow] when set. */
    var generateWithOptionsResult: kotlinx.coroutines.flow.Flow<com.browntowndev.enclave.domain.model.inference.GenerationEvent>? =
        null

    /** Queued flows for successive [generateWithOptions] calls (MockK returnsMany equivalent). */
    val generateWithOptionsResults =
        ArrayDeque<kotlinx.coroutines.flow.Flow<com.browntowndev.enclave.domain.model.inference.GenerationEvent>>()
    var loadedResult = false
    var contextUsageResult = 0
    val initializeCalls = mutableListOf<com.browntowndev.enclave.domain.model.inference.LlamaModelConfig>()
    val startConversationCalls = mutableListOf<String?>()
    val appendedMessages = mutableListOf<com.browntowndev.enclave.domain.model.chat.ChatMessage>()
    val setHistoryCalls = mutableListOf<List<com.browntowndev.enclave.domain.model.chat.ChatMessage>>()
    var generateCallCount = 0
    val generateWithOptionsCalls = mutableListOf<GenerateWithOptionsCall>()
    var stopGenerationCount = 0
    val resetConversationCalls = mutableListOf<String?>()
    var unloadCount = 0

    override suspend fun initialize(config: com.browntowndev.enclave.domain.model.inference.LlamaModelConfig) {
        initializeCalls += config
    }

    override suspend fun startConversation(systemPrompt: String?) {
        startConversationCalls += systemPrompt
    }

    override suspend fun appendMessage(message: com.browntowndev.enclave.domain.model.chat.ChatMessage) {
        appendedMessages += message
    }

    override suspend fun setHistory(messages: List<com.browntowndev.enclave.domain.model.chat.ChatMessage>) {
        setHistoryCalls += messages
    }

    override fun generate(): kotlinx.coroutines.flow.Flow<com.browntowndev.enclave.domain.model.inference.GenerationEvent> {
        generateCallCount++
        return generateFlow
    }

    override fun generateWithOptions(
        options: GenerationOptions,
        continuation: Boolean,
        prevMessageCount: Int,
    ): kotlinx.coroutines.flow.Flow<com.browntowndev.enclave.domain.model.inference.GenerationEvent> {
        generateWithOptionsCalls += GenerateWithOptionsCall(options, continuation, prevMessageCount)
        return when {
            generateWithOptionsResults.isNotEmpty() -> generateWithOptionsResults.removeFirst()
            generateWithOptionsResult != null -> generateWithOptionsResult!!
            else -> generateFlow
        }
    }

    override suspend fun stopGeneration() {
        stopGenerationCount++
    }

    override suspend fun resetConversation(systemPrompt: String?) {
        resetConversationCalls += systemPrompt
    }

    override suspend fun unload() {
        unloadCount++
    }

    override fun isLoaded(): Boolean = loadedResult

    override suspend fun contextUsage(): Int = contextUsageResult
}

/** Fake [com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort]. */
class FakeToolExecutionEventPort : com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort {
    override val events =
        kotlinx.coroutines.flow.MutableSharedFlow<com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent>(
            extraBufferCapacity = 64,
        )
}

/**
 * Delegating fake over [com.browntowndev.enclave.core.testing.FakeDefaultModelRepository]
 * that can fail getDefault with [getDefaultFailure] (e.g. an IOException).
 */
class FakeFailingDefaultModelRepository :
    com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort {
    private val delegate = com.browntowndev.enclave.core.testing.FakeDefaultModelRepository()
    var getDefaultFailure: Throwable? = null

    fun seed(assignment: com.browntowndev.enclave.domain.model.config.DefaultModelAssignment) {
        delegate.seed(assignment)
    }

    fun emitDefaults(assignments: List<com.browntowndev.enclave.domain.model.config.DefaultModelAssignment>) {
        delegate.emitDefaults(assignments)
    }

    override suspend fun getDefault(modelType: ModelType): com.browntowndev.enclave.domain.model.config.DefaultModelAssignment? {
        getDefaultFailure?.let { throw it }
        return delegate.getDefault(modelType)
    }

    override fun observeDefaults(): kotlinx.coroutines.flow.Flow<List<com.browntowndev.enclave.domain.model.config.DefaultModelAssignment>> =
        delegate.observeDefaults()

    override suspend fun setDefault(
        modelType: ModelType,
        localConfigId: com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId?,
        apiConfigId: com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId?,
        ttsProviderId: com.browntowndev.enclave.domain.model.config.TtsProviderId?,
        mediaProviderId: com.browntowndev.enclave.domain.model.config.MediaProviderId?,
        liveVoiceProviderId: com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId?,
    ) {
        delegate.setDefault(modelType, localConfigId, apiConfigId, ttsProviderId, mediaProviderId, liveVoiceProviderId)
    }

    override suspend fun clearDefault(modelType: ModelType) {
        delegate.clearDefault(modelType)
    }
}

/** Fake [com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort] with per-type path knobs. */
class FakeUtilityModelFilePort : com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort {
    val utilityPaths = mutableMapOf<com.browntowndev.enclave.domain.model.config.UtilityType, String?>()
    var localModelPath: String? = null
    val resolveCalls = mutableListOf<com.browntowndev.enclave.domain.model.config.UtilityType>()

    override suspend fun resolveUtilityModelPath(
        utilityType: com.browntowndev.enclave.domain.model.config.UtilityType,
    ): String? {
        resolveCalls += utilityType
        return utilityPaths[utilityType]
    }

    override suspend fun resolveLocalModelPath(
        metadata: com.browntowndev.enclave.domain.model.config.LocalModelMetadata,
    ): String? = localModelPath
}

/**
 * Subclass fake for the concrete [com.browntowndev.enclave.feature.inference.openai.OpenAiClientProvider]
 * (the catalog repository takes the concrete class, not the port). Records getClient calls and
 * returns [client].
 */
class FakeOpenAiClientProvider : com.browntowndev.enclave.feature.inference.openai.OpenAiClientProvider() {
    lateinit var client: com.openai.client.OpenAIClient

    data class GetClientRequest(
        val apiKey: String,
        val baseUrl: String?,
        val organizationId: String?,
        val projectId: String?,
        val headers: Map<String, String>,
    )

    val getClientRequests = mutableListOf<GetClientRequest>()

    override fun getClient(
        apiKey: String,
        baseUrl: String?,
        organizationId: String?,
        projectId: String?,
        headers: Map<String, String>,
    ): com.openai.client.OpenAIClient {
        getClientRequests += GetClientRequest(apiKey, baseUrl, organizationId, projectId, headers)
        return client
    }
}

/**
 * Subclass fake for the concrete [com.browntowndev.enclave.feature.inference.google.GoogleGenAiClientProvider]
 * (the catalog repository takes the concrete class, not the port). Records listModels calls and
 * returns [modelsResult].
 */
class FakeGoogleGenAiClientProvider : com.browntowndev.enclave.feature.inference.google.GoogleGenAiClientProvider() {
    var modelsResult: List<com.google.genai.types.Model> = emptyList()

    data class ListModelsRequest(
        val apiKey: String,
        val baseUrl: String?,
        val headers: Map<String, String>,
        val apiVersion: String,
    )

    val listModelsRequests = mutableListOf<ListModelsRequest>()

    override fun listModels(
        apiKey: String,
        baseUrl: String?,
        headers: Map<String, String>,
        apiVersion: String,
    ): List<com.google.genai.types.Model> {
        listModelsRequests += ListModelsRequest(apiKey, baseUrl, headers, apiVersion)
        return modelsResult
    }
}

/** Recording fake for the OkHttp [okhttp3.WebSocket] SDK interface (live-voice tests). */
class RecordingWebSocket : okhttp3.WebSocket {
    val sentTexts = mutableListOf<String>()
    var sendResult = true
    var closeCount = 0
    var cancelCount = 0

    override fun request(): okhttp3.Request =
        okhttp3.Request.Builder().url("https://localhost/").build()

    override fun queueSize(): Long = 0L

    override fun send(text: String): Boolean {
        sentTexts += text
        return sendResult
    }

    override fun send(bytes: okio.ByteString): Boolean = sendResult

    override fun close(code: Int, reason: String?): Boolean {
        closeCount++
        return true
    }

    override fun cancel() {
        cancelCount++
    }
}
