package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.core.agent.classifier.classifySearchIntent
import com.browntowndev.enclave.domain.port.inference.SearchIntentClassifierPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RustSearchIntentClassifierAdapter
    @Inject
    constructor() : SearchIntentClassifierPort {
        override fun requiresSearch(
            embedding: FloatArray,
            threshold: Float,
        ): Boolean = classifySearchIntent(embedding.toList(), threshold)

        override fun getSearchIntentProbability(embedding: FloatArray): Float =
            com.browntowndev.enclave.core.agent.classifier
                .getSearchIntentProbability(embedding.toList())
    }
