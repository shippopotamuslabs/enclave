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

    /** When set, [getActiveConfiguration] throws this instead of returning (ENC-28). */
    var failure: Throwable? = null

    /** Optional per-type overrides (ENC-28): a type present here wins over [configuration]. */
    val configurationsByType = mutableMapOf<ModelType, ActiveModelConfiguration?>()

    /** Every override passed to [getActiveConfigurationByOverride], in call order. */
    val requestedOverrides = mutableListOf<ChatModelOverride>()

    /** Result for [getActiveConfigurationByOverride] when [hasOverrideConfiguration] is set. */
    var overrideConfiguration: ActiveModelConfiguration? = null

    /** When true, [getActiveConfigurationByOverride] returns [overrideConfiguration] instead of [configuration]. */
    var hasOverrideConfiguration: Boolean = false

    override suspend fun getActiveConfiguration(modelType: ModelType): ActiveModelConfiguration? {
        requestedModelTypes += modelType
        failure?.let { throw it }
        return if (configurationsByType.containsKey(modelType)) {
            configurationsByType[modelType]
        } else {
            configuration
        }
    }

    override suspend fun getActiveConfigurationByOverride(override: ChatModelOverride): ActiveModelConfiguration? {
        requestedOverrides += override
        return if (hasOverrideConfiguration) overrideConfiguration else configuration
    }
}
