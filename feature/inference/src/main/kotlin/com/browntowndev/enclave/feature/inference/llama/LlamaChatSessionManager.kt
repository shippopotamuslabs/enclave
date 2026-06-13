package com.browntowndev.enclave.feature.inference.llama

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.GenerationEvent
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.LlamaModelConfig
import com.browntowndev.enclave.domain.port.inference.LlamaEnginePort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import com.browntowndev.enclave.domain.model.chat.ChatMessage as DomainChatMessage

/**
 * Manages the lifecycle of llama.cpp chat sessions.
 * Keeps conversation state separate from raw engine lifecycle.
 */
class LlamaChatSessionManager
    @Inject
    constructor(
        private val engine: LlamaEnginePort,
    ) {
        /**
         * Initialize the engine with the given model configuration.
         */
        suspend fun initializeEngine(config: LlamaModelConfig) {
            engine.initialize(config)
        }

        /**
         * Start a new conversation with an optional system prompt.
         */
        suspend fun startNewConversation(systemPrompt: String? = null) {
            engine.startConversation(systemPrompt)
        }

        /**
         * Send a user message to the conversation.
         */
        suspend fun sendUserMessage(text: String) {
            engine.appendMessage(DomainChatMessage(role = Role.USER, content = text))
        }

        /**
         * Replace the entire conversation history with the given messages.
         * Used for rehydrating context from database on new session.
         *
         * @param messages List of messages to set as history (excluding system prompt)
         */
        suspend fun setHistory(messages: List<DomainChatMessage>) {
            engine.setHistory(messages)
        }

        /**
         * Stream the assistant's response.
         */
        fun streamAssistantResponse(): Flow<GenerationEvent> = engine.generate()

        /**
         * Stream the assistant's response with per-request GenerationOptions.
         */
        fun streamAssistantResponseWithOptions(
            options: GenerationOptions,
            continuation: Boolean = false,
            prevMessageCount: Int = 0,
        ): Flow<GenerationEvent> =
            engine.generateWithOptions(
                options = options,
                continuation = continuation,
                prevMessageCount = prevMessageCount,
            )

        /**
         * Total KV cache token count across all turns in this session.
         * Grows each turn; use instead of n_prompt_tokens for full-context input token stats.
         */
        suspend fun readContextUsage(): Int = engine.contextUsage()

        /**
         * Stop the current generation if in progress.
         */
        suspend fun stopCurrentGeneration() {
            engine.stopGeneration()
        }

        /**
         * Clear the conversation with an optional new system prompt.
         */
        suspend fun clearConversation(systemPrompt: String? = null) {
            engine.resetConversation(systemPrompt)
        }

        /**
         * Shutdown the engine and release all resources.
         */
        suspend fun shutdown() {
            engine.unload()
        }
    }
