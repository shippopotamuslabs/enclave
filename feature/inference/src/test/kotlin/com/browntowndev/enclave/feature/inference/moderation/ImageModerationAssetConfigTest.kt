package com.browntowndev.enclave.feature.inference.moderation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageModerationAssetConfigTest {
    @Test
    fun parse_readsLabelsThresholdsAndBlockedLabels() {
        val config =
            ImageModerationAssetConfig.parse(
                """
                {
                  "model_path": "model_quantized.onnx",
                  "input_size": 380,
                  "labels": ["safe", "hentai", "porn", "sexy", "drawing"],
                  "thresholds": {
                    "porn": 0.5,
                    "sexy": 0.55,
                    "hentai": 0.5
                  },
                  "blocked_labels": ["porn", "hentai", "sexy"],
                  "channel_mean": [0.485, 0.456, 0.406],
                  "channel_std": [0.229, 0.224, 0.225],
                  "input_name": "input",
                  "output_name": "output"
                }
                """.trimIndent(),
            )

        assertEquals("model_quantized.onnx", config.modelPath)
        assertEquals(380, config.inputSize)
        assertEquals(listOf("safe", "hentai", "porn", "sexy", "drawing"), config.labels)
        assertEquals(0.5f, config.thresholds["porn"])
        assertEquals(listOf("porn", "hentai", "sexy"), config.blockedLabels)
        assertEquals(listOf(0.485f, 0.456f, 0.406f), config.channelMean)
        assertEquals(listOf(0.229f, 0.224f, 0.225f), config.channelStd)
        assertEquals("input", config.inputName)
        assertEquals("output", config.outputName)
        assertEquals(ImageModerationResizeMode.CENTER_CROP, config.resizeMode)
    }

    @Test
    fun parse_readsStretchResizeMode() {
        val config =
            ImageModerationAssetConfig.parse(
                """
                {
                  "model_path": "model_quantized.onnx",
                  "labels": ["normal", "nsfw"],
                  "resize_mode": "stretch"
                }
                """.trimIndent(),
            )

        assertEquals(ImageModerationResizeMode.STRETCH, config.resizeMode)
    }

    @Test
    fun parse_appliesImageNetDefaultsWhenNormalizationOmitted() {
        val config =
            ImageModerationAssetConfig.parse(
                """
                {
                  "model_path": "model.onnx",
                  "labels": ["neutral", "porn"]
                }
                """.trimIndent(),
            )

        assertEquals(224, config.inputSize)
        assertEquals(emptyList<String>(), config.blockedLabels)
        assertTrue(config.channelMean.isNotEmpty())
        assertTrue(config.channelStd.isNotEmpty())
    }
}
