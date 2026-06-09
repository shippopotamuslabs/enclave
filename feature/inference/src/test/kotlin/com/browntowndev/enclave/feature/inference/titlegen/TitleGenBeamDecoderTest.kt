package com.browntowndev.enclave.feature.inference.titlegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.exp

class TitleGenBeamDecoderTest {
    private val config =
        TitleGenAssetConfig.parse(
            """
            {
              "task_prefix": "short title: ",
              "max_input_length": 384,
              "max_output_tokens": 4,
              "beam_width": 4,
              "decoder_start_token_id": 0,
              "eos_token_id": 1,
              "pad_token_id": 0
            }
            """.trimIndent(),
        )

    @Test
    fun `logSoftmax peaks at argmax index`() {
        val logits = floatArrayOf(1f, 3f, 2f)
        val logProbs = logSoftmax(logits)
        assertEquals(1, argmax(logProbs))
        assertTrue(logProbs.all { value -> !value.isNaN() })
        val total = logProbs.sumOf { value -> exp(value.toDouble()) }
        assertEquals(1.0, total, 1e-4)
    }

    @Test
    fun `topKIndices returns highest values`() {
        assertEquals(listOf(2, 3, 1), topKIndices(floatArrayOf(0.1f, 0.2f, 0.9f, 0.3f), k = 3))
    }

    @Test
    fun `beam search prefers higher scoring branch`() {
        val greedyConfig = config.copy(beamWidth = 1)
        val beamConfig = config.copy(beamWidth = 4)

        var greedyStep = 0
        val greedyDecode: (LongArray) -> FloatArray? = { decoderIds ->
            when (greedyStep++) {
                0 -> floatArrayOf(-1f, -5f, -0.5f, 0f)
                else -> floatArrayOf(-1f, -5f, -5f, -5f)
            }
        }
        assertEquals(listOf(3), TitleGenBeamDecoder.decode(greedyConfig, greedyDecode))

        var beamStep = 0
        val beamDecode: (LongArray) -> FloatArray? = { decoderIds ->
            when {
                beamStep == 0 -> {
                    beamStep++
                    floatArrayOf(-1f, -5f, -0.5f, 0f)
                }
                decoderIds.contentEquals(longArrayOf(0, 2)) -> floatArrayOf(-5f, 2f, -5f, -5f)
                else -> floatArrayOf(-1f, -5f, -5f, -5f)
            }
        }
        assertEquals(listOf(2), TitleGenBeamDecoder.decode(beamConfig, beamDecode))
    }

    @Test
    fun `beam search stops on eos without emitting it`() {
        var step = 0
        val decodeStep: (LongArray) -> FloatArray? = { _ ->
            when (step++) {
                0 -> floatArrayOf(-1f, 3f, -1f, 2f)
                else -> floatArrayOf(-1f, 3f, -1f, -1f)
            }
        }

        assertEquals(emptyList<Int>(), TitleGenBeamDecoder.decode(config, decodeStep))
    }
}