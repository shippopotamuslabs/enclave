package com.browntowndev.enclave.feature.inference.compaction

import com.browntowndev.enclave.feature.inference.titlegen.TitleGenAssetConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompactionSummarizerAssetConfigTest {
    @Test
    fun parsesCompactionGenerationConfig() {
        val config =
            TitleGenAssetConfig.parse(
                """
                {
                  "eos_token_id": 7,
                  "instruction": "Summarize the conversation history below.\n\n",
                  "max_input_chars": 28000,
                  "max_output_tokens": 768
                }
                """.trimIndent(),
            )

        assertFalse(config.instruction.isBlank())
        assertTrue(config.maxInputChars >= 20_000)
        assertTrue(config.maxOutputTokens >= 512)
    }

    @Test
    fun bundledAssetConfigLoadsFromTestAssets() {
        val json =
            javaClass.classLoader
                ?.getResourceAsStream("models/compaction_summarizer/model_config.json")
                ?.bufferedReader()
                ?.use { it.readText() }
        if (json != null) {
            val config = TitleGenAssetConfig.parse(json)
            assertFalse(config.instruction.isBlank())
            assertTrue(config.maxOutputTokens > 24)
        }
    }
}