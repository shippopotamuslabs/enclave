package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import javax.inject.Inject

/** Deletes every memory row tracked by [memoriesRepository]. */
class DeleteAllMemoriesUseCase
    @Inject
    constructor(
        private val memoriesRepository: MemoriesRepository,
    ) {
        suspend operator fun invoke() {
            memoriesRepository.deleteAllMemories()
        }
    }
