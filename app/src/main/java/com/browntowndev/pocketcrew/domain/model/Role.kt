package com.browntowndev.pocketcrew.domain.model

/**
 * Represents the role of a message sender in a chat conversation.
 * Using an enum allows easy addition of new roles (e.g., SYSTEM) in the future.
 */
enum class Role(val apiValue: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system");

    companion object {
        /**
         * Converts a string value to a Role enum.
         * Defaults to USER if the value doesn't match any role.
         */
        fun fromApiValue(value: String): Role =
            entries.firstOrNull { it.apiValue == value } ?: USER
    }
}
