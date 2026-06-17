package com.browntowndev.enclave.domain.usecase.media.test

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake [DefaultModelRepositoryPort] that records every [getDefault] call by [ModelType]
 * so MD01 tests can assert that the default-slot path is never reached when an override is set.
 */
class FakeDefaultModelRepositoryWithCalls : DefaultModelRepositoryPort {
    private val _defaults = MutableStateFlow<Map<ModelType, DefaultModelAssignment>>(emptyMap())
    val getDefaultCalls: MutableSet<ModelType> = mutableSetOf()

    override suspend fun getDefault(modelType: ModelType): DefaultModelAssignment? {
        getDefaultCalls.add(modelType)
        return _defaults.value[modelType]
    }

    override fun observeDefaults(): Flow<List<DefaultModelAssignment>> =
        _defaults.asStateFlow().map { it.values.toList() }

    override suspend fun setDefault(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId?,
        mediaProviderId: MediaProviderId?,
        liveVoiceProviderId: LiveVoiceProviderId?,
    ) {
        val current = _defaults.value.toMutableMap()
        current[modelType] =
            DefaultModelAssignment(
                modelType = modelType,
                localConfigId = localConfigId,
                apiConfigId = apiConfigId,
                ttsProviderId = ttsProviderId,
                mediaProviderId = mediaProviderId,
                liveVoiceProviderId = liveVoiceProviderId,
            )
        _defaults.value = current
    }

    override suspend fun clearDefault(modelType: ModelType) {
        val current = _defaults.value.toMutableMap()
        current.remove(modelType)
        _defaults.value = current
    }

    fun seed(defaults: List<DefaultModelAssignment>) {
        _defaults.value = defaults.associateBy { it.modelType }
    }
}
