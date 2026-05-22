package com.browntowndev.enclave.domain.model.chat

/**
 * Represents the current state of a message in the generation pipeline.
 *
 * Note: Database only stores PROCESSING and COMPLETE. THINKING and GENERATING
 * are used by the UI layer for IndicatorState via MessageGenerationState flow.
 */
enum class MessageState(
    val apiValue: String,
) {
    /** Engine is being loaded into memory */
    ENGINE_LOADING("engine_loading"),

    /** Message is being processed - streaming generation in progress */
    PROCESSING("processing"),

    /** Model is outputting thinking content (hidden) */
    THINKING("thinking"),

    /** Model is outputting visible text */
    GENERATING("generating"),

    /** Generation complete - final response stored */
    COMPLETE("complete"),
    ;

    companion object {
        /**
         * Converts a string value to a MessageState enum.
         * Defaults to COMPLETE if the value doesn't match any state.
         */
        fun fromApiValue(value: String): MessageState =
            entries.firstOrNull {
                it.apiValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            } ?: COMPLETE
    }
}
