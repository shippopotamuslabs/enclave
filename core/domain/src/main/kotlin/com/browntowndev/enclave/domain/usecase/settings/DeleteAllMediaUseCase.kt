package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import javax.inject.Inject

/** Deletes every studio media asset via [studioRepository]. */
open class DeleteAllMediaUseCase
    @Inject
    constructor(
        private val studioRepository: StudioRepositoryPort,
    ) {
        open suspend operator fun invoke() {
            studioRepository.deleteAllMedia()
        }
    }
