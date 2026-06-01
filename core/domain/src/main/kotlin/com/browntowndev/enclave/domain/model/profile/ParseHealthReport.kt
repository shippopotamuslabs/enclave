package com.browntowndev.enclave.domain.model.profile

/**
 * Summary of a single export parsing pass. Used to detect schema drift before spending tokens.
 *
 * A [conversationExtractionRatio] or [messageTextRatio] below [HEALTHY_THRESHOLD] means the
 * export format may have changed — surface a warning to the user rather than silently producing
 * a thin corpus.
 */
data class ParseHealthReport(
    val provider: ConversationProvider,
    val totalEntries: Int,
    val extractedConversations: Int,
    val totalMessages: Int,
    val messagesWithText: Int,
    val skippedMalformed: Int,
) {
    val conversationExtractionRatio: Float
        get() = if (totalEntries == 0) 1f else extractedConversations.toFloat() / totalEntries

    val messageTextRatio: Float
        get() = if (totalMessages == 0) 1f else messagesWithText.toFloat() / totalMessages

    val isHealthy: Boolean
        get() = conversationExtractionRatio >= HEALTHY_THRESHOLD && messageTextRatio >= HEALTHY_THRESHOLD

    companion object {
        const val HEALTHY_THRESHOLD = 0.5f
    }
}
