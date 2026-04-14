package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.SystemPromptTemplates
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uses [Provider] to break a Dagger/Hilt constructor-injection cycle:
 *
 * InferenceFactoryImpl → LlmToolingOrchestrator → ToolExecutorPort
 *   → CompositeToolExecutor → ImageInspectToolExecutor → AnalyzeImageUseCase
 *   → InferenceFactoryPort → InferenceFactoryImpl (cycle).
 *
 * The factory is only needed at invoke-time, not at construction-time,
 * so [Provider] yields the correct semantics: lazy resolution after the
 * full singleton graph is initialized.
 */
class AnalyzeImageUseCase @Inject constructor(
    private val inferenceFactoryProvider: Provider<InferenceFactoryPort>,
    private val loggingPort: LoggingPort,
) {
    companion object {
        private const val TAG = "AnalyzeImageUseCase"
        private const val DEFAULT_PROMPT =
            "Describe this image in detail. Focus on directly observable content, any visible text, and details that matter for answering the user's request."
    }

    suspend operator fun invoke(imageUri: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            val effectivePrompt = buildPrompt(prompt)
            loggingPort.info(
                TAG,
                "Starting vision analysis imageUri=$imageUri promptChars=${effectivePrompt.length}"
            )
            try {
                inferenceFactoryProvider.get().withInferenceService(ModelType.VISION) { service ->
                    loggingPort.info(TAG, "Resolved vision inference service for imageUri=$imageUri")
                    service.setHistory(emptyList())

                    val description = StringBuilder()
                    service.sendPrompt(
                        prompt = effectivePrompt,
                        options = GenerationOptions(
                            reasoningBudget = 0,
                            modelType = ModelType.VISION,
                            systemPrompt = SystemPromptTemplates.VISION,
                            imageUris = listOf(imageUri),
                        ),
                        closeConversation = true,
                    ).collect { event ->
                        when (event) {
                            is InferenceEvent.PartialResponse -> description.append(event.chunk)
                            is InferenceEvent.Thinking -> Unit
                            is InferenceEvent.Finished -> {
                                loggingPort.info(
                                    TAG,
                                    "Vision analysis finished imageUri=$imageUri responseChars=${description.length}"
                                )
                            }
                            is InferenceEvent.SafetyBlocked -> {
                                loggingPort.error(
                                    TAG,
                                    "Vision analysis safety blocked imageUri=$imageUri reason=${event.reason}"
                                )
                                throw IllegalStateException("Vision analysis blocked: ${event.reason}")
                            }
                            is InferenceEvent.Error -> {
                                loggingPort.error(
                                    TAG,
                                    "Vision analysis inference error imageUri=$imageUri cause=${event.cause::class.java.simpleName}: ${event.cause.message}",
                                    event.cause
                                )
                                throw event.cause
                            }
                        }
                    }

                    description.toString().trim().also { result ->
                        if (result.isBlank()) {
                            loggingPort.warning(TAG, "Vision model returned an empty description imageUri=$imageUri")
                            throw IllegalStateException("Vision model returned an empty description")
                        }
                    }
                }
            } catch (t: Throwable) {
                loggingPort.error(
                    TAG,
                    "Vision analysis failed imageUri=$imageUri cause=${t::class.java.simpleName}: ${t.message}",
                    t
                )
                throw t
            }
        }

    private fun buildPrompt(prompt: String): String {
        val trimmedPrompt = prompt.trim()
        return if (trimmedPrompt.isBlank()) {
            DEFAULT_PROMPT
        } else {
            "$DEFAULT_PROMPT\n\nUser request: $trimmedPrompt"
        }
    }
}
