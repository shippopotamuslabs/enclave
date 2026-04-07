package com.browntowndev.pocketcrew.domain.model.inference

enum class ApiReasoningEffort(val wireValue: String, val displayName: String) {
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    XHIGH("xhigh", "X-High");

    companion object {
        fun fromWireValue(value: String?): ApiReasoningEffort? =
            entries.firstOrNull { it.wireValue == value }
    }
}
