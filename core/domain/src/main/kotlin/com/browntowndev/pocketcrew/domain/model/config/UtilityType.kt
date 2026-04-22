package com.browntowndev.pocketcrew.domain.model.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UtilityType(val apiValue: String) {
    @SerialName("whisper")
    WHISPER("whisper"),

    @SerialName("onnx")
    ONNX("onnx");

    companion object {
        fun fromApiValue(value: String?): UtilityType? {
            return entries.firstOrNull { type ->
                type.apiValue.equals(value, ignoreCase = true) ||
                    type.name.equals(value, ignoreCase = true)
            }
        }
    }
}
