package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.google.genai.Models
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible

data class GoogleSdkResult(
    val emittedAny: Boolean,
    val functionCall: FunctionCall?,
    val assistantContent: Content?,
)

interface GoogleGenAiSdkClient {
    suspend fun generateContentStream(
        modelId: String,
        contents: List<Content>,
        config: GenerateContentConfig,
        allowToolCall: Boolean = false,
        emitEvent: suspend (InferenceEvent) -> Unit
    ): GoogleSdkResult
}

internal class GoogleGenAiSdkClientImpl(
    private val modelsApiProvider: () -> Models,
    private val modelType: ModelType,
    private val loggingPort: LoggingPort,
) : GoogleGenAiSdkClient {

    companion object {
        private const val TAG = "GoogleGenAiSdkClient"
    }

    private val modelsApi: Models get() = modelsApiProvider()

    override suspend fun generateContentStream(
        modelId: String,
        contents: List<Content>,
        config: GenerateContentConfig,
        allowToolCall: Boolean,
        emitEvent: suspend (InferenceEvent) -> Unit
    ): GoogleSdkResult {
        logRequestDetails(modelId, contents)

        var emittedAny = false
        var lastFunctionCall: FunctionCall? = null
        var assistantContent: Content? = null

        try {
            runInterruptible(Dispatchers.IO) {
                modelsApi.generateContentStream(modelId, contents, config)
            }.use { responseStream ->
                val iterator = responseStream.iterator()
                while (currentCoroutineContext().isActive && runInterruptible(Dispatchers.IO) { iterator.hasNext() }) {
                    val response = runInterruptible(Dispatchers.IO) { iterator.next() }

                    response.candidates().orElse(emptyList()).forEach { candidate ->
                        val content = candidate.content().orElse(null)
                        if (content != null) {
                            assistantContent = content
                            content.parts().orElse(emptyList()).forEach { part ->
                                val text = part.text().orElse("")
                                if (text.isNotBlank()) {
                                    emittedAny = true
                                    if (part.thought().orElse(false)) {
                                        emitEvent(InferenceEvent.Thinking(text, modelType))
                                    } else {
                                        emitEvent(InferenceEvent.PartialResponse(text, modelType))
                                    }
                                }
                            }
                        }
                    }
                    lastFunctionCall = response.functionCalls()?.firstOrNull() ?: lastFunctionCall

                    val responseText = response.text().orEmpty()
                    if (responseText.isNotBlank() && !emittedAny) {
                        emitEvent(InferenceEvent.PartialResponse(responseText, modelType))
                        emittedAny = true
                    }
                }
            }
        } catch (e: Exception) {
            loggingPort.error(TAG, "SDK request failed: ${e.message}", e)
            throw e
        }

        return GoogleSdkResult(
            emittedAny = emittedAny,
            functionCall = lastFunctionCall,
            assistantContent = assistantContent,
        )
    }

    private fun logRequestDetails(modelId: String, contents: List<Content>) {
        var totalTextLength = 0
        var imageCount = 0
        val imageDetails = mutableListOf<String>()

        contents.forEach { content ->
            content.parts().ifPresent { parts ->
                parts.forEach { part ->
                    part.text().ifPresent { totalTextLength += it.length }
                    part.inlineData().ifPresent { inlineData ->
                        imageCount++
                        imageDetails.add("inline(mime=${inlineData.mimeType()})")
                    }
                    part.fileData().ifPresent { fileData ->
                        imageCount++
                        imageDetails.add("file(mime=${fileData.mimeType()}, uri=${fileData.fileUri()})")
                    }
                }
            }
        }

        loggingPort.info(
            TAG,
            "Gemini SDK Request: model=$modelId, promptTextLen=$totalTextLength, images=$imageCount, details=$imageDetails"
        )
    }
}
