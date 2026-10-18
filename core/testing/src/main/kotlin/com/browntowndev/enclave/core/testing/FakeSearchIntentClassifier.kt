package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.port.inference.SearchIntentClassifierPort

/** [SearchIntentClassifierPort] fake (ENC-28) with scripted answers. */
class FakeSearchIntentClassifier : SearchIntentClassifierPort {
    var requiresSearchResult: Boolean = false
    var probability: Float = 0f
    var requiresSearchCallCount = 0
        private set

    /** Number of [getSearchIntentProbability] calls (ENC-28). */
    var probabilityCallCount = 0
        private set

    override fun requiresSearch(embedding: FloatArray, threshold: Float): Boolean {
        requiresSearchCallCount++
        return requiresSearchResult
    }

    override fun getSearchIntentProbability(embedding: FloatArray): Float {
        probabilityCallCount++
        return probability
    }
}
