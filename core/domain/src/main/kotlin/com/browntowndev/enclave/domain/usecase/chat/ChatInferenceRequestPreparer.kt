package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import com.browntowndev.enclave.domain.util.WebSearchResultParser
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.memory.MemoryRetrievalPolicy
import com.browntowndev.enclave.domain.util.SearchIntentPolicy
import com.browntowndev.enclave.domain.util.ToolIntentActivationPolicy
import com.browntowndev.enclave.domain.util.isWebSearchSkillConfigured
import com.browntowndev.enclave.domain.util.isXSearchSkillConfigured
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.PromptComposerPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.MediaToolDefinitionFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.browntowndev.enclave.domain.model.inference.XSearchParams
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.SearchIntentClassifierPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ChatInferenceRequestPreparer(
    private val activeModelProvider: ActiveModelProviderPort,
    private val settingsRepository: SettingsRepository,
    private val messageRepository: MessageRepository,
    private val memoriesRepository: MemoriesRepository,
    private val embeddingEnginePort: EmbeddingEnginePort,
    private val promptComposer: PromptComposerPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val mediaProviderRepository: MediaProviderRepositoryPort,
    private val mediaToolDefinitionFactory: MediaToolDefinitionFactory,
    private val getProviderCapabilitiesUseCase: GetProviderCapabilitiesUseCase,
    private val loggingPort: LoggingPort,
    private val searchIntentClassifier: SearchIntentClassifierPort = DummySearchIntentClassifier,
    private val toolExecutor: ToolExecutorPort = DummyToolExecutor,
) {
    private val dateTimeFormatter =
        DateTimeFormatter
            .ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a")
            .withZone(ZoneId.systemDefault())
            .withLocale(Locale.getDefault())

    @Suppress("CyclomaticComplexMethod")
    suspend operator fun invoke(
        prompt: String,
        chatId: ChatId,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        modelType: ModelType,
    ): PreparedChatInferenceRequest {
        val settings = settingsRepository.settingsFlow.first()
        val config = activeModelProvider.getActiveConfiguration(modelType)
        val visionConfig = activeModelProvider.getActiveConfiguration(ModelType.VISION)
        val userMessage = messageRepository.getMessageById(userMessageId)
        val resolvedImageTarget = messageRepository.resolveLatestImageBearingUserMessage(chatId, userMessageId)
        val webSearchConfigured = settings.isWebSearchSkillConfigured()
        val xSearchConfigured = settings.isXSearchSkillConfigured()
        val activeVisionCapable = config?.isMultimodal == true
        val apiVisionConfigured = visionConfig?.isLocal == false && visionConfig.isMultimodal
        val hasImageContext = resolvedImageTarget != null
        val imageHandling =
            when {
                hasImageContext && activeVisionCapable -> ChatImageHandling.DIRECT
                hasImageContext && apiVisionConfigured && !activeVisionCapable -> ChatImageHandling.TOOL
                else -> ChatImageHandling.NONE
            }
        val reasoningBudget = if (config?.isLocal == true && config.thinkingEnabled) 2048 else 0

        val coreMemories = memoriesRepository.getCoreMemories()
        val embedding = if (prompt.isNotBlank()) embeddingEnginePort.getEmbedding(prompt) else null
        val retrievedMemories =
            if (embedding != null) {
                memoriesRepository.searchMemories(
                    queryVector = embedding,
                    limit = MemoryRetrievalPolicy.CHAT_RETRIEVAL_LIMIT,
                    maxDistance = MemoryRetrievalPolicy.MAX_L2_DISTANCE,
                    excludeCategories = MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES,
                )
            } else {
                emptyList()
            }

        val intentDecision =
            ToolIntentActivationPolicy.resolve(
                prompt = prompt,
                embedding = embedding,
                settings = settings,
                classifier = searchIntentClassifier,
            )

        loggingPort.info(
            TAG,
            "Tool intent activation: artifact=${intentDecision.activateArtifactTools} " +
                "x=${intentDecision.activateSearchX} webPreRetrieval=${intentDecision.runWebPreRetrieval} " +
                "heuristicNonSearch=${SearchIntentPolicy.isHeuristicNonSearchPrompt(prompt)} " +
                "researchBypass=${SearchIntentPolicy.hasConcurrentResearchIntent(prompt)}",
        )

        var preRetrievalSources = emptyList<WebSearchSource>()

        val (retrievedSearchResults, retrievedXSearchResults) = coroutineScope {
            val webDeferred = async {
                if (intentDecision.runWebPreRetrieval) {
                    val probability = searchIntentClassifier.getSearchIntentProbability(embedding!!)
                    loggingPort.info(
                        TAG,
                        "Search intent classification decision: requiresSearch=true " +
                            "(probability=$probability, threshold=${SearchIntentPolicy.PRE_RETRIEVAL_THRESHOLD}) " +
                            "for prompt: \"$prompt\"",
                    )
                    loggingPort.info(TAG, "Search intent detected for prompt: $prompt")
                    try {
                        val argumentsJson =
                            Json.encodeToString(
                                buildJsonObject {
                                    put("query", prompt)
                                },
                            )
                        val searchRequest =
                            ToolCallRequest(
                                toolName = ToolDefinition.WEB_SEARCH.name,
                                argumentsJson = argumentsJson,
                                provider = config?.apiProvider?.name ?: "local",
                                modelType = modelType,
                                chatId = chatId,
                                userMessageId = userMessageId,
                                assistantMessageId = assistantMessageId,
                            )
                        val searchResult = toolExecutor.execute(searchRequest)
                        val webSources = WebSearchResultParser.parse(assistantMessageId, searchResult.resultJson)
                        loggingPort.info(
                            TAG,
                            "Retrieved ${webSources.size} results from search tool for query: \"$prompt\"",
                        )
                        webSources to searchResult.resultJson
                    } catch (e: Exception) {
                        loggingPort.error(TAG, "Pre-retrieval search execution failed: ${e.message}", e)
                        emptyList<WebSearchSource>() to null
                    }
                } else {
                    loggingPort.info(
                        TAG,
                        "Skipping search intent classification: embeddingIsNull=${embedding == null}, " +
                            "webSearchConfigured=$webSearchConfigured",
                    )
                    emptyList<WebSearchSource>() to null
                }
            }

            val xDeferred = async {
                if (!xSearchConfigured) return@async emptyList<WebSearchSource>() to null

                val tokens = prompt.split(Regex("\\s+"))
                val matchedUrl = tokens.firstOrNull { X_STATUS_URL_REGEX.containsMatchIn(it) }?.trimEnd('.', ',', ';', '?', '!')

                val heuristicNonSearch = SearchIntentPolicy.isHeuristicNonSearchPrompt(prompt)
                val researchBypass = SearchIntentPolicy.hasConcurrentResearchIntent(prompt)
                val classifierSaysSearch = embedding != null &&
                    searchIntentClassifier.requiresSearch(embedding, SearchIntentPolicy.PRE_RETRIEVAL_THRESHOLD)
                val requiresXSearch = xSearchConfigured && classifierSaysSearch && !(heuristicNonSearch && !researchBypass)

                if (matchedUrl != null) {
                    try {
                        val argumentsJson = Json.encodeToString(
                            buildJsonObject {
                                put("tweet_url", matchedUrl)
                            }
                        )
                        val threadRequest = ToolCallRequest(
                            toolName = ToolDefinition.X_GET_THREAD.name,
                            argumentsJson = argumentsJson,
                            provider = config?.apiProvider?.name ?: "local",
                            modelType = modelType,
                            chatId = chatId,
                            userMessageId = userMessageId,
                            assistantMessageId = assistantMessageId,
                        )
                        val threadResult = toolExecutor.execute(threadRequest)
                        val xSources = WebSearchResultParser.parse(assistantMessageId, threadResult.resultJson)
                        xSources to threadResult.resultJson
                    } catch (e: Exception) {
                        loggingPort.error(TAG, "Pre-retrieval X thread retrieval failed: ${e.message}", e)
                        emptyList<WebSearchSource>() to null
                    }
                } else if (intentDecision.activateSearchX || requiresXSearch) {
                    try {
                        val deriveUseCase = com.browntowndev.enclave.domain.usecase.inference.DeriveXSearchParamsFromPromptUseCase()
                        val deriveResult = deriveUseCase(prompt)
                        val params = deriveResult.params
                        if (params != null) {
                            val argumentsJson = Json.encodeToString(XSearchParams.serializer(), params)
                            val searchRequest = ToolCallRequest(
                                toolName = ToolDefinition.X_SEARCH.name,
                                argumentsJson = argumentsJson,
                                provider = config?.apiProvider?.name ?: "local",
                                modelType = modelType,
                                chatId = chatId,
                                userMessageId = userMessageId,
                                assistantMessageId = assistantMessageId,
                            )
                            val searchResult = toolExecutor.execute(searchRequest)
                            val xSources = WebSearchResultParser.parse(assistantMessageId, searchResult.resultJson)
                            xSources to searchResult.resultJson
                        } else {
                            loggingPort.info(TAG, "Skipping X search, reason: ${deriveResult.skipReason}")
                            emptyList<WebSearchSource>() to null
                        }
                    } catch (e: Exception) {
                        loggingPort.error(TAG, "Pre-retrieval X search execution failed: ${e.message}", e)
                        emptyList<WebSearchSource>() to null
                    }
                } else {
                    emptyList<WebSearchSource>() to null
                }
            }

            val webRes = webDeferred.await()
            val xRes = xDeferred.await()
            preRetrievalSources = webRes.first + xRes.first
            webRes.second to xRes.second
        }

        loggingPort.debug(
            TAG,
            "generateWithService config modelType=$modelType configName=${config?.name} " +
                "isLocal=${config?.isLocal} webSearchConfigured=$webSearchConfigured xSearchConfigured=$xSearchConfigured " +
                "imageHandling=$imageHandling activeVisionCapable=$activeVisionCapable " +
                "apiVisionConfigured=$apiVisionConfigured thinkingEnabled=${config?.thinkingEnabled} " +
                "reasoningEffort=${config?.reasoningEffort} derivedReasoningBudget=$reasoningBudget",
        )
        if (config?.isLocal == true) {
            logLocalPrompt(prompt, modelType)
        }

        val strategy =
            when {
                config?.isLocal == true ->
                    when (config.localModelFormat) {
                        ModelFileFormat.LITERTLM, ModelFileFormat.LITERT_ALIAS -> ToolCallStrategy.LITE_RT_NATIVE
                        ModelFileFormat.GGUF -> ToolCallStrategy.JSON_XML_ENVELOPE
                        ModelFileFormat.BIN,
                        ModelFileFormat.ONNX,
                        ModelFileFormat.TXT,
                        -> ToolCallStrategy.JSON_XML_ENVELOPE
                        null -> ToolCallStrategy.JSON_XML_ENVELOPE
                    }
                config?.isLocal == false -> ToolCallStrategy.SDK_NATIVE
                else -> ToolCallStrategy.JSON_XML_ENVELOPE
            }

        val toolingEnabled = true // Memory tools are always available
        val imageAssignment = defaultModelRepository.getDefault(ModelType.IMAGE_GENERATION)
        val imageTool =
            if (imageAssignment?.mediaProviderId != null) {
                val provider = mediaProviderRepository.getMediaProvider(imageAssignment.mediaProviderId)
                mediaToolDefinitionFactory.buildImageToolDefinition(provider?.provider?.name)
            } else {
                null
            }

        val videoAssignment = defaultModelRepository.getDefault(ModelType.VIDEO_GENERATION)
        val videoTool =
            if (videoAssignment?.mediaProviderId != null) {
                val provider = mediaProviderRepository.getMediaProvider(videoAssignment.mediaProviderId)
                if (getProviderCapabilitiesUseCase(provider?.provider?.name).supportsVideo) {
                    mediaToolDefinitionFactory.buildVideoToolDefinition(provider?.provider?.name)
                } else {
                    null
                }
            } else {
                null
            }

        val musicAssignment = defaultModelRepository.getDefault(ModelType.MUSIC_GENERATION)
        val musicTool =
            if (musicAssignment?.mediaProviderId != null) {
                mediaToolDefinitionFactory.buildMusicToolDefinition()
            } else {
                null
            }

        val intentActivatedNames =
            buildSet {
                if (intentDecision.activateArtifactTools) {
                    add(ToolDefinition.START_ARTIFACT.name)
                    add(ToolDefinition.APPEND_BLOCKS.name)
                    add(ToolDefinition.SEARCH_CURRENT_ARTIFACT.name)
                }
                if (intentDecision.activateSearchX) {
                    add(ToolDefinition.X_SEARCH.name)
                }
            }

        val eligibleTools =
            buildList {
                add(ToolDefinition.DISCLOSE_TOOL_SCHEMAS)
                if (webSearchConfigured) {
                    add(ToolDefinition.WEB_SEARCH)
                    add(ToolDefinition.WEB_EXTRACT)
                }
                if (imageHandling == ChatImageHandling.TOOL) add(ToolDefinition.ATTACHED_IMAGE_INSPECT)
                add(ToolDefinition.SEARCH_CHAT_HISTORY)
                add(ToolDefinition.SEARCH_CHAT)
                add(ToolDefinition.GET_MESSAGE_CONTEXT)
                add(ToolDefinition.MANAGE_MEMORIES)
                add(ToolDefinition.START_ARTIFACT)
                add(ToolDefinition.APPEND_BLOCKS)
                add(ToolDefinition.SEARCH_CURRENT_ARTIFACT)
                if (xSearchConfigured) {
                    add(ToolDefinition.X_SEARCH)
                    add(ToolDefinition.X_GET_THREAD)
                    add(ToolDefinition.X_TRENDING)
                    add(ToolDefinition.X_USER_TWEETS)
                    add(ToolDefinition.X_MENTION_SEARCH)
                }
                imageTool?.takeIf { it.name.isNotBlank() }?.let { add(it) }
                videoTool?.takeIf { it.name.isNotBlank() }?.let { add(it) }
                musicTool?.takeIf { it.name.isNotBlank() }?.let { add(it) }
            }

        val toolCatalog =
            eligibleTools.map { tool ->
                val disclosureMode =
                    when {
                        tool.name in PRELOADED_TOOL_NAMES ->
                            com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode.PRELOADED
                        tool.name in intentActivatedNames ->
                            com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode.INTENT_ACTIVATED
                        else ->
                            com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode.DISCLOSE_ON_DEMAND
                    }
                tool.catalogEntry().copy(disclosureMode = disclosureMode)
            }

        val activeToolNames = PRELOADED_TOOL_NAMES + intentActivatedNames
        val activeTools =
            eligibleTools.filter { tool ->
                tool.name in activeToolNames
            }

        val systemPrompt =
            if (toolingEnabled) {
                promptComposer.compose(
                    baseSystemPrompt = config?.systemPrompt,
                    toolCatalog = toolCatalog,
                    activeTools = activeTools,
                    currentChatId = chatId.value,
                    strategy = strategy,
                    coreMemories = coreMemories,
                    retrievedMemories = retrievedMemories,
                    retrievedSearchResults = retrievedSearchResults,
                    retrievedXSearchResults = retrievedXSearchResults,
                )
            } else {
                config?.systemPrompt
            }

        val formattedTime = dateTimeFormatter.format(java.time.Instant.now())
        val promptWithMetadata = "$prompt\n\n[Metadata: Current Time: $formattedTime]"

        return PreparedChatInferenceRequest(
            prompt =
                prepareChatPrompt(
                    prompt = promptWithMetadata,
                    hasImageContext = hasImageContext,
                    imageHandling = imageHandling,
                    fileAttachments = userMessage?.content?.fileAttachments ?: emptyList(),
                ),
            preRetrievalSources = preRetrievalSources,
            preRetrievalXResultJson = retrievedXSearchResults,
            options =
                GenerationOptions(
                    reasoningBudget = reasoningBudget,
                    modelType = modelType,
                    modelId = config?.modelId,
                    apiProvider = config?.apiProvider,
                    apiModelPricing = config?.apiModelPricing,
                    systemPrompt = systemPrompt,
                    imageUris =
                        if (imageHandling == ChatImageHandling.DIRECT) {
                            resolvedImageTarget?.imageUris.orEmpty()
                        } else {
                            emptyList()
                        },
                    reasoningEffort = config?.reasoningEffort,
                    temperature = config?.temperature?.toFloat(),
                    topK = config?.topK,
                    topP = config?.topP?.toFloat(),
                    maxTokens = config?.maxTokens,
                    contextWindow = config?.contextWindow,
                    maxTokensDisabled = config?.maxTokensDisabled ?: false,
                    temperatureDisabled = config?.temperatureDisabled ?: false,
                    topPDisabled = config?.topPDisabled ?: false,
                    topKDisabled = config?.topKDisabled ?: false,
                    minPDisabled = config?.minPDisabled ?: false,
                    frequencyPenaltyDisabled = config?.frequencyPenaltyDisabled ?: false,
                    presencePenaltyDisabled = config?.presencePenaltyDisabled ?: false,
                    toolingEnabled = toolingEnabled,
                    toolCatalog = toolCatalog,
                    activeTools = activeTools,
                    toolCallStrategy = strategy,
                    chatId = chatId,
                    userMessageId = userMessageId,
                    assistantMessageId = assistantMessageId,
                ),
        )
    }

    private fun logLocalPrompt(
        prompt: String,
        modelType: ModelType,
    ) {
        val containsImageDescription = prompt.contains("Attached image description:")
        val chunks = prompt.chunked(PROMPT_LOG_CHUNK_SIZE)
        loggingPort.debug(
            TAG,
            "Local prompt handoff modelType=$modelType chars=${prompt.length} " +
                "containsImageDescription=$containsImageDescription " +
                "containsDataUri=${prompt.contains("data:image", ignoreCase = true)} " +
                "containsBase64Marker=${prompt.contains("base64,", ignoreCase = true)}",
        )
        chunks.forEachIndexed { index, chunk ->
            loggingPort.debug(
                TAG,
                "Local prompt handoff chunk ${index + 1}/${chunks.size} modelType=$modelType:\n$chunk",
            )
        }
    }

    companion object {
        private const val TAG = "GenerateChatResponse"
        private const val PROMPT_LOG_CHUNK_SIZE = 2_000

        private val PRELOADED_TOOL_NAMES =
            setOf(
                ToolDefinition.DISCLOSE_TOOL_SCHEMAS.name,
                ToolDefinition.WEB_SEARCH.name,
                ToolDefinition.WEB_EXTRACT.name,
                ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
            )

        private val X_STATUS_URL_REGEX = Regex(
            """https?://(?:www\.)?(?:x|twitter)\.com/[a-zA-Z0-9_]+/status/(\d+)""",
            RegexOption.IGNORE_CASE
        )

        fun parseTweetIdFromUrl(tweetUrlOrId: String): String {
            val match = X_STATUS_URL_REGEX.find(tweetUrlOrId)
            if (match != null) {
                return match.groupValues[1]
            }
            // Fallback if it is already just the numeric ID or something else
            val numericOnly = tweetUrlOrId.filter { it.isDigit() }
            return numericOnly.takeIf { it.isNotEmpty() } ?: tweetUrlOrId
        }
    }
}

