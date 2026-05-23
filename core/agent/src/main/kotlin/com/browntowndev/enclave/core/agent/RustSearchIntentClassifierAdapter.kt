package com.browntowndev.enclave.core.agent

import android.util.Log
import com.browntowndev.enclave.core.agent.classifier.classifySearchIntent
import com.browntowndev.enclave.domain.port.inference.SearchIntentClassifierPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RustSearchIntentClassifierAdapter @Inject constructor() : SearchIntentClassifierPort {
    override fun requiresSearch(embedding: FloatArray, threshold: Float): Boolean {
        return classifySearchIntent(embedding.toList(), threshold)
    }

    override fun getSearchIntentProbability(embedding: FloatArray): Float {
        val embeddingList = embedding.toList()
        Log.d(TAG, "getSearchIntentProbability called with embedding size=${embeddingList.size}")
        val result = com.browntowndev.enclave.core.agent.classifier.getSearchIntentProbability(embeddingList)
        Log.d(TAG, "getSearchIntentProbability result=$result")
        return result
    }

    private companion object {
        private const val TAG = "SearchIntentClassifier"
    }
}
