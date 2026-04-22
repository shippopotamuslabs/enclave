package com.browntowndev.pocketcrew.domain.model.inference

import kotlinx.serialization.Serializable

/**
 * Enum representing the file format of the model.
 */
@Serializable
enum class ModelFileFormat(val extension: String) {
    LITERTLM(".litertlm"),
    GGUF(".gguf"),
    BIN(".bin"),
    ONNX(".onnx"),
}
