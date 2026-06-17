package com.browntowndev.enclave.feature.inference.utilityonnx

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializes utility ONNX session loads so title generation and compaction summarization
 * never mmap two ~350 MiB LFM2 graphs concurrently.
 */
@Singleton
class UtilityOnnxLoadCoordinator
    @Inject
    constructor() {
        private val loadMutex = Mutex()

        suspend fun <T> withExclusiveLoad(block: suspend () -> T): T = loadMutex.withLock { block() }
    }
