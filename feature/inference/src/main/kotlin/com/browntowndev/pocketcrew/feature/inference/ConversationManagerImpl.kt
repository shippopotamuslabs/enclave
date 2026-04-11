package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.os.Debug
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tflite.gpu.support.TfLiteGpu
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.ConversationPort
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject


/**
 * Implementation of [ConversationManagerPort] that manages LiteRT conversation lifecycle.
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
    private val toolExecutor: ToolExecutorPort? = null,
) : ConversationManagerPort {

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
    )

    // Cached state: updated whenever reloadIfNeeded is called.
    private var currentModelPath: String? = null
    private var currentModelType: ModelType? = null
    private var currentConfigId: LocalModelConfigurationId? = null
    private var currentThinkingEnabled: Boolean? = null
    private var currentSystemPrompt: String = defaultSystemPrompt
    private var currentSamplerConfig: SamplerConfig? = null
    private var currentSignature: ConversationSignature? = null

    // Conversation is stateful and maintains context for multi-turn chats.
    @Volatile
    private var conversation: Conversation? = null

    // Cache the ConversationPort wrapper to return the same instance on repeated calls
    @Volatile
    private var conversationPort: ConversationPort? = null

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

    private fun getModelPath(filename: String): String {
        // Use getExternalFilesDir to match ModelFileScanner's directory choice
        val modelsDir = java.io.File(context.getExternalFilesDir(null), "models")
        return java.io.File(modelsDir, filename).absolutePath
    }

    private val mutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Returns the active conversation, initializing it if needed.
     * Thread-safe: concurrent calls will return the same conversation instance.
     *
     * @param modelType The type of model to get the configuration for.
     * @param options Per-request generation options (e.g., sampler overrides).
     * @return The active ConversationPort instance wrapping the LiteRT Conversation
     * @throws IllegalStateException if no model is registered for this model type
     */
    override suspend fun getConversation(modelType: ModelType, options: GenerationOptions?): ConversationPort = withContext(Dispatchers.IO) {
        // Lock to avoid concurrent initializations and ensure consistent state
        mutex.withLock {
            val activeConfig = activeModelProvider.getActiveConfiguration(modelType)
                ?: throw IllegalStateException("No active configuration for $modelType")

            if (!activeConfig.isLocal) {
                throw IllegalStateException("ConversationManager cannot run API models. ModelType $modelType is mapped to an API configuration.")
            }

            val configId = activeConfig.id as LocalModelConfigurationId
            val asset = localModelRepository.getAssetByConfigId(configId)
                ?: throw IllegalStateException("No registered asset for config ${activeConfig.id}. Download a model first.")

            val modelPath = getModelPath(asset.metadata.localFileName)
            val resolvedThinkingEnabled = false // LiteRT currently doesn't support reasoning budget
            val resolvedSystemPrompt = activeConfig.systemPrompt ?: defaultSystemPrompt
            val targetSamplerConfig = SamplerConfig(
                temperature = options?.temperature?.toDouble() ?: activeConfig.temperature ?: 0.7,
                topP = options?.topP?.toDouble() ?: activeConfig.topP ?: 0.95,
                topK = options?.topK ?: activeConfig.topK ?: 40
            )
            val hasSearchTool = options?.availableTools?.contains(ToolDefinition.TAVILY_WEB_SEARCH) == true

            val desiredSignature = ConversationSignature(
                modelType = modelType,
                configId = configId,
                thinkingEnabled = resolvedThinkingEnabled,
                systemPrompt = resolvedSystemPrompt,
                temperature = targetSamplerConfig.temperature,
                topP = targetSamplerConfig.topP,
                topK = targetSamplerConfig.topK,
                hasSearchTool = hasSearchTool,
            )

            val engineChanged = currentModelPath != modelPath || engine == null
            val conversationChanged =
                currentSignature != desiredSignature || conversation == null || conversation?.isAlive != true
            val conversationRecreated = engineChanged || conversationChanged

            if (engineChanged) {
                Log.d(
                    TAG,
                    "Recreating LiteRT engine for modelType=$modelType, modelPath=$modelPath memory=${memorySnapshot()}"
                )
                closeEngineLocked()
                currentModelPath = modelPath
            } else if (conversationChanged) {
                Log.d(
                    TAG,
                    "Recreating LiteRT conversation for modelType=$modelType, configId=$configId, thinkingEnabled=$resolvedThinkingEnabled memory=${memorySnapshot()}"
                )
                closeConversationLocked()
            } else {
                Log.d(
                    TAG,
                    "Reusing LiteRT conversation for modelType=$modelType, configId=$configId, thinkingEnabled=$resolvedThinkingEnabled memory=${memorySnapshot()}"
                )
            }

            ensureEngineInitializedLocked(modelPath, modelType, activeConfig.contextWindow ?: 2048)
            val eng = engine ?: throw IllegalStateException("Engine not initialized")

            if (conversation == null || conversation?.isAlive != true) {
                conversation?.close()

                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(resolvedSystemPrompt),
                    initialMessages = history.map { domainMsg ->
                        when (domainMsg.role) {
                            Role.USER -> Message.user(domainMsg.content)
                            Role.ASSISTANT -> Message.model(domainMsg.content)
                            Role.SYSTEM -> Message.system(domainMsg.content)
                        }
                    },
                    // NPU backends don't support custom sampler configs.
                    // Passing one causes redundant CPU-side sampling allocation.
                    samplerConfig = if (activeBackendIsNpu) null else targetSamplerConfig,
                    automaticToolCalling = true,
                    tools = if (hasSearchTool && toolExecutor != null) {
                        listOf(tool(LocalSearchToolset(toolExecutor, modelType)))
                    } else {
                        emptyList()
                    }
                )

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
            currentSamplerConfig = targetSamplerConfig

            Log.d(
                TAG,
                "getConversation decision: modelType=$modelType, configId=$configId, thinkingEnabled=$resolvedThinkingEnabled, systemPrompt=${resolvedSystemPrompt.take(120)}, samplerConfig=$targetSamplerConfig, engineRecreated=$engineChanged, conversationRecreated=$conversationRecreated, memory=${memorySnapshot()}"
            )
            
            return@withLock conversationPort!!
        }
    }

    /**
     * ToolSet for local inference models to access device capabilities.
     * Maps LiteRT native tool calls to the domain [ToolExecutorPort].
     */
    private inner class LocalSearchToolset(
        private val toolExecutor: ToolExecutorPort,
        private val modelType: ModelType,
    ) : ToolSet {
        @Tool(description = "Search the web for information.")
        fun tavily_web_search(
            @ToolParam(description = "The search query.") query: String
        ): String {
            Log.d(TAG, "Executing native tool call: tavily_web_search with query: $query")
            return runBlocking(Dispatchers.IO) {
                val request = ToolCallRequest(
                    toolName = ToolDefinition.TAVILY_WEB_SEARCH.name,
                    argumentsJson = SearchToolSupport.buildArgumentsJson(query),
                    provider = "LITERT",
                    modelType = modelType
                )
                toolExecutor.execute(request).resultJson
            }
        }
    }


    private fun ensureEngineInitializedLocked(
        modelPath: String,
        modelType: ModelType,
        contextWindow: Int
    ) {
        if (engine != null) return

        val cacheDir = cacheDirFor(modelPath)

        if (modelType == ModelType.VISION) {
            initializeVisionEngine(modelPath, contextWindow, cacheDir)
        } else {
            initializeTextEngine(modelPath, contextWindow, cacheDir)
        }
    }

    private fun initializeVisionEngine(
        modelPath: String,
        contextWindow: Int,
        cacheDir: String?
    ) {
        val backend = Backend.GPU()

        try {
                Log.d(
                    TAG,
                    "Initializing LiteRT vision engine with GPU backend, " +
                        "contextWindow=$contextWindow memoryBeforeInit=${memorySnapshot()}"
                )

            engine = createEngine(
                modelPath = modelPath,
                backend = backend,
                visionBackend = backend,
                contextWindow = contextWindow,
                cacheDir = cacheDir
            )

            engine?.initialize()
            activeBackendIsNpu = false

            Log.d(TAG, "LiteRT vision engine initialized successfully with GPU backend memoryAfterInit=${memorySnapshot()}")
        } catch (t: Throwable) {
            try {
                engine?.close()
            } catch (_: Throwable) {
            }
            
            Log.w(TAG, "LiteRT vision engine failed during initialize() memoryAfterFailure=${memorySnapshot()}", t)
            throw t
        }
    }

    private fun initializeTextEngine(
        modelPath: String,
        contextWindow: Int,
        cacheDir: String?
    ) {
        val useGpu = isGpuBackendAvailable()
        val backendName = if (useGpu) "GPU" else "CPU"
        val backend = if (useGpu) Backend.GPU() else Backend.CPU()
        var candidate: Engine? = null

        try {
            Log.d(
                TAG,
                "Initializing LiteRT text engine with $backendName backend, " +
                    "contextWindow=$contextWindow memoryBeforeInit=${memorySnapshot()}"
            )

            candidate = createEngine(
                modelPath = modelPath,
                backend = backend,
                visionBackend = null,
                contextWindow = contextWindow,
                cacheDir = cacheDir
            )

            candidate.initialize()

            engine = candidate
            activeBackendIsNpu = false

            Log.d(
                TAG,
                "LiteRT text engine initialized successfully with $backendName backend " +
                    "(isNpu=$activeBackendIsNpu) memoryAfterInit=${memorySnapshot()}"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "LiteRT $backendName backend failed during initialize() memoryAfterFailure=${memorySnapshot()}", t)
            try {
                candidate?.close()
            } catch (_: Throwable) {
            }
            throw IllegalStateException(
                "Failed to initialize LiteRT text engine with $backendName backend",
                t
            )
        }
    }

    private fun isGpuBackendAvailable(): Boolean {
        return try {
            val available = Tasks.await(TfLiteGpu.isGpuDelegateAvailable(context))
            Log.d(TAG, "TfLiteGpu.isGpuDelegateAvailable returned $available")
            available
        } catch (t: Throwable) {
            Log.w(TAG, "TfLiteGpu.isGpuDelegateAvailable failed; falling back to CPU", t)
            false
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
        Log.d(TAG, "Closed LiteRT conversation memoryAfterClose=${memorySnapshot()}")
    }

    override suspend fun setHistory(messages: List<DomainChatMessage>) {
        mutex.withLock {
            // A history is a continuation if the new list starts with the exact same messages
            // as the current list. If it doesn't (e.g. chat switch or message deletion),
            // we must recreate the conversation to ensure context integrity.
            // We only need to close if a conversation is already active; otherwise,
            // getConversation will naturally create it with the correct history.
            val isContinuation = this.history == messages || (
                this.history.isNotEmpty() &&
                messages.size >= this.history.size &&
                messages.take(this.history.size) == this.history
            )

            if (!isContinuation && conversation != null) {
                Log.d(TAG, "History discontinuity detected. Recreating conversation. (oldSize=${this.history.size}, newSize=${messages.size})")
                closeConversationLocked()
            } else if (isContinuation) {
                Log.d(TAG, "History is a continuation. Reusing conversation. (oldSize=${this.history.size}, newSize=${messages.size})")
            }

            this.history = messages.toList()
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
