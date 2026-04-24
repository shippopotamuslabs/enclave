package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import androidx.annotation.VisibleForTesting
import android.os.Debug
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.SamplerConfig
import com.browntowndev.pocketcrew.domain.model.inference.AttachedImageInspectParams
import com.browntowndev.pocketcrew.domain.model.inference.ExtractDepth
import com.browntowndev.pocketcrew.domain.model.inference.ExtractFormat
import com.browntowndev.pocketcrew.domain.model.inference.GetMessageContextParams
import com.browntowndev.pocketcrew.domain.model.inference.SearchChatHistoryParams
import com.browntowndev.pocketcrew.domain.model.inference.SearchChatParams
import com.browntowndev.pocketcrew.domain.model.inference.TavilyExtractParams
import com.browntowndev.pocketcrew.domain.model.inference.TavilyWebSearchParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolParam
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.util.ChatHistoryCompressor
import com.browntowndev.pocketcrew.domain.util.NativeToolResultFormatter
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.browntowndev.pocketcrew.domain.util.JTokkitTokenCounter
import com.google.ai.edge.litertlm.Message as LiteRtMessage
import com.google.ai.edge.litertlm.SamplerConfig as LiteRtSamplerConfig
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CancellationException as JavaCancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import javax.inject.Inject


/**
 * Implementation of [ConversationManager] that manages LiteRT conversation lifecycle.
 * Resolves the model and configuration lazily and recreates resources when the 
 * model or configuration changes.
 *
 * Thread-safe access to the conversation instance using a Mutex.
 * Concurrent calls to [getConversation] will return the same conversation instance
 * IF the configuration is identical.
 */
