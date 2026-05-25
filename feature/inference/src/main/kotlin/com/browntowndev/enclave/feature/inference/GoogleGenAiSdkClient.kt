package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.google.genai.Models
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible

import com.google.genai.Client
import com.google.genai.Caches
import com.google.genai.types.CreateCachedContentConfig
import com.google.genai.types.DeleteCachedContentConfig

data class GoogleSdkResult(
    val emittedAny: Boolean,
    val functionCall: FunctionCall?,
    val assistantContent: Content?,
    val finishReason: String?,
)

interface GoogleGenAiSdkClient {
    suspend fun generateContentStream(
        modelId: String,
        contents: List<Content>,
        config: GenerateContentConfig,
        allowToolCall: Boolean = false,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): GoogleSdkResult

    suspend fun createCachedContent(
        modelId: String,
        contents: List<Content>,
        systemInstruction: Content?,
        tools: List<com.google.genai.types.Tool>?,
        ttlSeconds: Long,
    ): String

    suspend fun deleteCachedContent(name: String)
}

internal class GoogleGenAiSdkClientImpl(
    private val clientProvider: () -> Client,
    private val modelType: ModelType,
    private val loggingPort: LoggingPort,
) : GoogleGenAiSdkClient {
    companion object {
        private const val TAG = "GoogleGenAiSdkClient"
    }

    // Secondary constructor to maintain compatibility with existing tests
    constructor(
        modelsApiProvider: () -> Models,
        modelType: ModelType,
        loggingPort: LoggingPort,
        @Suppress("UnusedPrivateParameter", "UnusedPrivateProperty") dummy: Unit = Unit
    ) : this(
        clientProvider = { throw UnsupportedOperationException("Caches require Client, but only Models provider was supplied") },
        modelType = modelType,
        loggingPort = loggingPort,
    ) {
        _modelsApiOverride = modelsApiProvider
    }

    private var _modelsApiOverride: (() -> Models)? = null

    private val client: Client get() = clientProvider()
    private val modelsApi: Models get() = _modelsApiOverride?.invoke() ?: client.models
    private val cachesApi: Caches get() = client.caches

    override suspend fun createCachedContent(
        modelId: String,
        contents: List<Content>,
        systemInstruction: Content?,
        tools: List<com.google.genai.types.Tool>?,
        ttlSeconds: Long,
    ): String {
        return runInterruptible(Dispatchers.IO) {
            val builder = CreateCachedContentConfig.builder()
                .contents(contents)
                .ttl(java.time.Duration.ofSeconds(ttlSeconds))

            if (systemInstruction != null) {
                builder.systemInstruction(systemInstruction)
            }
            if (!tools.isNullOrEmpty()) {
                builder.tools(tools)
            }

            val result = cachesApi.create(modelId, builder.build())
            result.name().orElseThrow {
                IllegalStateException("CachedContent creation failed to return resource name")
            }
        }
    }

    override suspend fun deleteCachedContent(name: String) {
        runInterruptible(Dispatchers.IO) {
            cachesApi.delete(name, DeleteCachedContentConfig.builder().build())
        }
    }

    @Suppress("NestedBlockDepth")
    override suspend fun generateContentStream(
        modelId: String,
        contents: List<Content>,
        config: GenerateContentConfig,
        allowToolCall: Boolean,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): GoogleSdkResult {
        logRequestDetails(modelId, contents)

        var emittedAny = false
        var lastFunctionCall: FunctionCall? = null
        var assistantContent: Content? = null
        var finishReason: String? = null

        try {
            runInterruptible(Dispatchers.IO) {
                modelsApi.generateContentStream(modelId, contents, config)
            }.use { responseStream ->
                val iterator = responseStream.iterator()
                while (currentCoroutineContext().isActive && runInterruptible(Dispatchers.IO) { iterator.hasNext() }) {
                    val response = runInterruptible(Dispatchers.IO) { iterator.next() }

                    response.candidates().orElse(emptyList()).forEach { candidate ->
                        finishReason = candidate.finishReason().map { it.toString() }.orElse(finishReason)
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
            finishReason = finishReason,
        )
    }

    private fun logRequestDetails(
        modelId: String,
        contents: List<Content>,
    ) {
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
            "Gemini SDK Request: model=$modelId, promptTextLen=$totalTextLength, images=$imageCount, details=$imageDetails",
        )
    }
}
