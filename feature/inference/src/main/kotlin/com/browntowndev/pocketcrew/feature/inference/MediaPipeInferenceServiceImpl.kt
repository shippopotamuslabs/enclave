package com.browntowndev.pocketcrew.feature.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * LLM inference implementation backed by MediaPipe's LlmInference API.
 * Accepts .task bundle files (the standard MediaPipe/LiteRT model format).
 *
 * MediaPipe LlmInference is the stable, production Google AI Edge API;
 * litertlm.Engine is the lower-level preview API with limited model support.
 */
class MediaPipeInferenceServiceImpl @Inject constructor(
    private val llmInference: LlmInferenceWrapper,
    private val modelType: ModelType,
    private val modelRegistry: ModelRegistryPort,
    private val processThinkingTokens: ProcessThinkingTokensUseCase,
) : LlmInferencePort {

    companion object {
        private const val TAG = "MediaPipeInference"
    }

    private data class SessionSignature(
        val topK: Int,
        val topP: Float,
        val temperature: Float,
        val reasoningBudget: Int,
        val historyFingerprint: Int,
    )

    private val mutex = Mutex()

    // Session is stateful — maintains multi-turn conversation context.
    private var session: LlmSessionPort? = null

    // Cache the signature of the currently active session
    private var currentSignature: SessionSignature? = null

    // Cached history to seed new sessions
    private var history: List<DomainChatMessage> = emptyList()

    /**
     * Gets the current session or creates and seeds a new one.
     * This operation is atomic to ensure history context is correctly injected.
     */
    private suspend fun getOrCreateAndSeedSessionLocked(options: GenerationOptions): LlmSessionPort {
        val config = modelRegistry.getRegisteredConfiguration(modelType)

        val targetTopK = options.topK ?: config?.topK ?: 40
        val targetTopP = options.topP ?: config?.topP?.toFloat() ?: 0.95f
        val targetTemperature = options.temperature ?: config?.temperature?.toFloat() ?: 0.7f
        val targetReasoningBudget = options.reasoningBudget

        val newSignature = SessionSignature(
            topK = targetTopK,
            topP = targetTopP,
            temperature = targetTemperature,
            reasoningBudget = targetReasoningBudget,
            historyFingerprint = history.hashCode(),
        )

        if (session != null && currentSignature != newSignature) {
            Log.d(TAG, "Options or history changed, recreating MediaPipe session")
            session?.close()
            session = null
            currentSignature = null
        }

        session?.let { return it }
        
        Log.d(TAG, "Creating new MediaPipe session with options: $newSignature")
        val newSession = llmInference.createSession(
            com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(targetTopK)
                .setTopP(targetTopP)
                .setTemperature(targetTemperature)
                .build()
        )

        if (history.isNotEmpty()) {
            Log.d(TAG, "Seeding new session with ${history.size} historical messages")
            history.forEach { msg ->
                val label = when (msg.role) {
                    Role.USER -> "User"
                    Role.ASSISTANT -> "Assistant"
                    Role.SYSTEM -> "System"
                }
                newSession.addQueryChunk("$label: ${msg.content}\n")
            }
        }

        session = newSession
        currentSignature = newSignature
        return newSession
    }

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> =
        sendPrompt(prompt, GenerationOptions(reasoningBudget = 0), closeConversation)

    override suspend fun setHistory(messages: List<DomainChatMessage>) {
        mutex.withLock {
            if (this.history != messages) {
                this.history = messages.toList() // Defensive copy
                // Session will be closed/recreated on next prompt if history fingerpint changes
            }
        }
    }

    override suspend fun closeSession() {
        // Constitutional Fix: use mutex.withLock in suspend function.
        mutex.withLock {
            try {
                session?.close()
                session = null
                currentSignature = null
                llmInference.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing session", e)
            }
        }
    }

    override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> = callbackFlow {
        val targetModelType = options.modelType ?: ModelType.FAST
        var isThinkingPhase = false
        val accumulatedThought = StringBuilder()
        val accumulatedText = StringBuilder()
        var buffer = ""

        try {
            val currentSession = mutex.withLock {
                getOrCreateAndSeedSessionLocked(options)
            }
            
            currentSession.addQueryChunk(prompt)

            currentSession.generateResponseAsync { partialResult, done ->
                val chunkText = partialResult ?: ""

                if (chunkText.isNotEmpty()) {
                    val state = processThinkingTokens(
                        currentBuffer = buffer,
                        newChunk = chunkText,
                        isThinking = isThinkingPhase
                    )
                    buffer = state.buffer
                    isThinkingPhase = state.isThinking

                    state.emittedSegments.forEach { segment ->
                        when (segment.kind) {
                            ProcessThinkingTokensUseCase.SegmentKind.THINKING -> {
                                accumulatedThought.append(segment.text)
                                trySend(InferenceEvent.Thinking(segment.text, targetModelType))
                            }
                            ProcessThinkingTokensUseCase.SegmentKind.VISIBLE -> {
                                accumulatedText.append(segment.text)
                                trySend(InferenceEvent.PartialResponse(segment.text, targetModelType))
                            }
                        }
                    }
                }

                if (done) {
                    if (buffer.isNotEmpty()) {
                        if (isThinkingPhase) {
                            accumulatedThought.append(buffer)
                            trySend(InferenceEvent.Thinking(buffer, targetModelType))
                        } else {
                            accumulatedText.append(buffer)
                            trySend(InferenceEvent.PartialResponse(buffer, targetModelType))
                        }
                    }
                    trySend(InferenceEvent.Finished(targetModelType))
                    close()
                }
            }

            awaitClose { }
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            trySend(InferenceEvent.Error(e, targetModelType))
            close()
        } finally {
            if (closeConversation) {
                mutex.withLock {
                    session?.close()
                    session = null
                }
            }
        }
    }
}
