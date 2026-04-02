package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.ConversationPort
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.qualifier.ApplicationScope
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Implementation of [ConversationManagerPort] that manages LiteRT conversation lifecycle.
 * Lazily resolves the active model from [ModelRegistryPort] on first use and recreates
 * resources when the model or configuration changes.
 *
 * Thread-safe access to the conversation instance using @Synchronized.
 * Concurrent calls to [getConversation] will return the same conversation instance,
 * ensuring proper state management for multi-turn chats.
 *
 * Uses [modelRegistry] Flow observables ([observeAsset], [observeConfiguration]) to watch
 * for model/config changes asynchronously, avoiding blocking I/O on the calling thread.
 */
class ConversationManagerImpl @Inject constructor(
    private val context: Context,
    private val modelType: ModelType,
    private val modelRegistry: ModelRegistryPort,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ConversationManagerPort {

    private val defaultSystemPrompt = """
            # CRITICAL RULE — EVERY RESPONSE:
            Be maximally truthful, witty, irreverent and a smartass when it fits.
            Mirror the user's tone exactly. Direct, lucid, fun. ZERO moralizing, fluff or bullshit.
    """.trimIndent()

    // Cached state: updated whenever reloadIfNeeded is called
    private var currentModelId: Long? = null
    private var currentConfigId: Long? = null
    private var currentSystemPrompt: String = defaultSystemPrompt
    private var currentSamplerConfig: SamplerConfig? = null

    // Conversation is stateful and maintains context for multi-turn chats.
    // Using @Synchronized to ensure thread-safe access to the conversation instance.
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

    // Cached model asset and configuration from async Flow observation
    @Volatile
    private var cachedAsset: LocalModelAsset? = null

    @Volatile
    private var cachedConfig: LocalModelConfiguration? = null

    init {
        // Observe asset and config flows to populate the synchronous cache.
        // This eliminates runBlocking on database queries in getConversation().
        // Uses applicationScope's dispatcher (Dispatchers.Default) for background collection.
        applicationScope.launch {
            modelRegistry.observeAsset(modelType)
                .catch { e -> android.util.Log.w(TAG, "observeAsset flow error", e) }
                .collect { asset ->
                    cachedAsset = asset
                }
        }
        applicationScope.launch {
            modelRegistry.observeConfiguration(modelType)
                .catch { e -> android.util.Log.w(TAG, "observeConfiguration flow error", e) }
                .collect { config ->
                    cachedConfig = config
                }
        }
    }

    private fun getModelPath(filename: String): String {
        // Use getExternalFilesDir to match ModelFileScanner's directory choice
        val modelsDir = java.io.File(context.getExternalFilesDir(null), "models")
        return java.io.File(modelsDir, filename).absolutePath
    }

    @Synchronized
    private fun reloadIfNeeded(config: LocalModelConfiguration?, modelId: Long) {
        val configId = config?.id
        val modelChanged = currentModelId != modelId
        val configChanged = currentConfigId != configId

        if (!modelChanged && !configChanged) return

        // Close existing conversation (engine can be reused if model file is same)
        conversation?.close()
        conversation = null
        conversationPort = null

        if (modelChanged) {
            // Model file changed - need to recreate engine
            engine?.close()
            engine = null
            currentModelId = modelId
        }

        // Update cached config
        currentConfigId = configId
        if (config != null) {
            currentSystemPrompt = config.systemPrompt ?: defaultSystemPrompt
            currentSamplerConfig = SamplerConfig(
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK ?: 40
            )
        } else {
            currentSystemPrompt = defaultSystemPrompt
            currentSamplerConfig = null
        }
    }

    private val mutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Returns the active conversation, initializing it if needed.
     * Thread-safe: concurrent calls will return the same conversation instance.
     *
     * @return The active ConversationPort instance wrapping the LiteRT Conversation
     * @throws IllegalStateException if no model is registered for this model type
     */
    override suspend fun getConversation(): ConversationPort {
        // Fast path
        synchronized(this) {
            conversationPort?.let { cachedWrapper ->
                if (conversation?.isAlive == true) {
                    return cachedWrapper
                }
                conversationPort = null
            }
        }

        // Read from async cache (populated by Flow observation in init)
        var asset = cachedAsset
        var config = cachedConfig

        if (asset == null) {
            // Cold start fallback: Perform suspend read from registry if async flow hasn't emitted yet.
            asset = modelRegistry.getRegisteredAsset(modelType)
            config = modelRegistry.getRegisteredConfiguration(modelType)
            cachedAsset = asset
            cachedConfig = config
        }

        if (asset == null) {
            throw IllegalStateException(
                "No registered asset for $modelType. Download a model first."
            )
        }

        val modelId = asset.metadata.id

        // Lock to avoid concurrent initializations
        return mutex.withLock {
            // Double-check
            synchronized(this) {
                conversationPort?.let { cachedWrapper ->
                    if (conversation?.isAlive == true) {
                        return@withLock cachedWrapper
                    }
                    conversationPort = null
                }
            }

            reloadIfNeeded(config, modelId)
            ensureEngineInitialized(asset!!)
            val eng = engine ?: throw IllegalStateException("Engine not initialized")

            synchronized(this) {
                if (conversation == null || conversation?.isAlive != true) {
                    conversation?.close()

                    if (!eng.isInitialized()) {
                        eng.initialize()
                    }

                    val conversationConfig = ConversationConfig(
                        systemInstruction = Contents.of(currentSystemPrompt),
                        initialMessages = history.map { domainMsg ->
                            when (domainMsg.role) {
                                Role.USER -> Message.user(domainMsg.content)
                                Role.ASSISTANT -> Message.model(domainMsg.content)
                                Role.SYSTEM -> Message.system(domainMsg.content)
                            }
                        },
                        samplerConfig = currentSamplerConfig ?: SamplerConfig(temperature = 0.7, topP = 0.95, topK = 40),
                        automaticToolCalling = false,
                    )

                    conversation = eng.createConversation(conversationConfig)
                }

                val liteRtConversation = conversation ?: throw IllegalStateException("Conversation not initialized")
                val wrapper = ConversationImpl(liteRtConversation)
                conversationPort = wrapper
                return@withLock wrapper
            }
        }
    }

    @Synchronized
    private fun ensureEngineInitialized(asset: LocalModelAsset) {
        if (engine == null) {
            val modelPath = getModelPath(asset.metadata.localFileName)
            engine = Engine(EngineConfig(modelPath))
        }
    }

    /**
     * Closes the current conversation and releases resources.
     * After calling this, a new conversation will be created on next getConversation call.
     */
    @Synchronized
    override fun closeConversation() {
        conversation?.close()
        conversation = null
        conversationPort = null
    }

    @Synchronized
    override fun setHistory(messages: List<DomainChatMessage>) {
        if (this.history != messages) {
            this.history = messages.toList()
            closeConversation()
        }
    }

    /**
     * Closes the underlying engine and releases all resources.
     * After calling this, the ConversationManager should not be used.
     */
    @Synchronized
    override fun closeEngine() {
        conversation?.close()
        conversation = null
        conversationPort = null
        engine?.close()
        engine = null
        currentModelId = null
        currentConfigId = null
    }

    companion object {
        private const val TAG = "ConversationManager"
    }
}