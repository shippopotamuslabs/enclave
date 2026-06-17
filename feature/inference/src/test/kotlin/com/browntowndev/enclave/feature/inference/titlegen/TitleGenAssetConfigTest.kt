package com.browntowndev.enclave.feature.inference.titlegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TitleGenAssetConfigTest {
    @Test
    fun parsesLfm2GenerationConfig() {
        val config =
            TitleGenAssetConfig.parse(
                """
                {
                  "eos_token_id": 7,
                  "instruction": "Write a short title.\n\nMessage: ",
                  "max_input_chars": 1500,
                  "max_output_tokens": 24
                }
                """.trimIndent(),
            )

        assertEquals(7, config.eosTokenId)
        assertEquals("Write a short title.\n\nMessage: ", config.instruction)
        assertEquals(1500, config.maxInputChars)
        assertEquals(24, config.maxOutputTokens)
    }

    @Test
    fun appliesDefaultsForMissingFields() {
        val config = TitleGenAssetConfig.parse("{}")
        assertEquals(7, config.eosTokenId)
        assertEquals(1500, config.maxInputChars)
        assertEquals(24, config.maxOutputTokens)
        assertEquals("", config.instruction)
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
            assertFalse(config.instruction.isBlank())
            assertTrue(config.maxInputChars > 0)
            assertTrue(config.maxOutputTokens > 0)
        }
    }
}
