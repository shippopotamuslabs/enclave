package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType

interface UtilityModelFilePort {
    suspend fun resolveUtilityModelPath(utilityType: UtilityType): String?

    /**
     * Resolves the absolute on-disk path of a downloaded local model file, or `null` when the
     * file is not present or fails its persisted size/checksum metadata.
     */
    suspend fun resolveLocalModelPath(metadata: LocalModelMetadata): String?
}
