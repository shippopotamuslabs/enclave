package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.first

class ChatInferenceRequestPreparer(
    private val activeModelProvider: ActiveModelProviderPort,
    private val settingsRepository: SettingsRepository,
    private val messageRepository: MessageRepository,
    private val memoriesRepository: MemoriesRepository,
    private val embeddingEnginePort: EmbeddingEnginePort,
    private val searchToolPromptComposer: SearchToolPromptComposer,
    private val loggingPort: LoggingPort,
) {
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
        val activeVisionCapable = config?.isMultimodal == true
        val apiVisionConfigured = visionConfig?.isLocal == false && visionConfig.isMultimodal
        val hasImageContext = resolvedImageTarget != null
        val imageHandling = when {
            hasImageContext && activeVisionCapable -> ChatImageHandling.DIRECT
            hasImageContext && apiVisionConfigured && !activeVisionCapable -> ChatImageHandling.TOOL
            else -> ChatImageHandling.NONE
        }
        val reasoningBudget = if (config?.isLocal == true && config.thinkingEnabled) 2048 else 0

        val coreMemories = memoriesRepository.getCoreMemories()
        val retrievedMemories = if (prompt.isNotBlank()) {
            val embedding = embeddingEnginePort.getEmbedding(prompt)
            memoriesRepository.searchMemories(embedding)
        } else {
            emptyList()
        }

        loggingPort.debug(
            TAG,
            "generateWithService config modelType=$modelType configName=${config?.name} isLocal=${config?.isLocal} searchEnabled=$searchEnabled imageHandling=$imageHandling activeVisionCapable=$activeVisionCapable apiVisionConfigured=$apiVisionConfigured thinkingEnabled=${config?.thinkingEnabled} reasoningEffort=${config?.reasoningEffort} derivedReasoningBudget=$reasoningBudget",
        )
        if (config?.isLocal == true) {
            logLocalPrompt(prompt, modelType)
        }

        val strategy = when {
            config?.isLocal == true -> when (config.localModelFormat) {
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
        val systemPrompt = if (toolingEnabled) {
            searchToolPromptComposer.compose(
                baseSystemPrompt = config?.systemPrompt,
                includeSearchTool = searchEnabled,
                includeImageInspectTool = imageHandling == ChatImageHandling.TOOL,
                includeMemoryTools = true,
                includeArtifactTool = true,
                currentChatId = chatId.value,
                strategy = strategy,
                coreMemories = coreMemories,
                retrievedMemories = retrievedMemories,
            )
        } else {
            config?.systemPrompt
        }
        val availableTools = buildList {
            if (settings.searchEnabled) {
                add(ToolDefinition.WEB_SEARCH)
                add(ToolDefinition.WEB_EXTRACT)
            }
            if (imageHandling == ChatImageHandling.TOOL) add(ToolDefinition.ATTACHED_IMAGE_INSPECT)
            add(ToolDefinition.SEARCH_CHAT_HISTORY)
            add(ToolDefinition.SEARCH_CHAT)
            add(ToolDefinition.GET_MESSAGE_CONTEXT)
            add(ToolDefinition.MANAGE_MEMORIES)
            add(ToolDefinition.GENERATE_ARTIFACT)
        }

        return PreparedChatInferenceRequest(
            prompt = prepareChatPrompt(
                prompt = prompt,
                hasImageContext = hasImageContext,
                imageHandling = imageHandling,
                fileAttachments = userMessage?.content?.fileAttachments ?: emptyList(),
            ),
            options = GenerationOptions(
                reasoningBudget = reasoningBudget,
                modelType = modelType,
                modelId = config?.modelId,
                apiProvider = config?.apiProvider,
                systemPrompt = systemPrompt,
                imageUris = if (imageHandling == ChatImageHandling.DIRECT) {
                    resolvedImageTarget?.imageUri?.let { listOf(it) }.orEmpty()
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
            "Local prompt handoff modelType=$modelType chars=${prompt.length} containsImageDescription=$containsImageDescription containsDataUri=${prompt.contains("data:image", ignoreCase = true)} containsBase64Marker=${prompt.contains("base64,", ignoreCase = true)}",
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
    fileAttachments: List<com.browntowndev.pocketcrew.domain.model.chat.FileAttachment> = emptyList(),
): String {
    val filePrefix = if (fileAttachments.isNotEmpty()) {
        fileAttachments.joinToString(separator = "\n\n") { file ->
            """
            File attached: ${file.name}
            Content:
            ${file.content}
            """.trimIndent()
        }
    } else null

    val imagePrefix = if (hasImageContext && imageHandling != ChatImageHandling.NONE) {
        when (imageHandling) {
            ChatImageHandling.DIRECT -> "The user attached an image."
            ChatImageHandling.TOOL -> "The user attached an image. If you need to inspect it, use attached_image_inspect."
            ChatImageHandling.NONE -> "The user attached an image."
        }
    } else null

    val contextHeader = listOfNotNull(filePrefix, imagePrefix)
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
