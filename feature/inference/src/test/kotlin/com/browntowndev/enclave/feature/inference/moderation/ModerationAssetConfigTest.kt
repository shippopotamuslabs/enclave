package com.browntowndev.enclave.feature.inference.moderation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModerationAssetConfigTest {
    @Test
    fun parse_readsModelTokenizerLabelsAndThresholds() {
        val config =
            ModerationAssetConfig.parse(
                """
                {
                  "model_path": "model_quantized.onnx",
                  "tokenizer_path": "tokenizer.json",
                  "max_sequence_length": 384,
                  "labels": ["OK", "S3"],
                  "label_aliases": {
                    "sexual/minors": ["S3"]
                  },
                  "thresholds": {
                    "sexual/minors": 0.65
                  },
                  "combined_illegal_labels": ["S3"]
                }
                """.trimIndent(),
            )

        assertEquals("model_quantized.onnx", config.modelPath)
        assertEquals("tokenizer.json", config.tokenizerPath)
        assertEquals(384, config.maxSequenceLength)
        assertEquals(listOf("OK", "S3"), config.labels)
        assertEquals(mapOf("sexual/minors" to listOf("S3")), config.labelAliases)
        assertEquals(mapOf("sexual/minors" to 0.65), config.thresholds)
        assertEquals(listOf("S3"), config.combinedIllegalLabels)
    }
}
