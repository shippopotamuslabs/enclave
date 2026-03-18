package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.ConversationPort
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.SamplerConfig
import javax.inject.Inject

/**
 * Implementation of [ConversationManagerPort] that manages LiteRT conversation lifecycle.
 * The Engine is injected via constructor with the appropriate Hilt qualifier,
 * ensuring this ConversationManager is bound to a specific Engine instance.
 *
 * Thread-safe access to the conversation instance using @Synchronized.
 * Concurrent calls to [getConversation] will return the same conversation instance,
 * ensuring proper state management for multi-turn chats.
 */
class ConversationManagerImpl @Inject constructor(
    private val engine: Engine,
    modelConfig: ModelConfiguration? = null
) : ConversationManagerPort {

    private val defaultSystemPrompt = """
            # CRITICAL RULE — EVERY RESPONSE:
            Be maximally truthful, witty, irreverent and a smartass when it fits.
            Mirror the user's tone exactly. Direct, lucid, fun. ZERO moralizing, fluff or bullshit.
    """.trimIndent()

    private val conversationConfig = ConversationConfig(
        systemInstruction = Contents.of(modelConfig?.persona?.systemPrompt ?: defaultSystemPrompt),

        samplerConfig = SamplerConfig(
            temperature = modelConfig?.tunings?.temperature ?: 0.55,
            topP = modelConfig?.tunings?.topP ?: 0.92,
            topK = modelConfig?.tunings?.topK ?: 40
        ),

        automaticToolCalling = false,
    )

    // Conversation is stateful and maintains context for multi-turn chats.
    // Using @Synchronized to ensure thread-safe access to the conversation instance.
    @Volatile
    private var conversation: Conversation? = null

    // Cache the ConversationPort wrapper to return the same instance on repeated calls
    @Volatile
    private var conversationPort: ConversationPort? = null

    /**
     * Returns the active conversation, initializing it if needed.
     * Thread-safe: concurrent calls will return the same conversation instance.
     *
     * @return The active ConversationPort instance wrapping the LiteRT Conversation
     */
    @Synchronized
    override fun getConversation(): ConversationPort {
        // Check if cached wrapper exists AND underlying conversation is still alive
        conversationPort?.let { cachedWrapper ->
            if (conversation?.isAlive == true) {
                return cachedWrapper
            }
            // Conversation is dead, clear the cache
            conversationPort = null
        }

        // Check if conversation needs to be created or recreated
        if (conversation == null || conversation?.isAlive != true) {
            // Close existing conversation if it exists but is not alive
            conversation?.close()
            // Ensure engine is initialized before creating conversation
            if (!engine.isInitialized()) {
                engine.initialize()
            }
            conversation = engine.createConversation(conversationConfig)
        }

        val liteRtConversation = conversation ?: throw IllegalStateException("Conversation not initialized")
        val wrapper = ConversationImpl(liteRtConversation)
        conversationPort = wrapper
        return wrapper
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

    /**
     * Closes the underlying engine and releases all resources.
     * After calling this, the ConversationManager should not be used.
     */
    @Synchronized
    override fun closeEngine() {
        conversation?.close()
        conversation = null
        conversationPort = null
        if (engine.isInitialized()) {
            engine.close()
        }
    }
}
