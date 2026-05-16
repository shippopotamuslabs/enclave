package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Deletes every memory row tracked by [memoriesRepository]. */
class DeleteAllMemoriesUseCase
    @Inject
    constructor(
        private val memoriesRepository: MemoriesRepository,
    ) {
        suspend operator fun invoke() {
            memoriesRepository.getAllMemoriesFlow().first().forEach { memory ->
                memoriesRepository.deleteMemory(memory.id)
            }
        }
    }
