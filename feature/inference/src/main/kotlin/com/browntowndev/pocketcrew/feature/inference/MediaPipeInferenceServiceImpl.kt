package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser.LocalToolEnvelope
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * LLM inference implementation backed by MediaPipe's LlmInference API.
 * Accepts .task bundle files (the standard MediaPipe/LiteRT model format).
 *
 * MediaPipe LlmInference is the stable, production Google AI Edge API;
 * litertlm.Engine is the lower-level preview API with limited model support.
 */
class MediaPipeInferenceServiceImpl @Inject constructor(
    private val llmInference: LlmInferenceWrapper,
    @ApplicationContext private val context: Context,
    private val modelType: ModelType,
    private val activeModelProvider: ActiveModelProviderPort,
    private val processThinkingTokens: ProcessThinkingTokensUseCase,
    val orchestrator: LlmToolingOrchestrator,
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

    private data class BufferedResponse(
        val thought: String,
        val text: String,
    )

    private data class BufferedToolPass(
        val thought: String,
        val text: String,
        val thoughtAlreadyEmitted: Boolean,
    )

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
        val activeConfig = activeModelProvider.getActiveConfiguration(modelType)
            ?: throw IllegalStateException("No active configuration for $modelType")

        if (!activeConfig.isLocal) {
            throw IllegalStateException("MediaPipeInferenceService cannot run API models. ModelType $modelType is mapped to an API configuration.")
        }

        val targetTopK = options.topK ?: activeConfig.topK ?: 40
        val targetTopP = options.topP ?: activeConfig.topP?.toFloat() ?: 0.95f
        val targetTemperature = options.temperature ?: activeConfig.temperature?.toFloat() ?: 0.7f
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
                .setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(true)
                        .build()
                )
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
        val retainedImages = mutableListOf<MPImage>()

        try {
            val currentSession = mutex.withLock {
                getOrCreateAndSeedSessionLocked(options)
            }

            options.imageUris.forEach { imageUri ->
                val mpImage = loadMpImage(imageUri)
                retainedImages.add(mpImage)
                currentSession.addImage(mpImage)
            }
            currentSession.addQueryChunk(prompt)

            if (ToolEnvelopeParser.hasLocalToolContract(options.systemPrompt)) {
                executeToolingPrompt(currentSession, prompt, options, targetModelType) { trySend(it) }
                close()
                return@callbackFlow
            }

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

            awaitClose {
                // keep channel open
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            trySend(InferenceEvent.Error(e, targetModelType))
            close()
        } finally {
            retainedImages.forEach { img ->
                try {
                    img.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing MPImage", e)
                }
            }
            retainedImages.clear()
            
            if (closeConversation) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    mutex.withLock {
                        session?.close()
                        session = null
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun executeToolingPrompt(
        session: LlmSessionPort,
        prompt: String,
        options: GenerationOptions,
        targetModelType: ModelType,
        emitEvent: (InferenceEvent) -> Unit,
    ) {
        orchestrator.execute(
            providerName = "MEDIAPIPE",
            initialParams = options,
            tag = TAG,
            onInferencePass = { params, allowToolCall ->
                collectToolPreparationPass(session, targetModelType, emitEvent)
            },
            onToolCallDetected = { pass ->
                ToolEnvelopeParser.extractLocalToolEnvelope(pass.text)?.let { envelope ->
                    ToolCallRequest(
                        toolName = envelope.toolName,
                        argumentsJson = envelope.argumentsJson,
                        provider = "MEDIAPIPE",
                        modelType = targetModelType,
                        chatId = options.chatId,
                        userMessageId = options.userMessageId,
                    )
                }
            },
            onToolResultMapped = { params, _, resultJson ->
                session.addQueryChunk(ToolEnvelopeParser.buildLocalToolResultMessage(resultJson))
                params
            },
            onNoToolCallOnFirstPass = { pass ->
                emitBufferedResponse(pass, targetModelType, emitEvent)
            },
            onFinished = { _, toolCallCount, lastResponse ->
                if (toolCallCount > 0 && lastResponse != null) {
                    emitBufferedResponse(
                        BufferedToolPass(
                            thought = lastResponse.thought,
                            text = lastResponse.text,
                            thoughtAlreadyEmitted = false
                        ),
                        targetModelType,
                        emitEvent
                    )
                }
                emitEvent(InferenceEvent.Finished(targetModelType))
            }
        )
    }

    private suspend fun collectToolPreparationPass(
        session: LlmSessionPort,
        targetModelType: ModelType,
        emitEvent: (InferenceEvent) -> Unit,
    ): BufferedToolPass {
        val buffered = streamSessionResponse(
            session = session,
            targetModelType = targetModelType,
            emitVisible = false,
            emitEvent = emitEvent,
        )
        return BufferedToolPass(
            thought = buffered.thought,
            text = buffered.text,
            thoughtAlreadyEmitted = true,
        )
    }

    private suspend fun streamSessionResponse(
        session: LlmSessionPort,
        targetModelType: ModelType,
        emitVisible: Boolean,
        emitEvent: (InferenceEvent) -> Unit,
    ): BufferedResponse =
        suspendCancellableCoroutine { continuation ->
            val thought = StringBuilder()
            val text = StringBuilder()
            var isThinking = false
            var buffer = ""

            try {
                val future = session.generateResponseAsync { partialResult, done ->
                    try {
                        val chunkText = partialResult ?: ""
                        if (chunkText.isNotEmpty()) {
                            val state = processThinkingTokens(
                                currentBuffer = buffer,
                                newChunk = chunkText,
                                isThinking = isThinking,
                            )
                            buffer = state.buffer
                            isThinking = state.isThinking
                            state.emittedSegments.forEach { segment ->
                                when (segment.kind) {
                                    ProcessThinkingTokensUseCase.SegmentKind.THINKING -> {
                                        thought.append(segment.text)
                                        emitEvent(InferenceEvent.Thinking(segment.text, targetModelType))
                                    }
                                    ProcessThinkingTokensUseCase.SegmentKind.VISIBLE -> {
                                        text.append(segment.text)
                                        if (emitVisible) {
                                            emitEvent(InferenceEvent.PartialResponse(segment.text, targetModelType))
                                        }
                                    }
                                }
                            }
                        }

                        if (done && continuation.isActive) {
                            if (buffer.isNotEmpty()) {
                                if (isThinking) {
                                    thought.append(buffer)
                                    emitEvent(InferenceEvent.Thinking(buffer, targetModelType))
                                } else {
                                    text.append(buffer)
                                    if (emitVisible) {
                                        emitEvent(InferenceEvent.PartialResponse(buffer, targetModelType))
                                    }
                                }
                            }
                            continuation.resume(
                                BufferedResponse(
                                    thought = thought.toString(),
                                    text = text.toString(),
                                )
                            )
                        }
                    } catch (error: Exception) {
                        continuation.resumeWithSafeException(error)
                    }
                }
                continuation.invokeOnCancellation {
                    future.cancel(true)
                }
            } catch (error: Exception) {
                continuation.resumeWithSafeException(error)
            }
        }

    private fun CancellableContinuation<BufferedResponse>.resumeWithSafeException(error: Exception) {
        if (isActive) {
            if (error is CancellationException) {
                cancel(error)
            } else {
                resumeWithException(error)
            }
        }
    }

    private fun emitBufferedResponse(
        response: BufferedToolPass,
        targetModelType: ModelType,
        emitEvent: (InferenceEvent) -> Unit,
    ) {
        if (response.thought.isNotBlank() && !response.thoughtAlreadyEmitted) {
            emitEvent(InferenceEvent.Thinking(response.thought, targetModelType))
        }
        emitVisibleText(response.text, targetModelType, emitEvent)
    }

    private fun emitVisibleText(
        text: String,
        targetModelType: ModelType,
        emitEvent: (InferenceEvent) -> Unit,
    ) {
        if (text.isNotBlank()) {
            emitEvent(InferenceEvent.PartialResponse(text, targetModelType))
        }
    }

    private suspend fun loadMpImage(imageUri: String): MPImage {
        val bitmap = ImageDownscaler.downscale(context, imageUri)
        return BitmapImageBuilder(bitmap).build()
    }
}
