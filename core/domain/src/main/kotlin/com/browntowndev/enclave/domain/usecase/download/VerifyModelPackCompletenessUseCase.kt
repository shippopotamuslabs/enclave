package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackVerificationDepth
import com.browntowndev.enclave.domain.port.download.ModelPackVerifierPort
import javax.inject.Inject

class VerifyModelPackCompletenessUseCase
    @Inject
    constructor(
        private val modelPackVerifier: ModelPackVerifierPort,
        private val resolveModelCatalog: ResolveModelCatalogUseCase,
    ) {
        suspend operator fun invoke(packId: String): Result<ModelPackCompleteness> =
            runCatching {
                val catalog = resolveModelCatalog().getOrThrow()
                modelPackVerifier.verifyPackById(
                    catalog,
                    packId,
                    ModelPackVerificationDepth.SIZE_ON_DISK,
                )
                    ?: throw IllegalArgumentException("Unknown model pack: $packId")
            }
    }
