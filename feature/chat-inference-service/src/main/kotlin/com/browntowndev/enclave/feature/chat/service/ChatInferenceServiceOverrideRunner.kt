package com.browntowndev.enclave.feature.chat.service

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort

internal suspend fun runChatInferenceWithService(
    inferenceFactory: InferenceFactoryPort,
    activeModelProvider: ActiveModelProviderPort,
    loggingPort: LoggingPort,
    assistantMessageId: MessageId,
    chatId: ChatId,
    modelType: ModelType,
    modelOverride: ChatModelOverride?,
    generateWithService: suspend (
        service: LlmInferencePort,
        explicitConfiguration: ActiveModelConfiguration?,
    ) -> Unit,
) {
    if (modelOverride == null) {
        inferenceFactory.withInferenceService(modelType) { service ->
            loggingPort.debug(
                ChatInferenceService.TAG,
                "withInferenceService entered chat=${chatId.value} assistantMessageId=${assistantMessageId.value}",
            )
            generateWithService(service, null)
        }
        return
    }

    val explicitConfiguration =
        activeModelProvider.getActiveConfigurationByOverride(modelOverride)
            ?: throw IllegalStateException(
                "Override configuration not found for ${modelOverride.displayName}",
            )
    when (modelOverride) {
        is ChatModelOverride.Local -> {
            inferenceFactory.withInferenceServiceForLocalConfig(modelOverride.configId) { service ->
                loggingPort.debug(
                    ChatInferenceService.TAG,
                    "withInferenceServiceForLocalConfig entered chat=${chatId.value} " +
                        "assistantMessageId=${assistantMessageId.value}",
                )
                generateWithService(service, explicitConfiguration)
            }
        }
        is ChatModelOverride.Api -> {
            val configId = modelOverride.configId as ApiModelConfigurationId
            inferenceFactory.withApiServiceForConfig(configId) { service ->
                loggingPort.debug(
                    ChatInferenceService.TAG,
                    "withApiServiceForConfig entered chat=${chatId.value} " +
                        "assistantMessageId=${assistantMessageId.value}",
                )
                generateWithService(service, explicitConfiguration)
            }
        }
    }
}
