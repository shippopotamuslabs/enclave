package com.browntowndev.enclave.core.testing

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
import kotlinx.coroutines.flow.update

/** In-memory [DefaultModelRepositoryPort] fake (ENC-28). */
class FakeDefaultModelRepository : DefaultModelRepositoryPort {
    private val defaultsFlow = MutableStateFlow<List<DefaultModelAssignment>>(emptyList())

    fun seed(assignment: DefaultModelAssignment) {
        defaultsFlow.update { current -> current.filterNot { it.modelType == assignment.modelType } + assignment }
    }

    override suspend fun getDefault(modelType: ModelType): DefaultModelAssignment? =
        defaultsFlow.value.firstOrNull { it.modelType == modelType }

    override fun observeDefaults(): Flow<List<DefaultModelAssignment>> = defaultsFlow.asStateFlow()

    override suspend fun setDefault(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId?,
        mediaProviderId: MediaProviderId?,
        liveVoiceProviderId: LiveVoiceProviderId?,
    ) {
        seed(
            DefaultModelAssignment(
                modelType = modelType,
                localConfigId = localConfigId,
                apiConfigId = apiConfigId,
                ttsProviderId = ttsProviderId,
                mediaProviderId = mediaProviderId,
                liveVoiceProviderId = liveVoiceProviderId,
            ),
        )
    }

    override suspend fun clearDefault(modelType: ModelType) {
        defaultsFlow.update { current -> current.filterNot { it.modelType == modelType } }
    }
}
