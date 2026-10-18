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

    /** Optional per-type overrides (ENC-28): a type present here wins over [configuration]. */
    val configurationsByType = mutableMapOf<ModelType, ActiveModelConfiguration?>()

    override suspend fun getActiveConfiguration(modelType: ModelType): ActiveModelConfiguration? {
        requestedModelTypes += modelType
        return if (configurationsByType.containsKey(modelType)) {
            configurationsByType[modelType]
        } else {
            configuration
        }
    }

    override suspend fun getActiveConfigurationByOverride(override: ChatModelOverride): ActiveModelConfiguration? =
        configuration
}
