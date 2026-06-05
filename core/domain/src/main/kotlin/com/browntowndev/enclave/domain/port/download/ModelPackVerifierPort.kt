package com.browntowndev.enclave.domain.port.download

import com.browntowndev.enclave.domain.model.download.ModelPack
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackVerificationDepth
import com.browntowndev.enclave.domain.model.download.ParsedModelCatalog

interface ModelPackVerifierPort {
    suspend fun verifyPack(
        pack: ModelPack,
        depth: ModelPackVerificationDepth = ModelPackVerificationDepth.SIZE_ON_DISK,
    ): ModelPackCompleteness

    suspend fun verifyPackById(
        catalog: ParsedModelCatalog,
        packId: String,
        depth: ModelPackVerificationDepth = ModelPackVerificationDepth.SIZE_ON_DISK,
    ): ModelPackCompleteness?
}
