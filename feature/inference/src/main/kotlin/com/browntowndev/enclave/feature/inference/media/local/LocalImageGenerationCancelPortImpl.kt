package com.browntowndev.enclave.feature.inference.media.local

import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCancelPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalImageGenerationCancelPortImpl
    @Inject
    constructor(
        private val engine: LocalImageGenerationEngine,
    ) : LocalImageGenerationCancelPort {
        override fun cancelActiveGeneration() {
            engine.cancel()
        }
    }
