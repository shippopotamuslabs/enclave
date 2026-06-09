package com.browntowndev.enclave.feature.inference.titlegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TitleGenAssetConfigTest {
    @Test
    fun parsesT5GenerationConfig() {
        val config =
            TitleGenAssetConfig.parse(
                """
                {
                  "model_type": "t5_encoder_decoder",
                  "max_input_length": 384,
                  "max_output_tokens": 16,
                  "beam_width": 4,
                  "decoder_start_token_id": 0,
                  "eos_token_id": 1,
                  "pad_token_id": 0,
                  "task_prefix": "short title: "
                }
                """.trimIndent(),
            )

        assertEquals(384, config.maxInputLength)
        assertEquals(16, config.maxOutputTokens)
        assertEquals(4, config.beamWidth)
        assertEquals(0, config.decoderStartTokenId)
        assertEquals(1, config.eosTokenId)
        assertEquals("short title: ", config.taskPrefix)
    }

    @Test
    fun bundledAssetConfigLoadsFromApkAssets() {
        val json =
            javaClass.classLoader
                ?.getResourceAsStream("models/chat_title_generator/model_config.json")
                ?.bufferedReader()
                ?.use { it.readText() }
        if (json != null) {
            val config = TitleGenAssetConfig.parse(json)
            assertTrue(config.taskPrefix.isNotBlank())
        }
    }
}