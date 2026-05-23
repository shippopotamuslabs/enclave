package com.browntowndev.enclave.domain.port.inference

interface SearchIntentClassifierPort {
    fun requiresSearch(embedding: FloatArray, threshold: Float = 0.5f): Boolean
    fun getSearchIntentProbability(embedding: FloatArray): Float
}
