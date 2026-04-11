package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.SystemPromptTemplates
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

class AnalyzeImageUseCase @Inject constructor(
    private val inferenceFactory: InferenceFactoryPort,
    private val loggingPort: LoggingPort,
) {
    companion object {
        private const val TAG = "AnalyzeImageUseCase"
        private const val DEFAULT_PROMPT =
            "Describe this image in detail. Focus on directly observable content, any visible text, and details that matter for answering the user's request."
    }

    suspend operator fun invoke(imageUri: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            inferenceFactory.withInferenceService(ModelType.VISION) { service ->
                service.setHistory(emptyList())

                val description = StringBuilder()
                service.sendPrompt(
                    prompt = buildPrompt(prompt),
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
                        is InferenceEvent.Finished -> Unit
                        is InferenceEvent.SafetyBlocked -> {
                            throw IllegalStateException("Vision analysis blocked: ${event.reason}")
                        }
                        is InferenceEvent.Error -> throw event.cause
                    }
                }

                description.toString().trim().also { result ->
                    if (result.isBlank()) {
                        loggingPort.warning(TAG, "Vision model returned an empty description")
                        throw IllegalStateException("Vision model returned an empty description")
                    }
                }
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
