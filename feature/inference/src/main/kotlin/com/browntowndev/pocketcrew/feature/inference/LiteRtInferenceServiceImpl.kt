package com.browntowndev.pocketcrew.feature.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase.SegmentKind
import com.browntowndev.pocketcrew.domain.util.TavilyResultParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException as JavaCancellationException
import javax.inject.Inject

class LiteRtInferenceServiceImpl @Inject constructor(
    private val conversationManager: ConversationManager,
    private val processThinkingTokens: ProcessThinkingTokensUseCase,
    private val toolExecutionEventPort: ToolExecutionEventPort,
    private val modelType: ModelType,
) : LlmInferencePort {
    companion object {
        private const val TAG = "LiteRtInferenceService"
    }

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
        return sendPrompt(prompt, GenerationOptions(reasoningBudget = 0, modelType = modelType), closeConversation)
    }

    override suspend fun closeSession() {
        conversationManager.closeConversation()
        conversationManager.closeEngine()
    }

    override suspend fun setHistory(messages: List<DomainChatMessage>) {
        conversationManager.setHistory(messages)
    }

    override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> = channelFlow {
        val targetModelType = options.modelType ?: ModelType.FAST
        var isThinking = false
        val accumulatedThought = StringBuilder()
        val accumulatedText = StringBuilder()
        var buffer = ""

        val eventJob = launch {
            toolExecutionEventPort.events
                .filterIsInstance<ToolExecutionEvent.Finished>()
                .collect { event ->
                    if (options.chatId != null && event.chatId != options.chatId) return@collect
                    if (options.userMessageId != null && event.userMessageId != options.userMessageId) return@collect

                    val resultJson = event.resultJson
                    if (!resultJson.isNullOrEmpty()) {
                        val assistantMessageId = options.assistantMessageId
                        if (assistantMessageId != null) {
                            val sources = TavilyResultParser.parse(assistantMessageId, resultJson)
                            val validSources = sources.filter { it.url.isNotEmpty() && it.title.isNotEmpty() }
                            if (validSources.isNotEmpty()) {
                                send(InferenceEvent.TavilyResults(validSources, targetModelType))
                            }
                        }
                    }
                }
        }

        suspend fun attemptInference(retryOnFail: Boolean) {
            try {
                val conversation = conversationManager.getConversation(targetModelType, options) {
                    send(InferenceEvent.EngineLoading(targetModelType))
                }
                send(InferenceEvent.Processing(targetModelType))
                
                Log.d(TAG, "Sending prompt with options: $prompt")

                conversation.sendMessageAsync(prompt, options).collect { response ->
                    if (response.thought.isNotEmpty()) {
                        send(InferenceEvent.Thinking(response.thought, targetModelType))
                    }
                    
                    if (response.text.isNotEmpty()) {
                        val state = processThinkingTokens(buffer, response.text, isThinking)
                        buffer = state.buffer
                        isThinking = state.isThinking

                        state.emittedSegments.forEach { segment ->
                            when (segment.kind) {
                                SegmentKind.THINKING -> {
                                    accumulatedThought.append(segment.text)
                                    send(InferenceEvent.Thinking(segment.text, targetModelType))
                                }
                                SegmentKind.VISIBLE -> {
                                    accumulatedText.append(segment.text)
                                    send(InferenceEvent.PartialResponse(segment.text, targetModelType))
                                }
                            }
                        }
                    }
                }

                if (buffer.isNotEmpty()) {
                    if (isThinking) {
                        accumulatedThought.append(buffer)
                        send(InferenceEvent.Thinking(buffer, targetModelType))
                    } else {
                        accumulatedText.append(buffer)
                        send(InferenceEvent.PartialResponse(buffer, targetModelType))
                    }
                }

                send(InferenceEvent.Finished(targetModelType))
            } catch (e: JavaCancellationException) {
                // LiteRT cancelProcess() signals cancellation through this exception type.
                // Emit Finished so the UI transitions out of generating state cleanly.
                Log.i(TAG, "LiteRT native inference cancelled by user for modelType=$targetModelType")
                conversationManager.cancelProcess()
                send(InferenceEvent.Finished(targetModelType))
            } catch (e: CancellationException) {
                // Coroutine cancellation (e.g. inferenceJob.cancel()) — propagate normally
                // but emit Finished first so UI transitions out of generating state cleanly.
                Log.i(TAG, "LiteRT inference job cancelled for modelType=$targetModelType")
                conversationManager.cancelProcess()
                send(InferenceEvent.Finished(targetModelType))
                throw e
            } catch (e: Exception) {
                if (retryOnFail) {
                    Log.w(TAG, "LiteRT inference failed; resetting engine and retrying once", e)
                    try {
                        conversationManager.closeEngine()
                    } catch (_: Throwable) {}
                    
                    // Reset accumulators before retry
                    isThinking = false
                    accumulatedThought.clear()
                    accumulatedText.clear()
                    buffer = ""
                    
                    attemptInference(retryOnFail = false)
                } else {
                    Log.e(TAG, "Error sending prompt (retry failed)", e)
                    send(InferenceEvent.Error(e, targetModelType))
                }
            }
        }

        try {
            attemptInference(retryOnFail = true)
        } finally {
            eventJob.cancel()
            conversationManager.cancelCurrentGeneration()
            if (closeConversation) {
                conversationManager.closeConversation()
            }
        }
    }.flowOn(Dispatchers.Default)
}
