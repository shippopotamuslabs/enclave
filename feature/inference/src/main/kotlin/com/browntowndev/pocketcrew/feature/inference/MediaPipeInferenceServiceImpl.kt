package com.browntowndev.pocketcrew.feature.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
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
) : LlmInferencePort {

    companion object {
        private const val TAG = "MediaPipeInference"
    }

    private val mutex = Mutex()

    // Session is stateful — maintains multi-turn conversation context.
    private var session: LlmSessionPort? = null

    // Cached history to seed new sessions
    private var history: List<DomainChatMessage> = emptyList()

    /**
     * Gets the current session or creates and seeds a new one.
     * This operation is atomic to ensure history context is correctly injected.
     */
    private fun getOrCreateAndSeedSessionLocked(): LlmSessionPort {
        session?.let { return it }
        
        val newSession = llmInference.createSession(
            com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTopP(0.95f)
                .setTemperature(0.7f)
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
        return newSession
    }

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> = callbackFlow {
        val modelType = ModelType.FAST
        var isThinkingPhase = false
        val accumulatedThought = StringBuilder()
        val accumulatedText = StringBuilder()

        try {
            val currentSession = mutex.withLock {
                getOrCreateAndSeedSessionLocked()
            }
            
            currentSession.addQueryChunk(prompt)

            currentSession.generateResponseAsync { partialResult, done ->
                val chunkText = partialResult ?: ""

                if (chunkText.isNotEmpty()) {
                    if (chunkText.contains("<think>")) {
                        isThinkingPhase = true
                        val cleanText = chunkText.replace("<think>", "")
                        if (cleanText.isNotEmpty()) {
                            accumulatedThought.append(cleanText)
                            trySend(InferenceEvent.Thinking(cleanText, modelType))
                        }
                    } else if (chunkText.contains("</think>")) {
                        isThinkingPhase = false
                        val cleanText = chunkText.replace("</think>", "")
                        if (cleanText.isNotEmpty()) {
                            accumulatedText.append(cleanText)
                            trySend(InferenceEvent.PartialResponse(cleanText, modelType))
                        }
                    } else if (isThinkingPhase) {
                        accumulatedThought.append(chunkText)
                        trySend(InferenceEvent.Thinking(chunkText, modelType))
                    } else {
                        accumulatedText.append(chunkText)
                        trySend(InferenceEvent.PartialResponse(chunkText, modelType))
                    }
                }

                if (done) {
                    trySend(InferenceEvent.Finished(modelType))
                    close()
                }
            }

            awaitClose {
                // No-op: session lifecycle managed by closeSession() or finally block
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            trySend(InferenceEvent.Error(e, modelType))
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

    override suspend fun setHistory(messages: List<DomainChatMessage>) {
        mutex.withLock {
            if (this.history != messages) {
                this.history = messages.toList() // Defensive copy
                // Invalidate session to force re-seeding on next prompt
                session?.close()
                session = null
            }
        }
    }

    override suspend fun closeSession() {
        // Constitutional Fix: use mutex.withLock in suspend function.
        mutex.withLock {
            try {
                session?.close()
                session = null
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

        try {
            val currentSession = mutex.withLock {
                getOrCreateAndSeedSessionLocked()
            }
            
            currentSession.addQueryChunk(prompt)

            currentSession.generateResponseAsync { partialResult, done ->
                val chunkText = partialResult ?: ""

                if (chunkText.isNotEmpty()) {
                    if (chunkText.contains("<think>")) {
                        isThinkingPhase = true
                        val cleanText = chunkText.replace("<think>", "")
                        if (cleanText.isNotEmpty()) {
                            accumulatedThought.append(cleanText)
                            trySend(InferenceEvent.Thinking(cleanText, targetModelType))
                        }
                    } else if (chunkText.contains("</think>")) {
                        isThinkingPhase = false
                        val cleanText = chunkText.replace("</think>", "")
                        if (cleanText.isNotEmpty()) {
                            accumulatedText.append(cleanText)
                            trySend(InferenceEvent.PartialResponse(cleanText, targetModelType))
                        }
                    } else if (isThinkingPhase) {
                        accumulatedThought.append(chunkText)
                        trySend(InferenceEvent.Thinking(chunkText, targetModelType))
                    } else {
                        accumulatedText.append(chunkText)
                        trySend(InferenceEvent.PartialResponse(chunkText, targetModelType))
                    }
                }

                if (done) {
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
