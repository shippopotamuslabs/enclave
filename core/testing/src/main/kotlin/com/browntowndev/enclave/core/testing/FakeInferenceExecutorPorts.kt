package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.enclave.domain.port.inference.OnDeviceInferenceCancellationPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Fake [ChatInferenceExecutorPort] (ENC-28). */
class FakeChatInferenceExecutorPort : ChatInferenceExecutorPort {
    var flow: Flow<MessageGenerationState> = emptyFlow()
    val executeCalls = mutableListOf<String>()
    val executeWithOverrideCalls = mutableListOf<String>()

    /** Resolved configurations passed to [executeWithOverride], in call order (ENC-28). */
    val executeWithOverrideConfigurations = mutableListOf<ActiveModelConfiguration>()
    var stopCount = 0

    override fun execute(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
        modelType: ModelType,
        backgroundInferenceEnabled: Boolean,
    ): Flow<MessageGenerationState> {
        executeCalls += prompt
        return flow
    }

    override fun executeWithOverride(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
        modelOverride: ChatModelOverride,
        resolvedConfiguration: ActiveModelConfiguration,
        backgroundInferenceEnabled: Boolean,
    ): Flow<MessageGenerationState> {
        executeWithOverrideCalls += prompt
        executeWithOverrideConfigurations += resolvedConfiguration
        return flow
    }

    override fun stop() {
        stopCount++
    }
}

/** Fake [OnDeviceInferenceCancellationPort] (ENC-28). */
class FakeOnDeviceInferenceCancellationPort : OnDeviceInferenceCancellationPort {
    var cancelCount = 0

    override fun cancelActiveGeneration() {
        cancelCount++
    }
}
