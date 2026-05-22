package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.config.UtilityType

interface UtilityModelFilePort {
    suspend fun resolveUtilityModelPath(utilityType: UtilityType): String?
}
