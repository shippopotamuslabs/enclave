package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort

/** [ActiveModelProviderPort] fake (ENC-28): serves one canned [configuration] for every request. */
class FakeActiveModelProvider(
    var configuration: ActiveModelConfiguration? = null,
) : ActiveModelProviderPort {
    val requestedModelTypes = mutableListOf<ModelType>()

    override suspend fun getActiveConfiguration(modelType: ModelType): ActiveModelConfiguration? {
        requestedModelTypes += modelType
        return configuration
    }

    override suspend fun getActiveConfigurationByOverride(override: ChatModelOverride): ActiveModelConfiguration? =
        configuration
}
