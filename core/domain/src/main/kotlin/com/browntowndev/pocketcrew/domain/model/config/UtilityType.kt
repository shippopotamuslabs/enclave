package com.browntowndev.pocketcrew.domain.model.config

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
        }

    companion object {
        fun fromApiValue(value: String?): UtilityType? =
            entries.firstOrNull { type ->
                type.apiValue.equals(value, ignoreCase = true) ||
                    type.name.equals(value, ignoreCase = true)
            }
    }
}
