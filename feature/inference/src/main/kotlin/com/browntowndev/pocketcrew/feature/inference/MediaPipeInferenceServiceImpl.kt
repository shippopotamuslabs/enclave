package com.browntowndev.pocketcrew.feature.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
) : LlmInferencePort {

    companion object {
        private const val TAG = "MediaPipeInference"
    }

    private val mutex = Mutex()

    // Session is stateful — maintains multi-turn conversation context.
    // MediaPipe auto-manages conversation history within a session.
    private var session: LlmSessionPort? = null

    // Cached history to seed new sessions
    private var history: List<DomainChatMessage> = emptyList()

    private fun getOrCreateSession(): Pair<LlmSessionPort, Boolean> {
        session?.let { return it to false }
        
        val newSession = llmInference.createSession(
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTopP(0.95f)
                .setTemperature(0.7f)
                .build()
        )
        session = newSession
        return newSession to true
    }

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> = callbackFlow {
        var isThinkingPhase = false
        val accumulatedThought = StringBuilder()
        val accumulatedText = StringBuilder()

        try {
            val (currentSession, isNewSession) = mutex.withLock {
                getOrCreateSession()
            }
            
            // If it's a new session and we have history, seed it now
            if (isNewSession) {
                mutex.withLock {
                    if (history.isNotEmpty()) {
                        Log.d(TAG, "Seeding session with ${history.size} historical messages")
                        history.forEach { msg ->
                            val label = when (msg.role) {
                                Role.USER -> "User"
                                Role.ASSISTANT -> "Assistant"
                                Role.SYSTEM -> "System"
                            }
                            currentSession.addQueryChunk("$label: ${msg.content}\n")
                        }
                    }
                }
            }
            
            currentSession.addQueryChunk(prompt)

            currentSession.generateResponseAsync { partialResult, done ->
                val chunkText = partialResult ?: ""

                if (chunkText.isNotEmpty()) {
                    // Route text based on <think> tags — same logic as the old Engine impl.
                    // TODO: For production, use a sliding window buffer to handle split tokens
                    //       (e.g., "<th" + "ink>") across chunk boundaries.
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
                // No-op: session lifecycle managed by closeSession()
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

    private suspend fun closeSessionOnly() {
        mutex.withLock {
            try {
                session?.close()
                session = null
            } catch (e: Exception) {
                Log.w(TAG, "Error closing session only", e)
            }
        }
    }

    override suspend fun setHistory(messages: List<DomainChatMessage>) {
        mutex.withLock {
            if (this.history != messages) {
                this.history = messages
                // Close existing session to force re-seeding with new history on next prompt
                // Call internal logic without nested lock
                try {
                    session?.close()
                    session = null
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing session during setHistory", e)
                }
            }
        }
    }

    override fun closeSession() {
        // This is called from outside, likely main thread or DI cleanup.
        // Since it's not suspend, we use a fire-and-forget approach or block if absolutely needed.
        // The interface LlmInferencePort.closeSession() is not suspend.
        // We'll use tryLock or similar to be safe, but session close is usually fast.
        try {
            session?.close()
            session = null
            llmInference.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing session", e)
        }
    }
}
