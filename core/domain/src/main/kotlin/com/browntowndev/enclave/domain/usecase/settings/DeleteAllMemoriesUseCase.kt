package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import javax.inject.Inject

/** Deletes every memory row tracked by [memoriesRepository]. */
open class DeleteAllMemoriesUseCase
    @Inject
    constructor(
        private val memoriesRepository: MemoriesRepository,
    ) {
        open suspend operator fun invoke() {
            memoriesRepository.deleteAllMemories()
        }
    }
