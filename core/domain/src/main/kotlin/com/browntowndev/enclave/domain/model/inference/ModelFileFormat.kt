package com.browntowndev.enclave.domain.model.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Enum representing the file format of the model.
 */
@Serializable
enum class ModelFileFormat(
    val extension: String,
) {
    @SerialName("LITERTLM")
    LITERTLM(".litertlm"),

    @SerialName("LITERT")
    LITERT_ALIAS(".litertlm"),

    @SerialName("GGUF")
    GGUF(".gguf"),

    @SerialName("BIN")
    BIN(".bin"),

    @SerialName("ONNX")
    ONNX(".onnx"),

    @SerialName("TXT")
    TXT(".txt"),
}
