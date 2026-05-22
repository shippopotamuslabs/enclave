package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.inference.ModelType

interface ActiveModelProviderPort {
    suspend fun getActiveConfiguration(modelType: ModelType): ActiveModelConfiguration?
}
