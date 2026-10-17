package com.browntowndev.enclave.feature.chat.service

import android.content.Context
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeInferenceFactory
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.FakePromptComposer
import com.browntowndev.enclave.core.testing.FakeSearchIntentClassifier
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.usecase.chat.DirectChatInferenceExecutor
import com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.MediaToolDefinitionFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** One recorded executor invocation (shared by the two executor fakes below). */
data class RecordedExecuteCall(
    val prompt: String,
    val userMessageId: MessageId,
    val assistantMessageId: MessageId,
    val chatId: ChatId,
    val userHasImage: Boolean,
    val modelType: ModelType?,
    val modelOverride: ChatModelOverride?,
    val backgroundInferenceEnabled: Boolean,
)

/** Recording [ChatInferenceServiceStarter] fake (ENC-28); never touches the real Context. */
class FakeChatInferenceServiceStarter(
    context: Context,
    loggingPort: LoggingPort,
) : ChatInferenceServiceStarter(context, loggingPort) {
    data class StartCall(
        val prompt: String,
        val userMessageId: MessageId,
        val assistantMessageId: MessageId,
        val chatId: ChatId,
        val userHasImage: Boolean,
        val modelType: ModelType?,
        val modelOverride: ChatModelOverride?,
    )

    val startCalls = mutableListOf<StartCall>()
    var startFailure: Throwable? = null

    var stopInferenceCount = 0
        private set

    override fun startService(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
        modelType: ModelType,
    ) {
        startCalls += StartCall(prompt, userMessageId, assistantMessageId, chatId, userHasImage, modelType, null)
        startFailure?.let { throw it }
    }

    override fun startServiceWithOverride(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
        modelOverride: ChatModelOverride,
    ) {
        startCalls += StartCall(prompt, userMessageId, assistantMessageId, chatId, userHasImage, null, modelOverride)
        startFailure?.let { throw it }
    }

    override fun stopInference() {
        stopInferenceCount++
    }
}

/** Recording [DirectChatInferenceExecutor] fake (ENC-28), wired with :core:testing fakes. */
class FakeDirectChatInferenceExecutor :
    DirectChatInferenceExecutor(
        inferenceFactory = FakeInferenceFactory(),
        activeModelProvider = FakeActiveModelProvider(),
        messageRepository = FakeMessageRepository(),
        settingsRepository = FakeSettingsRepository(),
        memoriesRepository = FakeMemoriesRepository(),
        embeddingEnginePort = FakeEmbeddingEngine(),
        promptComposer = FakePromptComposer(),
        defaultModelRepository = FakeDefaultModelRepository(),
        mediaProviderRepository = FakeMediaProviderRepository(),
        mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
        getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
        loggingPort = FakeLoggingPort(),
        searchIntentClassifier = FakeSearchIntentClassifier(),
        toolExecutor = FakeToolExecutor(),
    ) {
    val executeCalls = mutableListOf<RecordedExecuteCall>()
    var stopCount = 0
        private set

    override fun execute(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
        modelType: ModelType,
        backgroundInferenceEnabled: Boolean,
    ): Flow<MessageGenerationState> {
        executeCalls +=
            RecordedExecuteCall(prompt, userMessageId, assistantMessageId, chatId, userHasImage, modelType, null, backgroundInferenceEnabled)
        return emptyFlow()
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
        executeCalls +=
            RecordedExecuteCall(prompt, userMessageId, assistantMessageId, chatId, userHasImage, null, modelOverride, backgroundInferenceEnabled)
        return emptyFlow()
    }

    override fun stop() {
        stopCount++
    }
}

/** Recording [ChatInferenceServiceExecutor] fake (ENC-28). */
class FakeChatInferenceServiceExecutor(
    serviceStarter: FakeChatInferenceServiceStarter,
    loggingPort: LoggingPort,
) : ChatInferenceServiceExecutor(serviceStarter, loggingPort) {
    val executeCalls = mutableListOf<RecordedExecuteCall>()
    var stopCount = 0
        private set

    override fun execute(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
        modelType: ModelType,
        backgroundInferenceEnabled: Boolean,
    ): Flow<MessageGenerationState> {
        executeCalls +=
            RecordedExecuteCall(prompt, userMessageId, assistantMessageId, chatId, userHasImage, modelType, null, backgroundInferenceEnabled)
        return emptyFlow()
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
        executeCalls +=
            RecordedExecuteCall(prompt, userMessageId, assistantMessageId, chatId, userHasImage, null, modelOverride, backgroundInferenceEnabled)
        return emptyFlow()
    }

    override fun stop() {
        stopCount++
    }
}