data class PreparedChatInferenceRequest(
    val prompt: String,
    val options: GenerationOptions,
    val preRetrievalSources: List<WebSearchSource> = emptyList(),
    val preRetrievalXResultJson: String? = null,
)

internal enum class ChatImageHandling {
    NONE,
    DIRECT,
    TOOL,
}

internal fun prepareChatPrompt(
    prompt: String,
    hasImageContext: Boolean,
    imageHandling: ChatImageHandling,
    fileAttachments: List<com.browntowndev.enclave.domain.model.chat.FileAttachment> = emptyList(),
): String {
    val filePrefix =
        if (fileAttachments.isNotEmpty()) {
            fileAttachments.joinToString(separator = "\n\n") { file ->
                """
                File attached: ${file.name}
                Content:
                ${file.content}
                """.trimIndent()
            }
        } else {
            null
        }

    val imagePrefix =
        if (hasImageContext && imageHandling != ChatImageHandling.NONE) {
            when (imageHandling) {
                ChatImageHandling.DIRECT -> "The user attached one or more images."
                ChatImageHandling.TOOL -> "The user attached one or more images. If you need to inspect them, use attached_image_inspect."
                ChatImageHandling.NONE -> "The user attached one or more images."
            }
        } else {
            null
        }

    val contextHeader =
        listOfNotNull(filePrefix, imagePrefix)
            .joinToString(separator = "\n\n")

    if (contextHeader.isBlank()) return prompt

    return if (prompt.isBlank()) {
        """
        $contextHeader

        Respond helpfully based on the context.
        """.trimIndent()
    } else {
        """
        $contextHeader

        User request:
        ${prompt.trim()}
        """.trimIndent()
    }
}

private object DummySearchIntentClassifier : SearchIntentClassifierPort {
    override fun requiresSearch(embedding: FloatArray, threshold: Float): Boolean = false
    override fun getSearchIntentProbability(embedding: FloatArray): Float = 0.0f
}

private object DummyToolExecutor : ToolExecutorPort {
    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        return ToolExecutionResult(request.toolName, "{}")
    }
}
