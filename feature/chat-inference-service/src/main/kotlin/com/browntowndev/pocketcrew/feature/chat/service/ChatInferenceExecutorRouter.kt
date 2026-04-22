package com.browntowndev.pocketcrew.feature.chat.service

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.pocketcrew.domain.usecase.chat.DirectChatInferenceExecutor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Routes chat inference execution between a direct (in-process) executor
 * and a service-backed (foreground-service) executor based on the
 * [backgroundInferenceEnabled] flag.
 */
class ChatInferenceExecutorRouter @Inject constructor(
    private val directExecutor: DirectChatInferenceExecutor,
    private val serviceExecutor: ChatInferenceServiceExecutor,
) : ChatInferenceExecutorPort {

    override fun execute(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
        modelType: ModelType,
        backgroundInferenceEnabled: Boolean,
    ): Flow<MessageGenerationState> {
        return if (backgroundInferenceEnabled) {
            serviceExecutor.execute(
                prompt = prompt,
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = userHasImage,
                modelType = modelType,
                backgroundInferenceEnabled = true,
            )
        } else {
            directExecutor.execute(
                prompt = prompt,
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = userHasImage,
                modelType = modelType,
                backgroundInferenceEnabled = false,
            )
        }
    }

    override fun stop() {
        // Stop both executors — the direct executor's stop() is a no-op,
        // and the service executor sends a stop intent to the FGS.
        directExecutor.stop()
        serviceExecutor.stop()
    }
}