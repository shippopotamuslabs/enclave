package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.CleanupRepository
import javax.inject.Inject

/** Deletes persisted artifacts/documents via [cleanupRepository]. */
class DeleteAllArtifactsUseCase
    @Inject
    constructor(
        private val cleanupRepository: CleanupRepository,
    ) {
        suspend operator fun invoke() {
            cleanupRepository.deleteAllArtifacts()
        }
    }
