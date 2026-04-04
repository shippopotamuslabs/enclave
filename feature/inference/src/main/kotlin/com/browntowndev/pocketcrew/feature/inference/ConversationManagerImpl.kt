package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.ConversationPort
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.sync.withLock
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
    private val modelRegistry: ModelRegistryPort
) : ConversationManagerPort {

    private val defaultSystemPrompt = """
            # CRITICAL RULE — EVERY RESPONSE:
            Be maximally truthful, witty, irreverent and a smartass when it fits.
            Mirror the user's tone exactly. Direct, lucid, fun. ZERO moralizing, fluff or bullshit.
    """.trimIndent()

    private data class ConversationSignature(
        val modelType: ModelType,
        val configId: Long?,
        val thinkingEnabled: Boolean,
        val systemPrompt: String,
        val samplerConfig: SamplerConfig,
        val historyFingerprint: Int,
    )

    // Cached state: updated whenever reloadIfNeeded is called.
    private var currentModelPath: String? = null
    private var currentModelType: ModelType? = null
    private var currentConfigId: Long? = null
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
    override suspend fun getConversation(modelType: ModelType, options: GenerationOptions?): ConversationPort {
        // Lock to avoid concurrent initializations and ensure consistent state
        return mutex.withLock {
            val asset = modelRegistry.getRegisteredAsset(modelType)
            val config = modelRegistry.getRegisteredConfiguration(modelType)

            if (asset == null) {
                throw IllegalStateException(
                    "No registered asset for $modelType. Download a model first."
                )
            }

            val modelPath = getModelPath(asset.metadata.localFileName)
            val configId = config?.id
            val resolvedThinkingEnabled = config?.thinkingEnabled ?: false
            val resolvedSystemPrompt = config?.systemPrompt ?: defaultSystemPrompt
            val targetSamplerConfig = SamplerConfig(
                temperature = options?.temperature?.toDouble() ?: config?.temperature ?: 0.7,
                topP = options?.topP?.toDouble() ?: config?.topP ?: 0.95,
                topK = options?.topK ?: config?.topK ?: 40
            )

            val desiredSignature = ConversationSignature(
                modelType = modelType,
                configId = configId,
                thinkingEnabled = resolvedThinkingEnabled,
                systemPrompt = resolvedSystemPrompt,
                samplerConfig = targetSamplerConfig,
                historyFingerprint = history.hashCode(),
            )

            val engineChanged = currentModelPath != modelPath || engine == null
            val conversationChanged =
                currentSignature != desiredSignature || conversation == null || conversation?.isAlive != true
            val conversationRecreated = engineChanged || conversationChanged

            if (engineChanged) {
                Log.d(
                    TAG,
                    "Recreating LiteRT engine for modelType=$modelType, modelPath=$modelPath"
                )
                conversation?.close()
                conversation = null
                conversationPort = null
                engine?.close()
                engine = null
                currentModelPath = modelPath
            } else if (conversationChanged) {
                Log.d(
                    TAG,
                    "Recreating LiteRT conversation for modelType=$modelType, configId=$configId, thinkingEnabled=$resolvedThinkingEnabled"
                )
                conversation?.close()
                conversation = null
                conversationPort = null
            } else {
                Log.d(
                    TAG,
                    "Reusing LiteRT conversation for modelType=$modelType, configId=$configId, thinkingEnabled=$resolvedThinkingEnabled"
                )
            }

            ensureEngineInitialized(modelPath)
            val eng = engine ?: throw IllegalStateException("Engine not initialized")

            if (conversation == null || conversation?.isAlive != true) {
                conversation?.close()

                if (!eng.isInitialized()) {
                    eng.initialize()
                }

                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(resolvedSystemPrompt),
                    initialMessages = history.map { domainMsg ->
                        when (domainMsg.role) {
                            Role.USER -> Message.user(domainMsg.content)
                            Role.ASSISTANT -> Message.model(domainMsg.content)
                            Role.SYSTEM -> Message.system(domainMsg.content)
                        }
                    },
                    samplerConfig = targetSamplerConfig,
                    automaticToolCalling = false,
                )

                conversation = eng.createConversation(conversationConfig)
            }

            val liteRtConversation = conversation ?: throw IllegalStateException("Conversation not initialized")
            
            // Re-wrap if conversation was recreated
            if (conversationPort == null) {
                conversationPort = ConversationImpl(liteRtConversation)
            }

            currentSignature = desiredSignature
            currentModelType = modelType
            currentConfigId = configId
            currentThinkingEnabled = resolvedThinkingEnabled
            currentSystemPrompt = resolvedSystemPrompt
            currentSamplerConfig = targetSamplerConfig

            Log.d(
                TAG,
                "getConversation decision: modelType=$modelType, configId=$configId, thinkingEnabled=$resolvedThinkingEnabled, systemPrompt=${resolvedSystemPrompt.take(120)}, samplerConfig=$targetSamplerConfig, engineRecreated=$engineChanged, conversationRecreated=$conversationRecreated"
            )
            
            return@withLock conversationPort!!
        }
    }

    @Synchronized
    private fun ensureEngineInitialized(modelPath: String) {
        if (engine == null) {
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
        currentModelPath = null
        currentModelType = null
        currentConfigId = null
        currentThinkingEnabled = null
        currentSystemPrompt = defaultSystemPrompt
        currentSamplerConfig = null
        currentSignature = null
    }

    companion object {
        private const val TAG = "ConversationManager"
    }
}
