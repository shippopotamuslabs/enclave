package com.browntowndev.pocketcrew.domain.usecase.chat

import javax.inject.Inject

/**
 * Default implementation of SafetyProbe that performs basic content validation.
 */
class SafetyProbeImpl @Inject constructor() : SafetyProbe {
    override fun isSafe(content: String): Boolean {
        // Basic content validation - implement actual safety checks as needed
        return content.isNotBlank()
    }
}
