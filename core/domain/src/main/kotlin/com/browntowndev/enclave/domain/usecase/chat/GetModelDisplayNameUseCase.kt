package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import javax.inject.Inject

/**
 * Use case for getting the display name of a model.
 */
open class GetModelDisplayNameUseCase
    @Inject
    constructor(
        private val activeModelProvider: ActiveModelProviderPort,
    ) {
        /**
         * Returns the display name of the model for the given type.
         *
         * @param modelType The type of model to get display name for
         * @return The display name, or empty string if no model is registered
         */
        open suspend operator fun invoke(modelType: ModelType): String =
            try {
                activeModelProvider.getActiveConfiguration(modelType)?.name ?: ""
            } catch (_: ActiveModelConfigurationDecryptException) {
                ActiveModelConfigurationDecryptException.USER_MESSAGE
            }
    }
