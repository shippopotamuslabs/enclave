package com.browntowndev.enclave.domain.model.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UtilityType(
    val apiValue: String,
) {
    @SerialName("whisper")
    WHISPER("whisper"),

    @SerialName("onnx")
    ONNX("onnx"),

    @SerialName("onnx_model")
    ONNX_MODEL("onnx_model"),

    @SerialName("onnx_vocab")
    ONNX_VOCAB("onnx_vocab"),

    @SerialName("title_generator_encoder")
    TITLE_GENERATOR_ENCODER("title_generator_encoder"),

    @SerialName("title_generator_decoder")
    TITLE_GENERATOR_DECODER("title_generator_decoder"),

    @SerialName("title_generator_tokenizer")
    TITLE_GENERATOR_TOKENIZER("title_generator_tokenizer"),

    @SerialName("flux2_vae")
    FLUX2_VAE("flux2_vae"),

    @SerialName("qwen3_text_encoder")
    QWEN3_TEXT_ENCODER("qwen3_text_encoder"),

    @SerialName("sd15_qnn_pack")
    SD15_QNN_PACK("sd15_qnn_pack"),

    @SerialName("sdxl_qnn_pack")
    SDXL_QNN_PACK("sdxl_qnn_pack"),

    @SerialName("juggernaut_xl_qnn_pack")
    JUGGERNAUT_XL_QNN_PACK("juggernaut_xl_qnn_pack"),
    ;

    /**
     * Returns the human-readable display name for download UI and notifications.
     */
    fun displayName(): String =
        when (this) {
            WHISPER -> "Speech Recognition"
            ONNX_MODEL -> "Embeddings"
            ONNX_VOCAB -> "Embedding Vocabulary"
            ONNX -> "Utility Model"
            TITLE_GENERATOR_ENCODER -> "Chat Title Model"
            TITLE_GENERATOR_DECODER -> "Chat Title Decoder"
            TITLE_GENERATOR_TOKENIZER -> "Chat Title Tokenizer"
            FLUX2_VAE -> "Flux.2 VAE"
            QWEN3_TEXT_ENCODER -> "Qwen3 Text Encoder"
            SD15_QNN_PACK -> "SD 1.5 NPU weights"
            SDXL_QNN_PACK -> "SDXL NPU weights"
            JUGGERNAUT_XL_QNN_PACK -> "Juggernaut XL NPU weights"
        }

    companion object {
        fun fromApiValue(value: String?): UtilityType? =
            entries.firstOrNull { type ->
                type.apiValue.equals(value, ignoreCase = true) ||
                    type.name.equals(value, ignoreCase = true)
            }
    }
}
