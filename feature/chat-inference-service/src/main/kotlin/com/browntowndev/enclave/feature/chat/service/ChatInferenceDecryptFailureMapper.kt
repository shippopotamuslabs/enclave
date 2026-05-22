package com.browntowndev.enclave.feature.chat.service

import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelType

/** Maps vault decrypt failures to user-visible generation terminal states (TEST-03). */
internal object ChatInferenceDecryptFailureMapper {
    fun toFailedState(
        cause: ActiveModelConfigurationDecryptException,
        modelType: ModelType,
    ): MessageGenerationState.Failed =
        MessageGenerationState.Failed(
            error =
                ActiveModelConfigurationDecryptException(
                    modelType = modelType,
                    message = cause.message ?: ActiveModelConfigurationDecryptException.USER_MESSAGE,
                ),
            modelType = modelType,
        )
}
