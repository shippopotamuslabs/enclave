package com.browntowndev.enclave.feature.inference.titlegen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Generation parameters for the on-device chat-title generator (LFM2 decoder-only).
 *
 * Bundled in the APK (tiny, committed) as `model_config.json`; the large graph/weights/
 * tokenizer files are downloaded on demand and resolved via the utility-model registry, so
 * only these static generation parameters live here.
 */
internal data class TitleGenAssetConfig(
    val instruction: String,
    val maxInputChars: Int,
    val maxOutputTokens: Int,
    val eosTokenId: Int,
) {
    companion object {
        fun parse(configJson: String): TitleGenAssetConfig {
            val root = Json.parseToJsonElement(configJson).jsonObject
            fun int(
                key: String,
                default: Int,
            ): Int = root[key]?.jsonPrimitive?.intOrNull ?: default
            return TitleGenAssetConfig(
                instruction = root["instruction"]?.jsonPrimitive?.content ?: "",
                maxInputChars = int("max_input_chars", DEFAULT_MAX_INPUT_CHARS),
                maxOutputTokens = int("max_output_tokens", DEFAULT_MAX_OUTPUT_TOKENS),
                eosTokenId = int("eos_token_id", DEFAULT_EOS_TOKEN_ID),
            )
        }

        private const val DEFAULT_MAX_INPUT_CHARS = 1500
        private const val DEFAULT_MAX_OUTPUT_TOKENS = 24
        private const val DEFAULT_EOS_TOKEN_ID = 7
    }
}
