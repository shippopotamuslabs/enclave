package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.flow.Flow

interface DefaultModelRepositoryPort {
    suspend fun getDefault(modelType: ModelType): DefaultModelAssignment?

    fun observeDefaults(): Flow<List<DefaultModelAssignment>>

    suspend fun setDefault(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId? = null,
        apiConfigId: ApiModelConfigurationId? = null,
        ttsProviderId: TtsProviderId? = null,
        mediaProviderId: MediaProviderId? = null,
        liveVoiceProviderId: LiveVoiceProviderId? = null,
    )

    suspend fun clearDefault(modelType: ModelType)
}
