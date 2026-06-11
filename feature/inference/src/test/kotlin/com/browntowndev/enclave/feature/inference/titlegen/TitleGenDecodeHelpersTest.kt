package com.browntowndev.enclave.feature.inference.titlegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for decode helpers that survive the LFM2 rewrite:
 * [argmax], [lastStepLogits], and [describeLogitsHealth].
 */
class TitleGenDecodeHelpersTest {
    @Test
    fun `argmax returns index of largest value`() {
        assertEquals(2, argmax(floatArrayOf(0.1f, 0.2f, 0.9f, 0.3f)))
        assertEquals(0, argmax(floatArrayOf(5f, 1f, 1f)))
    }

    @Test
    fun `lastStepLogits extracts final decode step from 3d output`() {
        // shape [1, 2, 3] -> last step is the second row
        val output: Array<Array<FloatArray>> =
            arrayOf(
                arrayOf(
                    floatArrayOf(1f, 0f, 0f),
                    floatArrayOf(0f, 0f, 9f),
                ),
            )

        val row = lastStepLogits(output)

        assertEquals(2, row?.let(::argmax))
    }

    @Test
    fun `lastStepLogits returns null on unexpected shape`() {
        assertNull(lastStepLogits(null))
        assertNull(lastStepLogits("not a tensor"))
        assertNull(lastStepLogits(arrayOf<Any?>()))
    }

    @Test
    fun `describeLogitsHealth counts nan and inf and includes top5`() {
        val logits = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1.0f, 2.0f)
        val description = describeLogitsHealth(logits)
        assertTrue(description.contains("nan=1"))
        assertTrue(description.contains("+inf=1"))
        assertTrue(description.contains("-inf=1"))
        assertTrue(description.contains("top5="))
    }
}
