package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.CleanupRepository
import javax.inject.Inject

/** Deletes persisted artifacts/documents via [cleanupRepository]. */
open class DeleteAllArtifactsUseCase
    @Inject
    constructor(
        private val cleanupRepository: CleanupRepository,
    ) {
        open suspend operator fun invoke() {
            cleanupRepository.deleteAllArtifacts()
        }
    }
