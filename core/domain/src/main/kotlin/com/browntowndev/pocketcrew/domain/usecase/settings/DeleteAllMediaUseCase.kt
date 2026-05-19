package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import javax.inject.Inject

/** Deletes every studio media asset via [studioRepository]. */
class DeleteAllMediaUseCase
    @Inject
    constructor(
        private val studioRepository: StudioRepositoryPort,
    ) {
        suspend operator fun invoke() {
            studioRepository.deleteAllMedia()
        }
    }
