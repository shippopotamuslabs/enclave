package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
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
        val searchEnabled = settings.searchEnabled
        val xSearchEnabled = settings.xSearchEnabled
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
        val retrievedMemories =
            if (prompt.isNotBlank()) {
                val embedding = embeddingEnginePort.getEmbedding(prompt)
                memoriesRepository.searchMemories(embedding)
            } else {
                emptyList()
            }

        loggingPort.debug(
            TAG,
            "generateWithService config modelType=$modelType configName=${config?.name} " +
                "isLocal=${config?.isLocal} searchEnabled=$searchEnabled xSearchEnabled=$xSearchEnabled " +
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

        val systemPrompt =
            if (toolingEnabled) {
                promptComposer.compose(
                    baseSystemPrompt = config?.systemPrompt,
                    includeSearchTool = searchEnabled,
                    includeXSearchTool = xSearchEnabled,
                    includeImageInspectTool = imageHandling == ChatImageHandling.TOOL,
                    includeMemoryTools = true,
                    includeArtifactTool = true,
                    includeImageGenerationTool = imageTool != null,
                    includeVideoGenerationTool = videoTool != null,
                    includeMusicGenerationTool = musicTool != null,
                    currentChatId = chatId.value,
                    strategy = strategy,
                    coreMemories = coreMemories,
                    retrievedMemories = retrievedMemories,
                )
            } else {
                config?.systemPrompt
            }

        val availableTools =
            buildList {
                if (settings.searchEnabled) {
                    add(ToolDefinition.WEB_SEARCH)
                    add(ToolDefinition.WEB_EXTRACT)
                }
                if (settings.xSearchEnabled) {
                    add(ToolDefinition.X_SEARCH)
                }
                if (imageHandling == ChatImageHandling.TOOL) add(ToolDefinition.ATTACHED_IMAGE_INSPECT)
                add(ToolDefinition.SEARCH_CHAT_HISTORY)
                add(ToolDefinition.SEARCH_CHAT)
                add(ToolDefinition.GET_MESSAGE_CONTEXT)
                add(ToolDefinition.MANAGE_MEMORIES)
                add(ToolDefinition.START_ARTIFACT)
                add(ToolDefinition.APPEND_BLOCKS)
                add(ToolDefinition.SEARCH_CURRENT_ARTIFACT)
                imageTool?.let { add(it) }
                videoTool?.let { add(it) }
                musicTool?.let { add(it) }
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
            options =
                GenerationOptions(
                    reasoningBudget = reasoningBudget,
                    modelType = modelType,
                    modelId = config?.modelId,
                    apiProvider = config?.apiProvider,
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
                    availableTools = availableTools,
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

    private companion object {
        private const val TAG = "GenerateChatResponse"
        private const val PROMPT_LOG_CHUNK_SIZE = 2_000
    }
}

data class PreparedChatInferenceRequest(
    val prompt: String,
    val options: GenerationOptions,
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
