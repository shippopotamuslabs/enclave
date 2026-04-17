package com.browntowndev.pocketcrew.domain.util

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.ModelType

/**
 * Interface for counting tokens in a string for various models.
 */
interface TokenCounter {
    fun countTokens(text: String, modelName: String? = null): Int
}

/**
 * Implementation of [TokenCounter] using the JTokkit library.
 * Optimized for OpenAI models but provides generic fallback for others.
 */
object JTokkitTokenCounter : TokenCounter {
    private val registry: EncodingRegistry = Encodings.newDefaultEncodingRegistry()
    private val cl100kBaseEncoding: Encoding = registry.getEncoding(com.knuddels.jtokkit.api.EncodingType.CL100K_BASE)

    override fun countTokens(text: String, modelName: String?): Int {
        if (text.isEmpty()) return 0

        val encoding = if (modelName != null) {
            val modelType = ModelType.fromName(modelName).orElse(null)
            if (modelType != null) {
                registry.getEncodingForModel(modelType)
            } else {
                cl100kBaseEncoding
            }
        } else {
            cl100kBaseEncoding
        }

        return encoding.countTokens(text)
    }
}