class ConversationManagerImpl @Inject constructor(
    private val context: Context,
    private val localModelRepository: LocalModelRepositoryPort,
    private val activeModelProvider: ActiveModelProviderPort,
    private val loggingPort: LoggingPort,
    private val toolExecutor: ToolExecutorPort,
) : ConversationManager {

    private val defaultSystemPrompt = """
            # CRITICAL RULE — EVERY RESPONSE:
            Be maximally truthful, witty, irreverent and a smartass when it fits.
            Mirror the user's tone exactly. Direct, lucid, fun. ZERO moralizing, fluff or bullshit.
    """.trimIndent()

    private data class ConversationSignature(
        val modelType: ModelType,
        val configId: LocalModelConfigurationId,
        val thinkingEnabled: Boolean,
        val systemPrompt: String,
        val temperature: Double,
        val topP: Double,
        val topK: Int,
        val hasSearchTool: Boolean,
        val hasExtractTool: Boolean,
        val hasImageTool: Boolean,
        val hasMemoryTools: Boolean,
        val hasGetMessageContextTool: Boolean,
    )

    // Cached state: updated whenever reloadIfNeeded is called.
    private var currentModelPath: String? = null
    private var currentModelType: ModelType? = null
    private var currentConfigId: LocalModelConfigurationId? = null
    private var currentThinkingEnabled: Boolean? = null
    private var currentSystemPrompt: String = defaultSystemPrompt
    private var currentContextWindow: Int = 2048
    private var currentSamplerConfig: SamplerConfig? = null
    private var currentSignature: ConversationSignature? = null

    // Conversation is stateful and maintains context for multi-turn chats.
    @Volatile
    private var conversation: Conversation? = null

    // Cache the LiteRtConversation wrapper to return the same instance on repeated calls
    @Volatile
    private var conversationPort: LiteRtConversation? = null

    // Cached history to seed new conversations
    @Volatile
    private var history: List<DomainChatMessage> = emptyList()

    // The underlying engine instance, created lazily
    @Volatile
    private var engine: Engine? = null

    // Track which backend the engine was initialized with
    // NPU backends require samplerConfig=null (runtime doesn't support custom sampling on NPU)
    @Volatile
    private var activeBackendIsNpu: Boolean = false

    // Flag to track if the current conversation has accumulated transient tool results
    // that should be flushed before the next turn to prevent context window exhaustion.
    @Volatile
    private var hasTransientToolResults: Boolean = false

    @Volatile
    private var transientToolResultTokens: Int = 0

    @Volatile
    private var contextFullWarned: Boolean = false

    private fun getModelPath(filename: String): String {
        // Use getExternalFilesDir to match ModelFileScanner's directory choice
        val modelsDir = File(context.getExternalFilesDir(null), "models")
        return File(modelsDir, filename).absolutePath
    }

    private val mutex = Mutex()

    @Volatile
    private var isCurrentGenerationCancelled: Boolean = false

    private val cancellationEpoch = AtomicInteger(0)

    override fun cancelCurrentGeneration() {
        Log.d(TAG, "cancelCurrentGeneration called")
        isCurrentGenerationCancelled = true
    }

    override fun cancelProcess() {
        Log.i(TAG, "cancelProcess requested - resetting manager state")
        isCurrentGenerationCancelled = true
        cancellationEpoch.incrementAndGet()

        conversationPort?.let { port ->
            Log.d(TAG, "Triggering native cancelProcess on active port")
            port.cancelProcess()
        }
    }

    /**
     * Returns the active conversation, initializing it if needed.
     * Thread-safe: concurrent calls will return the same conversation instance.
     *
     * @param modelType The type of model to get the configuration for.
     * @param options Per-request generation options (e.g., sampler overrides).
     * @return The active LiteRtConversation instance wrapping the LiteRT Conversation
     * @throws IllegalStateException if no model is registered for this model type
     */
    override suspend fun getConversation(
        modelType: ModelType, 
        options: GenerationOptions?,
        onLoadingStarted: suspend () -> Unit
    ): LiteRtConversation = withContext(Dispatchers.IO) {
        isCurrentGenerationCancelled = false
        val requestCancellationEpoch = cancellationEpoch.get()
        // Lock to avoid concurrent initializations and ensure consistent state
        mutex.withLock {
            suspend fun throwIfCancellationRequested(reason: String): Nothing {
                Log.w(
                    TAG,
                    "getConversation cancelled reason=$reason isActive=${currentCoroutineContext().isActive} " +
                        "isCancelled=$isCurrentGenerationCancelled requestEpoch=$requestCancellationEpoch " +
                        "currentEpoch=${cancellationEpoch.get()}",
                )
                throw CancellationException(reason)
            }

            suspend fun ensureRequestActive(reason: String) {
                if (
                    !currentCoroutineContext().isActive ||
                    isCurrentGenerationCancelled ||
                    cancellationEpoch.get() != requestCancellationEpoch
                ) {
                    throwIfCancellationRequested(reason)
                }
            }

            ensureRequestActive("Request cancelled before initialization")
            val activeConfig = activeModelProvider.getActiveConfiguration(modelType)
                ?: throw IllegalStateException("No active configuration for $modelType")

            if (modelType == ModelType.VISION) {
                throw IllegalStateException("ConversationManager does not run vision models. Vision is API-only.")
            }

            if (!activeConfig.isLocal) {
                throw IllegalStateException("ConversationManager cannot run API models. ModelType $modelType is mapped to an API configuration.")
            }

            val configId = activeConfig.id as LocalModelConfigurationId
            val asset = localModelRepository.getAssetByConfigId(configId)
                ?: throw IllegalStateException("No registered asset for config ${activeConfig.id}. Download a model first.")

            val modelPath = getModelPath(asset.metadata.localFileName)
            val resolvedThinkingEnabled = activeConfig.thinkingEnabled ?: false
            val resolvedSystemPrompt = options?.systemPrompt ?: activeConfig.systemPrompt ?: defaultSystemPrompt
            val chatId = options?.chatId
            val userMessageId = options?.userMessageId
            val targetSamplerConfig = SamplerConfig(
                temperature = options?.temperature?.toDouble() ?: activeConfig.temperature ?: 0.7,
                topP = options?.topP?.toDouble() ?: activeConfig.topP ?: 0.95,
                topK = options?.topK ?: activeConfig.topK ?: 40
            )
            val hasSearchTool = options?.availableTools?.contains(ToolDefinition.TAVILY_WEB_SEARCH) == true
            val hasExtractTool = options?.availableTools?.contains(ToolDefinition.TAVILY_EXTRACT) == true
            val hasImageTool = options?.availableTools?.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT) == true
            val hasMemoryTools = options?.availableTools?.contains(ToolDefinition.SEARCH_CHAT_HISTORY) == true ||
                options?.availableTools?.contains(ToolDefinition.SEARCH_CHAT) == true
            val hasGetMessageContextTool = options?.availableTools?.contains(ToolDefinition.GET_MESSAGE_CONTEXT) == true

            val desiredSignature = ConversationSignature(
                modelType = modelType,
                configId = configId,
                thinkingEnabled = resolvedThinkingEnabled,
                systemPrompt = resolvedSystemPrompt,
                temperature = targetSamplerConfig.temperature,
                topP = targetSamplerConfig.topP,
                topK = targetSamplerConfig.topK,
                hasSearchTool = hasSearchTool,
                hasExtractTool = hasExtractTool,
                hasImageTool = hasImageTool,
                hasMemoryTools = hasMemoryTools,
                hasGetMessageContextTool = hasGetMessageContextTool,
            )

            val engineChanged = currentModelPath != modelPath || engine == null
            val conversationChanged =
                currentSignature != desiredSignature || conversation == null || conversation?.isAlive != true || hasTransientToolResults
            val conversationRecreated = engineChanged || conversationChanged

            if (engineChanged) {
                ensureRequestActive("Cancelled before engine creation")
                onLoadingStarted()
                Log.d(
                    TAG,
                    "Recreating LiteRT engine for modelType=$modelType, modelPath=$modelPath memory=${memorySnapshot()}"
                )
                closeEngineLocked()
                // currentModelPath = modelPath // Moved to after successful initialization
                hasTransientToolResults = false
                transientToolResultTokens = 0
                contextFullWarned = false
            } else if (conversationChanged) {
                Log.d(
                    TAG,
                    "Recreating LiteRT conversation for modelType=$modelType, configId=$configId, thinkingEnabled=$resolvedThinkingEnabled, hasTransientToolResults=$hasTransientToolResults memory=${memorySnapshot()}"
                )
                closeConversationLocked()
                hasTransientToolResults = false
                transientToolResultTokens = 0
                contextFullWarned = false
            } else {
                Log.d(
                    TAG,
                    "Reusing LiteRT conversation for modelType=$modelType, configId=$configId, thinkingEnabled=$resolvedThinkingEnabled memory=${memorySnapshot()}"
                )
            }

            ensureEngineInitializedLocked(modelPath, activeConfig.contextWindow ?: 2048, asset.metadata.isMultimodal)
            if (
                !currentCoroutineContext().isActive ||
                isCurrentGenerationCancelled ||
                cancellationEpoch.get() != requestCancellationEpoch
            ) {
                Log.w(TAG, "getConversation cancelled after engine initialization - closing engine before returning")
                closeEngineLocked()
                throw CancellationException("Cancelled after engine initialization")
            }
            val eng = engine ?: throw IllegalStateException("Engine not initialized")

            if (conversation == null || conversation?.isAlive != true) {
                conversation?.close()

                val contextWindow = activeConfig.contextWindow ?: 2048
                // Reserve extra buffer for transient tool results already in the KV cache.
                // This ensures compressHistory accounts for tool result tokens that will
                // coexist in the context window alongside the compressed history.
                val effectiveBufferTokens = 1000 + transientToolResultTokens
                val compressedHistory = ChatHistoryCompressor.compressHistory(
                    history = history,
                    systemPrompt = resolvedSystemPrompt,
                    contextWindowTokens = contextWindow,
                    bufferTokens = effectiveBufferTokens,
                    modelId = asset.metadata.localFileName,
                    tokenCounter = JTokkitTokenCounter
                )

                if (compressedHistory.size < history.size) {
                    Log.d(TAG, "Compressed LiteRT history from ${history.size} to ${compressedHistory.size} messages to fit context window")
                }

                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(resolvedSystemPrompt),
                    initialMessages = compressedHistory.map { domainMsg: DomainChatMessage ->
                        when (domainMsg.role) {
                            Role.USER -> LiteRtMessage.user(domainMsg.content)
                            Role.ASSISTANT -> LiteRtMessage.model(domainMsg.content)
                            Role.SYSTEM -> LiteRtMessage.system(domainMsg.content)
                        }
                    },
                    // NPU backends don't support custom sampler configs.
                    // Passing one causes redundant CPU-side sampling allocation.
                    samplerConfig = if (activeBackendIsNpu) null else LiteRtSamplerConfig(
                        temperature = targetSamplerConfig.temperature,
                        topP = targetSamplerConfig.topP,
                        topK = targetSamplerConfig.topK,
                    ),
                    automaticToolCalling = true,
                    tools = buildList {
                        if (hasSearchTool) {
                            add(
                                tool(
                                    LocalSearchToolset(
                                        modelType = modelType,
                                        chatId = chatId,
                                        userMessageId = userMessageId,
                                    )
                                )
                            )
                        }
                        if (hasExtractTool) {
                            add(
                                tool(
                                    LocalExtractToolset(
                                        modelType = modelType,
                                        chatId = chatId,
                                        userMessageId = userMessageId,
                                    )
                                )
                            )
                        }
                        if (hasImageTool) {
                            add(
                                tool(
                                    LocalImageInspectToolset(
                                        modelType = modelType,
                                        chatId = chatId,
                                        userMessageId = userMessageId,
                                    )
                                )
                            )
                        }
                        if (hasMemoryTools) {
                            add(
                                tool(
                                    LocalSearchChatHistoryToolset(
                                        modelType = modelType,
                                        chatId = chatId,
                                        userMessageId = userMessageId,
                                    )
                                )
                            )
                            add(
                                tool(
                                    LocalSearchChatToolset(
                                        modelType = modelType,
                                        chatId = chatId,
                                        userMessageId = userMessageId,
                                    )
                                )
                            )
                        }
                        if (hasGetMessageContextTool) {
                            add(
                                tool(
                                    LocalGetMessageContextToolset(
                                        modelType = modelType,
                                        chatId = chatId,
                                        userMessageId = userMessageId,
                                    )
                                )
                            )
                        }
                    }
                )

                ensureRequestActive("Cancelled during conversation setup")

                Log.d(
                    TAG,
                    "Creating LiteRT conversation for modelType=$modelType historySize=${history.size} hasSearchTool=$hasSearchTool memoryBeforeCreate=${memorySnapshot()}"
                )
                conversation = eng.createConversation(conversationConfig)
                Log.d(
                    TAG,
                    "LiteRT conversation created for modelType=$modelType memoryAfterCreate=${memorySnapshot()}"
                )
            }

            if (
                !currentCoroutineContext().isActive ||
                isCurrentGenerationCancelled ||
                cancellationEpoch.get() != requestCancellationEpoch
            ) {
                Log.w(TAG, "getConversation cancelled after conversation creation - cleaning up")
                closeConversationLocked()
                throw CancellationException("Cancelled immediately after conversation setup")
            }

            val liteRtConversation = conversation ?: throw IllegalStateException("Conversation not initialized")
            
            // Re-wrap if conversation was recreated
            if (conversationPort == null) {
                conversationPort = ConversationImpl(context, liteRtConversation)
            }

            currentSignature = desiredSignature
            currentModelType = modelType
            currentConfigId = configId
            currentThinkingEnabled = resolvedThinkingEnabled
            currentSystemPrompt = resolvedSystemPrompt
            currentContextWindow = activeConfig.contextWindow ?: 2048
            currentSamplerConfig = targetSamplerConfig
            currentModelPath = modelPath // Set only after full success

            Log.d(
                TAG,
                "getConversation decision: modelType=$modelType, configId=$configId, thinkingEnabled=$resolvedThinkingEnabled, systemPrompt=${resolvedSystemPrompt.take(120)}, samplerConfig=$targetSamplerConfig, engineRecreated=$engineChanged, conversationRecreated=$conversationRecreated, memory=${memorySnapshot()}"
            )
            
            return@withContext conversationPort!!
        }
    }

    /**
     * ToolSet for local inference models to access device capabilities.
     * Maps LiteRT native tool calls to the domain [ToolExecutorPort].
     */
    private inner class LocalSearchToolset(
        private val modelType: ModelType,
        private val chatId: ChatId?,
        private val userMessageId: MessageId?,
    ) : ToolSet {
        @Tool(description = "Search the web for information.")
        fun tavily_web_search(
            @ToolParam(description = "The search query.") query: String
        ): String {
            Log.d(TAG, "Executing native tool call: tavily_web_search with query: $query")
            loggingPort.info(
                TAG,
                "Native tool call requested tool=${ToolDefinition.TAVILY_WEB_SEARCH.name} modelType=$modelType queryChars=${query.length}"
            )
            return try {
                runBlocking(Dispatchers.IO) {
                    loggingPort.info(
                        TAG,
                        "Entered native tool coroutine tool=${ToolDefinition.TAVILY_WEB_SEARCH.name} modelType=$modelType"
                    )
                    val request = ToolCallRequest(
                        toolName = ToolDefinition.TAVILY_WEB_SEARCH.name,
                        argumentsJson = ToolDefinition.TAVILY_WEB_SEARCH.encodeArguments<TavilyWebSearchParams>(TavilyWebSearchParams(query)),
                        provider = "LITERT",
                        modelType = modelType,
                        chatId = chatId,
                        userMessageId = userMessageId,
                    )
                    loggingPort.info(
                        TAG,
                        "Dispatching native tool call tool=${request.toolName} provider=${request.provider} modelType=${request.modelType}"
                    )
                    executeToolSafely(request)
                }
            } catch (e: JavaCancellationException) {
                throw e
            } catch (t: Throwable) {
                val rootCause = t.rootCause()
                loggingPort.error(
                    TAG,
                    "Native tool method failed before safe execution tool=${ToolDefinition.TAVILY_WEB_SEARCH.name} cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
                    rootCause
                )
                buildToolErrorJson(ToolDefinition.TAVILY_WEB_SEARCH.name, rootCause)
            }
        }
    }


    private inner class LocalExtractToolset(
        private val modelType: ModelType,
        private val chatId: ChatId?,
        private val userMessageId: MessageId?,
    ) : ToolSet {
        @Tool(description = "Extract and parse the full content from a list of URLs.")
        fun tavily_extract(
            @ToolParam(description = "JSON array of URLs to extract content from.") urls: String,
            @ToolParam(description = "Extraction depth: basic or advanced.") extract_depth: String = "basic",
            @ToolParam(description = "Output format: markdown or text.") format: String = "markdown",
        ): String {
            Log.d(TAG, "Executing native tool call: tavily_extract with urls: $urls")
            loggingPort.info(
                TAG,
                "Native tool call requested tool=${ToolDefinition.TAVILY_EXTRACT.name} modelType=$modelType chatId=${chatId?.value ?: "<none>"} userMessageId=${userMessageId?.value ?: "<none>"} urlCount=${urls.length}"
            )
            return try {
                runBlocking(Dispatchers.IO) {
                    loggingPort.info(
                        TAG,
                        "Entered native tool coroutine tool=${ToolDefinition.TAVILY_EXTRACT.name} modelType=$modelType"
                    )
                    val parsedUrls = NativeToolResultFormatter.parseUrls(urls)
                    val params = TavilyExtractParams(
                        urls = parsedUrls,
                        extract_depth = extract_depth.takeIf { it.isNotBlank() }?.let { ExtractDepth.valueOf(it) } ?: ExtractDepth.basic,
                        format = format.takeIf { it.isNotBlank() }?.let { ExtractFormat.valueOf(it) } ?: ExtractFormat.markdown,
                    )
                    val request = ToolCallRequest(
                        toolName = ToolDefinition.TAVILY_EXTRACT.name,
                        argumentsJson = ToolDefinition.TAVILY_EXTRACT.encodeArguments<TavilyExtractParams>(params),
                        provider = "LITERT",
                        modelType = modelType,
                        chatId = chatId,
                        userMessageId = userMessageId,
                    )
                    loggingPort.info(
                        TAG,
                        "Dispatching native tool call tool=${request.toolName} provider=${request.provider} modelType=${request.modelType}"
                    )
                    executeToolSafely(request)
                }
            } catch (e: JavaCancellationException) {
                throw e
            } catch (t: Throwable) {
                val rootCause = t.rootCause()
                loggingPort.error(
                    TAG,
                    "Native tool method failed before safe execution tool=${ToolDefinition.TAVILY_EXTRACT.name} cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
                    rootCause
                )
                buildToolErrorJson(ToolDefinition.TAVILY_EXTRACT.name, rootCause)
            }
        }

    }

    private inner class LocalImageInspectToolset(
        private val modelType: ModelType,
        private val chatId: ChatId?,
        private val userMessageId: MessageId?,
    ) : ToolSet {
        @Tool(description = "Inspect a previously attached image.")
        fun attached_image_inspect(
            @ToolParam(description = "The question about the image.") question: String
        ): String {
            Log.d(TAG, "Executing native tool call: attached_image_inspect with question: $question")
            loggingPort.info(
                TAG,
                "Native tool call requested tool=${ToolDefinition.ATTACHED_IMAGE_INSPECT.name} modelType=$modelType chatId=${chatId?.value ?: "<none>"} userMessageId=${userMessageId?.value ?: "<none>"} questionChars=${question.length}"
            )
            return try {
                runBlocking(Dispatchers.IO) {
                    loggingPort.info(
                        TAG,
                        "Entered native tool coroutine tool=${ToolDefinition.ATTACHED_IMAGE_INSPECT.name} modelType=$modelType chatId=${chatId?.value ?: "<none>"} userMessageId=${userMessageId?.value ?: "<none>"}"
                    )
                    val request = ToolCallRequest(
                        toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                        argumentsJson = ToolDefinition.ATTACHED_IMAGE_INSPECT.encodeArguments<AttachedImageInspectParams>(AttachedImageInspectParams(question)),
                        provider = "LITERT",
                        modelType = modelType,
                        chatId = chatId,
                        userMessageId = userMessageId,
                    )
                    loggingPort.info(
                        TAG,
                        "Dispatching native tool call tool=${request.toolName} provider=${request.provider} modelType=${request.modelType} chatId=${request.chatId?.value ?: "<none>"} userMessageId=${request.userMessageId?.value ?: "<none>"}"
                    )
                    executeToolSafely(request)
                }
            } catch (e: JavaCancellationException) {
                throw e
            } catch (t: Throwable) {
                val rootCause = t.rootCause()
                loggingPort.error(
                    TAG,
                    "Native tool method failed before safe execution tool=${ToolDefinition.ATTACHED_IMAGE_INSPECT.name} cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
                    rootCause
                )
                buildToolErrorJson(ToolDefinition.ATTACHED_IMAGE_INSPECT.name, rootCause)
            }
        }
    }

    private inner class LocalSearchChatHistoryToolset(
        private val modelType: ModelType,
        private val chatId: ChatId?,
        private val userMessageId: MessageId?,
    ) : ToolSet {
        @Tool(description = "Search the user's past conversation history for context.")
        fun search_chat_history(
            @ToolParam(description = "The search queries separated by commas.") queries: String
        ): String {
            Log.d(TAG, "Executing native tool call: search_chat_history with queries: $queries")
            loggingPort.info(
                TAG,
                "Native tool call requested tool=${ToolDefinition.SEARCH_CHAT_HISTORY.name} modelType=$modelType queryChars=${queries.length}"
            )
            return try {
                runBlocking(Dispatchers.IO) {
                    loggingPort.info(
                        TAG,
                        "Entered native tool coroutine tool=${ToolDefinition.SEARCH_CHAT_HISTORY.name} modelType=$modelType"
                    )
                    // Parse comma-separated string into List<String>
                    val queryList = queries.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        
                    val request = ToolCallRequest(
                        toolName = ToolDefinition.SEARCH_CHAT_HISTORY.name,
                        argumentsJson = ToolDefinition.SEARCH_CHAT_HISTORY.encodeArguments<SearchChatHistoryParams>(SearchChatHistoryParams(queries = queryList)),
                        provider = "LITERT",
                        modelType = modelType,
                        chatId = chatId,
                        userMessageId = userMessageId,
                    )
                    loggingPort.info(
                        TAG,
                        "Dispatching native tool call tool=${request.toolName} provider=${request.provider} modelType=${request.modelType}"
                    )
                    executeToolSafely(request)
                }
            } catch (e: JavaCancellationException) {
                throw e
            } catch (t: Throwable) {
                val rootCause = t.rootCause()
                loggingPort.error(
                    TAG,
                    "Native tool method failed before safe execution tool=${ToolDefinition.SEARCH_CHAT_HISTORY.name} cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
                    rootCause
                )
                buildToolErrorJson(ToolDefinition.SEARCH_CHAT_HISTORY.name, rootCause)
            }
        }
    }

    private inner class LocalSearchChatToolset(
        private val modelType: ModelType,
        private val chatId: ChatId?,
        private val userMessageId: MessageId?,
    ) : ToolSet {
        @Tool(description = "Search messages in a specific chat for details no longer in context.")
        fun search_chat(
            @ToolParam(description = "The ID of the chat to search.") chat_id: String,
            @ToolParam(description = "The search query.") query: String
        ): String {
            Log.d(TAG, "Executing native tool call: search_chat with chatId: $chat_id query: $query")
            loggingPort.info(
                TAG,
                "Native tool call requested tool=${ToolDefinition.SEARCH_CHAT.name} modelType=$modelType chatId=$chat_id queryChars=${query.length}"
            )
            return try {
                runBlocking(Dispatchers.IO) {
                    loggingPort.info(
                        TAG,
                        "Entered native tool coroutine tool=${ToolDefinition.SEARCH_CHAT.name} modelType=$modelType chatId=$chat_id"
                    )
                    val request = ToolCallRequest(
                        toolName = ToolDefinition.SEARCH_CHAT.name,
                        argumentsJson = ToolDefinition.SEARCH_CHAT.encodeArguments<SearchChatParams>(SearchChatParams(chat_id = chat_id, query = query)),
                        provider = "LITERT",
                        modelType = modelType,
                        chatId = chatId,
                        userMessageId = userMessageId,
                    )
                    loggingPort.info(
                        TAG,
                        "Dispatching native tool call tool=${request.toolName} provider=${request.provider} modelType=${request.modelType} chatId=${request.chatId?.value ?: "<none>"}"
                    )
                    executeToolSafely(request)
                }
            } catch (e: JavaCancellationException) {
                throw e
            } catch (t: Throwable) {
                val rootCause = t.rootCause()
                loggingPort.error(
                    TAG,
                    "Native tool method failed before safe execution tool=${ToolDefinition.SEARCH_CHAT.name} cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
                    rootCause
                )
                buildToolErrorJson(ToolDefinition.SEARCH_CHAT.name, rootCause)
            }
        }
    }

    private inner class LocalGetMessageContextToolset(
        private val modelType: ModelType,
        private val chatId: ChatId?,
        private val userMessageId: MessageId?,
    ) : ToolSet {
        @Tool(description = "Get messages surrounding a specific message in its chat for more context.")
        fun get_message_context(
            @ToolParam(description = "The ID of the anchor message to get context around.") message_id: String,
            @ToolParam(description = "Number of messages before the anchor message. Default 5.") before: Int = 5,
            @ToolParam(description = "Number of messages after the anchor message. Default 5.") after: Int = 5
        ): String {
            Log.d(TAG, "Executing native tool call: get_message_context with messageId: $message_id before: $before after: $after")
            loggingPort.info(
                TAG,
                "Native tool call requested tool=${ToolDefinition.GET_MESSAGE_CONTEXT.name} modelType=$modelType chatId=${chatId?.value ?: "<none>"} userMessageId=${userMessageId?.value ?: "<none>"} messageId=$message_id"
            )
            return try {
                runBlocking(Dispatchers.IO) {
                    loggingPort.info(
                        TAG,
                        "Entered native tool coroutine tool=${ToolDefinition.GET_MESSAGE_CONTEXT.name} modelType=$modelType messageId=$message_id"
                    )
                    val params = GetMessageContextParams(message_id, before, after)
                    val request = ToolCallRequest(
                        toolName = ToolDefinition.GET_MESSAGE_CONTEXT.name,
                        argumentsJson = ToolDefinition.GET_MESSAGE_CONTEXT.encodeArguments<GetMessageContextParams>(params),
                        provider = "LITERT",
                        modelType = modelType,
                        chatId = chatId,
                        userMessageId = userMessageId,
                    )
                    loggingPort.info(
                        TAG,
                        "Dispatching native tool call tool=${request.toolName} provider=${request.provider} modelType=${request.modelType} chatId=${request.chatId?.value ?: "<none>"}"
                    )
                    executeToolSafely(request)
                }
            } catch (e: JavaCancellationException) {
                throw e
            } catch (t: Throwable) {
                val rootCause = t.rootCause()
                loggingPort.error(
                    TAG,
                    "Native tool method failed before safe execution tool=${ToolDefinition.GET_MESSAGE_CONTEXT.name} cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
                    rootCause
                )
                buildToolErrorJson(ToolDefinition.GET_MESSAGE_CONTEXT.name, rootCause)
            }
        }
    }

    private suspend fun executeToolSafely(request: ToolCallRequest): String {
        if (isCurrentGenerationCancelled) {
            loggingPort.warning(TAG, "Tool execution aborted: generation was cancelled")
            throw CancellationException("Generation was cancelled")
        }

        // Return a compact error payload instead of throwing across the native boundary.
        // The LiteRT tool loop can keep running and surface the failure as a model-visible turn.
        if (contextFullWarned) {
            loggingPort.error(
                TAG,
                "Model ignored context-full warning and called another tool tool=${request.toolName}. Returning stop-tools error payload."
            )
            return LocalToolResultPolicy.buildContextWindowExceededToolError(request.toolName)
        }

        return try {
            loggingPort.debug(
                TAG,
                "executeToolSafely entered tool=${request.toolName} provider=${request.provider} modelType=${request.modelType} chatId=${request.chatId?.value ?: "<none>"} userMessageId=${request.userMessageId?.value ?: "<none>"}"
            )

            loggingPort.debug(
                TAG,
                "Resolved ToolExecutorPort for native tool call tool=${request.toolName}"
            )
            val rawResultJson = toolExecutor.execute(request).resultJson
            val modelId = currentModelPath?.substringAfterLast('/')
            val decision = LocalToolResultPolicy.evaluate(
                rawResultJson = rawResultJson,
                contextWindowTokens = currentContextWindow,
                currentSystemPrompt = currentSystemPrompt,
                history = history,
                transientToolResultTokens = transientToolResultTokens,
                modelId = modelId,
            )

            loggingPort.debug(
                TAG,
                "Native tool call completed tool=${request.toolName} finalPayload=${decision.finalResult.take(2000)}"
            )

            if (decision.shouldTrackTransientToolResult) {
                hasTransientToolResults = true
                // Track actual token count of tool result (plus a small buffer for the tool call request)
                transientToolResultTokens += JTokkitTokenCounter.countTokens(
                    decision.finalResult,
                    modelId,
                ) + 30
            }

            if (decision.contextFull) {
                loggingPort.warning(
                    TAG,
                    "Context nearly full after tool result. Appending stop-tools warning. tool=${request.toolName} usedTokens=${decision.totalTokens} window=$currentContextWindow"
                )
                contextFullWarned = true
            }

            if (decision.hasErrorPayload) {
                loggingPort.error(
                    TAG,
                    "Native tool call returned error payload tool=${request.toolName} provider=${request.provider} modelType=${request.modelType} chatId=${request.chatId?.value ?: "<none>"} userMessageId=${request.userMessageId?.value ?: "<none>"} payload=${decision.finalResult.take(2000)}"
                )
            }
            decision.finalResult
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val rootCause = t.rootCause()
            loggingPort.error(
                TAG,
                "Native tool call failed tool=${request.toolName} cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
                rootCause,
            )
            buildToolErrorJson(request.toolName, rootCause)
        }
    }

    @VisibleForTesting
    internal fun forceContextFullWarnedForTest(value: Boolean) {
        contextFullWarned = value
    }

    @VisibleForTesting
    internal suspend fun executeToolSafelyForTest(request: ToolCallRequest): String =
        executeToolSafely(request)

    private fun buildToolErrorJson(toolName: String, throwable: Throwable): String =
        """{"error":"tool_execution_failed","tool":"${escapeJson(toolName)}","exception":"${escapeJson(throwable::class.java.simpleName)}","message":"${escapeJson(throwable.message ?: "Unknown error")}"}"""

    private fun Throwable.rootCause(): Throwable {
        val cause = when (this) {
            is InvocationTargetException -> targetException ?: cause
            else -> cause
        }
        return if (cause == null || cause === this) this else cause.rootCause()
    }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")


    private fun ensureEngineInitializedLocked(
        modelPath: String,
        contextWindow: Int,
        isMultimodal: Boolean
    ) {
        if (engine != null) return

        val cacheDir = cacheDirFor(modelPath)
        initializeEngine(modelPath, contextWindow, cacheDir, isMultimodal)
    }

    private fun initializeEngine(
        modelPath: String,
        contextWindow: Int,
        cacheDir: String?,
        isMultimodal: Boolean
    ) {
        val backend = Backend.GPU()
        val visionBackend = if (isMultimodal) Backend.GPU() else null
        var candidate: Engine? = null

        try {
            Log.d(
                TAG,
                "Initializing LiteRT text engine with GPU backend, " +
                    "contextWindow=$contextWindow memoryBeforeInit=${memorySnapshot()}"
            )

            candidate = createEngine(
                modelPath = modelPath,
                backend = backend,
                visionBackend = visionBackend,
                contextWindow = contextWindow,
                cacheDir = cacheDir
            )

            candidate.initialize()

            engine = candidate
            activeBackendIsNpu = false

            Log.d(
                TAG,
                "LiteRT text engine initialized successfully with GPU backend " +
                    "(isNpu=$activeBackendIsNpu) memoryAfterInit=${memorySnapshot()}"
            )
        } catch (t: Throwable) {
            if (t is CancellationException || t is JavaCancellationException) {
                Log.i(TAG, "LiteRT GPU engine initialization cancelled by user")
                try { candidate?.close() } catch (_: Throwable) {}
                throw t
            }
            Log.w(TAG, "LiteRT GPU backend failed during initialize() memoryAfterFailure=${memorySnapshot()}", t)
            try {
                candidate?.close()
            } catch (_: Throwable) {
            }
            throw IllegalStateException(
                "Failed to initialize LiteRT text engine with GPU backend",
                t
            )
        }
    }

    private fun createEngine(
        modelPath: String,
        backend: Backend,
        visionBackend: Backend?,
        contextWindow: Int,
        cacheDir: String?
    ): Engine {
        return Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = backend,
                visionBackend = visionBackend,
                maxNumTokens = contextWindow,
                cacheDir = cacheDir
            )
        )
    }

    private fun cacheDirFor(modelPath: String): String? {
        return if (modelPath.startsWith("/data/local/tmp")) {
            context.getExternalFilesDir(null)?.absolutePath
        } else {
            null
        }
    }

    /**
     * Closes the current conversation and releases resources.
     * After calling this, a new conversation will be created on next getConversation call.
     */
    override suspend fun closeConversation() {
        mutex.withLock {
            closeConversationLocked()
        }
    }

    private fun closeConversationLocked() {
        Log.d(TAG, "Closing LiteRT conversation memoryBeforeClose=${memorySnapshot()}")

        conversation?.close()
        conversation = null
        conversationPort = null
        currentSignature = null
        hasTransientToolResults = false
        transientToolResultTokens = 0
        contextFullWarned = false
        Log.d(TAG, "Closed LiteRT conversation memoryAfterClose=${memorySnapshot()}")
    }

    override suspend fun setHistory(messages: List<DomainChatMessage>) {
        mutex.withLock {
            // A history is a continuation if the new list starts with the exact same messages
            // as the current list. If it doesn't (e.g. chat switch or message deletion),
            // we must recreate the conversation to ensure context integrity.
            // We only need to close if a conversation is already active; otherwise,
            // getConversation will naturally create it with the correct history.
            val isContinuation = messages == history || (
                messages.size >= history.size &&
                messages.take(history.size) == history
            )

            if (!isContinuation && conversation != null) {
                Log.d(TAG, "History discontinuity detected. Recreating conversation. (oldSize=${history.size}, newSize=${messages.size})")
                closeConversationLocked()
            } else if (isContinuation) {
                Log.d(TAG, "History is a continuation. Reusing conversation. (oldSize=${history.size}, newSize=${messages.size})")
            }

            history = messages.toList<DomainChatMessage>()
        }
    }

    /**
     * Closes the underlying engine and releases all resources.
     * After calling this, the ConversationManager should not be used.
     */
    override suspend fun closeEngine() {
        mutex.withLock {
            closeEngineLocked()
        }
    }

    private fun closeEngineLocked() {
        Log.d(TAG, "Closing LiteRT engine memoryBeforeClose=${memorySnapshot()}")
        closeConversationLocked()
        engine?.close()
        engine = null
        currentModelPath = null
        currentModelType = null
        currentConfigId = null
        currentThinkingEnabled = null
        currentSystemPrompt = defaultSystemPrompt
        currentSamplerConfig = null
        currentSignature = null
        activeBackendIsNpu = false
        Log.d(TAG, "Closed LiteRT engine memoryAfterClose=${memorySnapshot()}")
    }

    private fun memorySnapshot(): String {
        val runtime = Runtime.getRuntime()
        val usedJvmMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val totalJvmMb = runtime.totalMemory() / (1024 * 1024)
        val maxJvmMb = runtime.maxMemory() / (1024 * 1024)
        val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        return "jvmUsedMb=$usedJvmMb jvmTotalMb=$totalJvmMb jvmMaxMb=$maxJvmMb nativeHeapMb=$nativeHeapMb"
    }

    companion object {
        private const val TAG = "ConversationManager"
    }
}
