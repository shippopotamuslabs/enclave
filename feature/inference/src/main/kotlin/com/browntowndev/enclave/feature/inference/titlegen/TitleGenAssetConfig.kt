package com.browntowndev.enclave.feature.inference.titlegen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Generation parameters for the on-device chat-title generator (encoder-decoder T5).
 *
 * Bundled in the APK (tiny, committed) as `model_config.json`; the large encoder/decoder/
 * tokenizer files are downloaded on demand and resolved via the utility-model registry, so
 * only these static decode parameters live here.
 */
internal data class TitleGenAssetConfig(
    val maxInputLength: Int,
    val maxOutputTokens: Int,
    val beamWidth: Int,
    val decoderStartTokenId: Int,
    val eosTokenId: Int,
    val padTokenId: Int,
    val taskPrefix: String,
) {
    companion object {
        fun parse(configJson: String): TitleGenAssetConfig {
            val root = Json.parseToJsonElement(configJson).jsonObject
            fun int(
                key: String,
                default: Int,
            ): Int = root[key]?.jsonPrimitive?.intOrNull ?: default
            return TitleGenAssetConfig(
                maxInputLength = int("max_input_length", DEFAULT_MAX_INPUT_LENGTH),
                maxOutputTokens = int("max_output_tokens", DEFAULT_MAX_OUTPUT_TOKENS),
                beamWidth = int("beam_width", DEFAULT_BEAM_WIDTH),
                decoderStartTokenId = int("decoder_start_token_id", 0),
                eosTokenId = int("eos_token_id", 1),
                padTokenId = int("pad_token_id", 0),
                taskPrefix = root["task_prefix"]?.jsonPrimitive?.content ?: "",
            )
        }

        private const val DEFAULT_MAX_INPUT_LENGTH = 384
        private const val DEFAULT_MAX_OUTPUT_TOKENS = 24
        private const val DEFAULT_BEAM_WIDTH = 4
    }
}
