package com.browntowndev.enclave.domain.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToolCosineSimilarityTest {
    @Test
    fun identical_normalized_vectors_score_one() {
        val vector = ToolCosineSimilarity.l2Normalize(floatArrayOf(1f, 2f, 3f))
        assertEquals(1.0, ToolCosineSimilarity.cosine(vector, vector), 1e-6)
    }

    @Test
    fun orthogonal_vectors_score_zero() {
        val left = ToolCosineSimilarity.l2Normalize(floatArrayOf(1f, 0f))
        val right = ToolCosineSimilarity.l2Normalize(floatArrayOf(0f, 1f))
        assertEquals(0.0, ToolCosineSimilarity.cosine(left, right), 1e-6)
    }
}
