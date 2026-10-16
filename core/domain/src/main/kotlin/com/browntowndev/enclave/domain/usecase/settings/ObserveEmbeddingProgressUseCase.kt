package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.EmbeddingProgressPort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes embedding reindex progress without leaking the port to ViewModels (ENC-22). */
class ObserveEmbeddingProgressUseCase @Inject constructor(
    private val port: EmbeddingProgressPort,
) {
    val isReindexing: StateFlow<Boolean> get() = port.isReindexing

    val progress: StateFlow<Float> get() = port.progress
}
