package com.browntowndev.enclave.domain.port.repository

import kotlinx.coroutines.flow.StateFlow

interface EmbeddingProgressPort {
    val isReindexing: StateFlow<Boolean>
    val progress: StateFlow<Float>
}
