package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentRawToolCall
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolEnvelopeParserPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.types.Content
import com.google.genai.types.FunctionCallingConfig
import com.google.genai.types.FunctionCallingConfigMode
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.ToolConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CopyOnWriteArrayList

class GoogleInferenceServiceImpl(
    private val sdkClient: GoogleGenAiSdkClient,
    private val modelId: String,
    private val modelType: ModelType,
    private val baseUrl: String? = null,
    private val loggingPort: LoggingPort,
    private val toolEnvelopeParserPort: ToolEnvelopeParserPort,
    @Suppress("UnusedPrivateProperty") // Injected for mutation tests; streaming holdback lives in core/agent.
    private val toolStreamGatePort: ToolStreamGatePort,
) : RustCompatibleInferenceService {
    companion object {
        private const val MAX_LOG_BODY_CHARS = 4_000
        private const val TAG = "GoogleInferenceService"
        private const val PROVIDER = "GOOGLE"
        private val TOOL_ARGUMENT_JSON_MAPPER = ObjectMapper()
    }

    private val conversationHistory = CopyOnWriteArrayList<ChatMessage>()

    override fun sendPrompt(
        prompt: String,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> = sendPrompt(prompt, GenerationOptions(reasoningBudget = 0), closeConversation)

    override fun sendPrompt(
        prompt: String,
        options: GenerationOptions,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> =
        flow {
            try {
                require(options.imageUris.size <= 3600) {
                    "Google API supports a maximum of 3600 images per request."
                }
                val requestHistory = buildRequestHistory(options)
                loggingPort.debug(
                    TAG,
                    @Suppress("MaxLineLength")
                    "sendPrompt start provider=$PROVIDER model=$modelId modelType=$modelType closeConversation=$closeConversation reasoningBudget=${options.reasoningBudget} reasoningEffort=${options.reasoningEffort} maxTokens=${options.maxTokens}",
                )
                executePrompt(prompt, options, requestHistory) { emit(it) }

                conversationHistory.add(ChatMessage(Role.USER, prompt))
                if (closeConversation) {
                    closeSession()
                }
            } catch (e: CancellationException) {
                loggingPort.debug(TAG, "sendPrompt cancelled provider=$PROVIDER model=$modelId")
                emit(InferenceEvent.Finished(modelType))
                throw e
            } catch (e: IllegalArgumentException) {
                loggingPort.error(TAG, "Tool request failed. exception=${e::class.java.simpleName} message=${e.message}", e)
                emit(InferenceEvent.Error(e, modelType))
                return@flow
            } catch (e: IllegalStateException) {
                loggingPort.error(TAG, "Tool request failed. exception=${e::class.java.simpleName} message=${e.message}", e)
                emit(InferenceEvent.Error(e, modelType))
                return@flow
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                loggingPort.error(TAG, "API request failed. exception=${e::class.java.simpleName} message=$errorMsg", e)
                emit(InferenceEvent.Error(Exception("API Error ($PROVIDER): $errorMsg", e), modelType))
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun executePrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ) {
        logImagePayloads(options)

        val request =
            GoogleRequestMapper.mapToGenerateContentRequest(
                prompt = prompt,
                history = requestHistory,
                options = options,
            )

        loggingPort.info(
            TAG,
            "Using Google GenAI SDK. model=$modelId reasoningBudget=${options.reasoningBudget} maxTokens=${options.maxTokens}",
        )
        loggingPort.debug(
            TAG,
            @Suppress("MaxLineLength")
            "GenerateContent request provider=$PROVIDER model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${truncateForLogs(
                request.config.toString(),
            )}",
        )

        sdkClient.generateContentStream(
            modelId = modelId,
            contents = request.contents,
            config = request.config,
            emitEvent = emitEvent,
        )
        emitEvent(InferenceEvent.Finished(modelType))
    }

    private fun buildFunctionResponsePart(
        toolName: String,
        resultJson: String,
    ): Part =
        Part.fromFunctionResponse(
            toolName,
            mapOf("output" to resultJson),
        )

    override suspend fun setHistory(messages: List<ChatMessage>) {
        conversationHistory.clear()
        conversationHistory.addAll(messages)
    }

    override suspend fun closeSession() {
        conversationHistory.clear()
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun completeSingleTurn(
        sessionMessages: List<AgentLoopMessage>,
        systemPrompt: String,
        availableTools: List<ToolDefinition>,
        allowToolCall: Boolean,
        onTextToken: suspend (String) -> Unit,
        onThinkingToken: suspend (String) -> Unit,
        options: GenerationOptions,
    ): AgentModelTurnResult {
        val contents = mutableListOf<Content>()
        val imageUris = options.imageUris.take(3600)
        val lastUserIndex = sessionMessages.indexOfLast { msg ->
            msg.role == Role.USER && run {
                val meta = msg.metadataJson
                if (meta == "null" || meta.isBlank()) return@run true
                val obj = runCatching { Json.parseToJsonElement(meta).jsonObject }.getOrNull()
                obj?.get("call_id")?.jsonPrimitive?.contentOrNull == null
            }
        }

        sessionMessages.forEachIndexed { index, msg ->
            val metadata =
                if (msg.metadataJson != "null" && msg.metadataJson.isNotBlank()) {
                    runCatching { Json.parseToJsonElement(msg.metadataJson).jsonObject }.getOrNull()
                } else {
                    null
                }

            when (msg.role) {
                Role.SYSTEM -> {} // handled via systemInstruction in config
                Role.USER -> {
                    val attachImages = index == lastUserIndex && imageUris.isNotEmpty()
                    val parts =
                        if (!attachImages) {
                            listOf(Part.fromText(msg.content))
                        } else {
                            buildList {
                                add(Part.fromText(msg.content))
                                ImagePayloads.fromUris(imageUris).forEach { payload ->
                                    add(Part.fromBytes(payload.bytes, payload.mimeType))
                                }
                            }
                        }
                    contents.add(
                        Content
                            .builder()
                            .role("user")
                            .parts(parts)
                            .build(),
                    )
                }
                Role.ASSISTANT -> {
                    if (msg.id.startsWith("compact-summary-")) {
                        contents.add(
                            Content
                                .builder()
                                .role("user")
                                .parts(listOf(Part.fromText("[Conversation summary]\n${msg.content}")))
                                .build(),
                        )
                        return@forEachIndexed
                    }
                    val callId = metadata?.get("call_id")?.jsonPrimitive?.content
                    if (callId != null) {
                        contents.add(
                            Content
                                .builder()
                                .role("user")
                                .parts(listOf(buildFunctionResponsePart(callId, msg.content)))
                                .build(),
                        )
                        return@forEachIndexed
                    }
                    val rawCalls = metadata?.get("raw_tool_calls")
                    val parts = mutableListOf<Part>()
                    if (msg.content.isNotBlank()) parts.add(Part.fromText(msg.content))
                    if (rawCalls is JsonArray) {
                        rawCalls.forEach { tc ->
                            val obj = runCatching { tc.jsonObject }.getOrNull() ?: return@forEach
                            val callName = obj["name"]?.jsonPrimitive?.content ?: return@forEach
                            val argsJson = obj["arguments_json"]?.jsonPrimitive?.content ?: "{}"
                            val args =
                                runCatching {
                                    @Suppress("UNCHECKED_CAST")
                                    com.fasterxml.jackson.databind
                                        .ObjectMapper()
                                        .readValue(argsJson, Map::class.java) as Map<String, Any>
                                }.getOrElse { emptyMap() }
                            parts.add(Part.fromFunctionCall(callName, args))
                        }
                    }
                    if (parts.isNotEmpty()) {
                        contents.add(
                            Content
                                .builder()
                                .role("model")
                                .parts(parts)
                                .build(),
                        )
                    }
                }
            }
        }

        val configBuilder = GenerateContentConfig.builder()
        systemPrompt.takeIf { it.isNotBlank() }?.let {
            configBuilder.systemInstruction(Content.fromParts(Part.fromText(it)))
        }
        options.temperature?.let { configBuilder.temperature(it) }
        options.topP?.let { configBuilder.topP(it) }
        options.maxTokens?.let { configBuilder.maxOutputTokens(it) }

        if (allowToolCall && availableTools.isNotEmpty()) {
            configBuilder.tools(availableTools.map { it.toGoogleTool() })
            configBuilder.toolConfig(
                ToolConfig
                    .builder()
                    .functionCallingConfig(
                        FunctionCallingConfig.builder().mode(FunctionCallingConfigMode.Known.AUTO).build(),
                    ).build(),
            )
        }

        val accumulatedText = StringBuilder()
        val result =
            sdkClient.generateContentStream(
                modelId = modelId,
                contents = contents,
                config = configBuilder.build(),
                allowToolCall = allowToolCall,
                emitEvent = { event ->
                    when (event) {
                        is InferenceEvent.PartialResponse -> {
                            accumulatedText.append(event.chunk)
                            onTextToken(event.chunk)
                        }
                        is InferenceEvent.Thinking -> onThinkingToken(event.chunk)
                        else -> {}
                    }
                },
            )

        val toolCalls =
            result.functionCall?.let { fc ->
                val name = fc.name().orElse(null) ?: return@let null
                val argsJson =
                    toolEnvelopeParserPort
                        .coerceProviderArguments(
                            toolName = name,
                            argsJson = TOOL_ARGUMENT_JSON_MAPPER.writeValueAsString(fc.args().orElse(emptyMap())),
                        ).argumentsJson
                listOf(AgentRawToolCall(id = "", name = name, argumentsJson = argsJson))
            } ?: emptyList()
        val bufferedText = accumulatedText.toString()

        return AgentModelTurnResult(
            assistantText = bufferedText.takeIf { it.isNotBlank() },
            toolCalls = toolCalls,
            finishReason = googleFinishReason(toolCalls, result.finishReason, bufferedText),
        )
    }

    private fun googleFinishReason(
        toolCalls: List<AgentRawToolCall>,
        finishReason: String?,
        assistantText: String?,
    ): AgentFinishReason {
        if (toolCalls.isNotEmpty()) {
            return AgentFinishReason.ToolUse
        }
        return finishReason
            ?.let { ProviderFinishReasonMapper.fromGoogle(it, assistantText.orEmpty()).finishReason }
            ?: AgentFinishReason.Stop
    }

    private fun buildRequestHistory(options: GenerationOptions): List<ChatMessage> {
        val historyWithSystemPrompt = mergeSystemPrompt(conversationHistory, options.systemPrompt)
        loggingPort.debug(
            TAG,
            @Suppress("MaxLineLength")
            "Prepared request history provider=$PROVIDER model=$modelId modelType=$modelType historyCount=${historyWithSystemPrompt.size} systemPromptIncluded=${historyWithSystemPrompt.firstOrNull()?.role == Role.SYSTEM}",
        )
        return historyWithSystemPrompt
    }

    private fun mergeSystemPrompt(
        history: List<ChatMessage>,
        systemPrompt: String?,
    ): List<ChatMessage> {
        val normalizedPrompt = systemPrompt?.trim()?.takeIf { it.isNotEmpty() } ?: return history
        if (history.any { it.role == Role.SYSTEM && it.content == normalizedPrompt }) {
            return history
        }
        return listOf(ChatMessage(role = Role.SYSTEM, content = normalizedPrompt)) + history
    }

    private fun truncateForLogs(value: String): String {
        if (value.length <= MAX_LOG_BODY_CHARS) {
            return value
        }
        return value.take(MAX_LOG_BODY_CHARS) + "...<truncated>"
    }

    private fun logImagePayloads(options: GenerationOptions) {
        if (options.imageUris.isEmpty()) {
            return
        }
        val payloads =
            runCatching {
                ImagePayloads.fromUris(options.imageUris)
            }.getOrElse { error ->
                loggingPort.error(
                    TAG,
                    "Failed to load image payloads provider=$PROVIDER model=$modelId message=${error.message}",
                    error,
                )
                return
            }

        val details =
            payloads.joinToString(separator = "; ") { payload ->
                "file=${payload.filename}, mime=${payload.mimeType}, bytes=${payload.byteCount}, sha256=${payload.sha256.take(8)}"
            }
        loggingPort.info(
            TAG,
            "Image payloads provider=$PROVIDER model=$modelId count=${payloads.size} $details",
        )
    }
}
