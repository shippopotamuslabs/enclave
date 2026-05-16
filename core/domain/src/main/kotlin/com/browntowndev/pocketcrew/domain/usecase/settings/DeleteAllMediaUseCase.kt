package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Deletes every studio media asset via [studioRepository]. */
class DeleteAllMediaUseCase
    @Inject
    constructor(
        private val studioRepository: StudioRepositoryPort,
    ) {
        suspend operator fun invoke() {
            studioRepository.observeAllMedia().first().forEach { asset ->
                studioRepository.deleteMedia(asset.id)
            }
        }
    }
