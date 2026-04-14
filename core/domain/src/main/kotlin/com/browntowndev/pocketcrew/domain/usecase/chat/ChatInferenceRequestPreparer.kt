package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.first

internal class ChatInferenceRequestPreparer(
    private val activeModelProvider: ActiveModelProviderPort,
    private val settingsRepository: SettingsRepository,
    private val messageRepository: MessageRepository,
    private val searchToolPromptComposer: SearchToolPromptComposer,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        prompt: String,
        chatId: ChatId,
        userMessageId: MessageId,
        modelType: ModelType,
    ): PreparedChatInferenceRequest {
        val settings = settingsRepository.settingsFlow.first()
        val config = activeModelProvider.getActiveConfiguration(modelType)
        val visionConfig = activeModelProvider.getActiveConfiguration(ModelType.VISION)
        val resolvedImageTarget = messageRepository.resolveLatestImageBearingUserMessage(chatId, userMessageId)
        val searchEnabled = settings.searchEnabled
        val activeVisionCapable = config?.visionCapable == true
        val apiVisionConfigured = visionConfig?.isLocal == false && visionConfig.visionCapable
        val hasImageContext = resolvedImageTarget != null
        val imageHandling = when {
            hasImageContext &&
                activeVisionCapable &&
                !settings.alwaysUseVisionModel -> ChatImageHandling.DIRECT
            hasImageContext &&
                apiVisionConfigured &&
                (settings.alwaysUseVisionModel || !activeVisionCapable) -> ChatImageHandling.TOOL
            else -> ChatImageHandling.NONE
        }
        val reasoningBudget = if (config?.isLocal == true && config.thinkingEnabled) 2048 else 0

        loggingPort.debug(
            TAG,
            "generateWithService config modelType=$modelType configName=${config?.name} isLocal=${config?.isLocal} searchEnabled=$searchEnabled imageHandling=$imageHandling activeVisionCapable=$activeVisionCapable apiVisionConfigured=$apiVisionConfigured alwaysUseVisionModel=${settings.alwaysUseVisionModel} thinkingEnabled=${config?.thinkingEnabled} reasoningEffort=${config?.reasoningEffort} derivedReasoningBudget=$reasoningBudget",
        )
        if (config?.isLocal == true) {
            logLocalPrompt(prompt, modelType)
        }

        val strategy = when {
            config?.isLocal == true -> when (config.localModelFormat) {
                com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.LITERTLM -> ToolCallStrategy.LITE_RT_NATIVE
                com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.GGUF -> ToolCallStrategy.JSON_XML_ENVELOPE
                com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.TASK -> ToolCallStrategy.JSON_XML_ENVELOPE
                null -> ToolCallStrategy.JSON_XML_ENVELOPE
            }
            config?.isLocal == false -> ToolCallStrategy.SDK_NATIVE
            else -> ToolCallStrategy.JSON_XML_ENVELOPE
        }

        val toolingEnabled = searchEnabled || imageHandling == ChatImageHandling.TOOL
        val systemPrompt = if (toolingEnabled) {
            searchToolPromptComposer.compose(
                baseSystemPrompt = config?.systemPrompt,
                includeSearchTool = searchEnabled,
                includeImageInspectTool = imageHandling == ChatImageHandling.TOOL,
                strategy = strategy,
            )
        } else {
            config?.systemPrompt
        }
        val availableTools = buildList {
            if (searchEnabled) add(ToolDefinition.TAVILY_WEB_SEARCH)
            if (imageHandling == ChatImageHandling.TOOL) add(ToolDefinition.ATTACHED_IMAGE_INSPECT)
        }

        return PreparedChatInferenceRequest(
            prompt = prepareChatPrompt(
                prompt = prompt,
                hasImageContext = hasImageContext,
                imageHandling = imageHandling,
            ),
            options = GenerationOptions(
                reasoningBudget = reasoningBudget,
                modelType = modelType,
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
                toolingEnabled = toolingEnabled,
                availableTools = availableTools,
                chatId = chatId,
                userMessageId = userMessageId,
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

internal data class PreparedChatInferenceRequest(
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
): String {
    if (!hasImageContext || imageHandling == ChatImageHandling.NONE) return prompt

    val prefix = when (imageHandling) {
        ChatImageHandling.DIRECT -> "The user attached an image."
        ChatImageHandling.TOOL -> "The user attached an image. If you need to inspect it, use attached_image_inspect."
        ChatImageHandling.NONE -> "The user attached an image."
    }

    return if (prompt.isBlank()) {
        """
        $prefix

        Respond helpfully based on the image and any other context.
        """.trimIndent()
    } else {
        """
        $prefix

        User request:
        ${prompt.trim()}
        """.trimIndent()
    }
}
