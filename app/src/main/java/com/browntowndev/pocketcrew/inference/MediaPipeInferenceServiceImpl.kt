package com.browntowndev.pocketcrew.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.inference.llama.ChatMessage
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * LLM inference implementation backed by MediaPipe's LlmInference API.
 * Accepts .task bundle files (the standard MediaPipe/LiteRT model format).
 *
 * MediaPipe LlmInference is the stable, production Google AI Edge API;
 * litertlm.Engine is the lower-level preview API with limited model support.
 */
class MediaPipeInferenceServiceImpl @Inject constructor(
    private val llmInference: LlmInference,
    private val modelType: ModelType,
) : LlmInferencePort {

    companion object {
        private const val TAG = "MediaPipeInference"
    }

    // Session is stateful — maintains multi-turn conversation context.
    // MediaPipe auto-manages conversation history within a session.
    private var session: LlmInferenceSession? = null

    private fun getOrCreateSession(): LlmInferenceSession {
        return session ?: LlmInferenceSession.createFromOptions(
            llmInference,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTopP(0.95f)
                .setTemperature(0.7f)
                .build()
        ).also { session = it }
    }

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> = callbackFlow {
        var isThinkingPhase = false
        val accumulatedThought = StringBuilder()
        val accumulatedText = StringBuilder()

        try {
            val currentSession = getOrCreateSession()
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
                            trySend(InferenceEvent.Thinking(cleanText, accumulatedThought.toString(), modelType))
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
                        trySend(InferenceEvent.Thinking(chunkText, accumulatedThought.toString(), modelType))
                    } else {
                        accumulatedText.append(chunkText)
                        trySend(InferenceEvent.PartialResponse(chunkText, modelType))
                    }
                }

                if (done) {
                    trySend(
                        InferenceEvent.Completed(
                            finalResponse = accumulatedText.toString(),
                            rawFullThought = accumulatedThought.toString().takeIf { it.isNotEmpty() },
                            modelType = modelType
                        )
                    )
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
                session?.close()
                session = null
            }
        }
    }

    private fun closeSessionOnly() {
        try {
            session?.close()
            session = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing session only", e)
        }
    }

    override suspend fun setHistory(messages: List<DomainChatMessage>) {
        // MediaPipe manages its own conversation history within the session
        // This is a no-op for now - MediaPipe may have different persistence needs
        Log.d(TAG, "setHistory called with ${messages.size} messages - not implemented for MediaPipe")
    }

    override fun closeSession() {
        try {
            closeSessionOnly()
            llmInference.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing session", e)
        }
    }
}