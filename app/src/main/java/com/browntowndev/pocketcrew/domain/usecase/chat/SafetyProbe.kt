package com.browntowndev.pocketcrew.domain.usecase.chat

/**
 * Safety probe interface for content validation.
 * This is a stub - implement actual safety checks as needed.
 */
interface SafetyProbe {
    /**
     * Check if the content is safe to display.
     * @param content The content to check
     * @return true if safe, false otherwise
     */
    fun isSafe(content: String): Boolean = true
}
