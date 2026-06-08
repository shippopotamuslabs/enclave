package com.browntowndev.enclave.domain.search

import com.browntowndev.enclave.domain.model.inference.ToolSearchThresholds
import kotlin.math.sqrt

/**
 * Cosine similarity for L2-normalized embedding vectors (dot product).
 */
object ToolCosineSimilarity {
    fun cosine(
        left: FloatArray,
        right: FloatArray,
    ): Double {
        if (left.isEmpty() || right.isEmpty() || left.size != right.size) return 0.0
        var dot = 0.0
        for (index in left.indices) {
            dot += left[index] * right[index]
        }
        return dot
    }

    fun l2Normalize(vector: FloatArray): FloatArray {
        val norm =
            sqrt(vector.sumOf { (it * it).toDouble() })
                .toFloat()
                .coerceAtLeast(ToolSearchThresholds.L2_NORM_EPSILON)
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }
}
