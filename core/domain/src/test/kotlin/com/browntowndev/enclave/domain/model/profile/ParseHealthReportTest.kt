package com.browntowndev.enclave.domain.model.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParseHealthReportTest {
    @Test
    fun `PP-50 isHealthy true when both ratios above threshold`() {
        val report =
            ParseHealthReport(
                provider = ConversationProvider.OPENAI,
                totalEntries = 10,
                extractedConversations = 9,
                totalMessages = 20,
                messagesWithText = 19,
                skippedMalformed = 1,
            )
        assertTrue(report.isHealthy)
        assertEquals(0.9f, report.conversationExtractionRatio)
        assertEquals(0.95f, report.messageTextRatio)
    }

    @Test
    fun `PP-51 isHealthy false when conversation extraction ratio below threshold`() {
        val report =
            ParseHealthReport(
                provider = ConversationProvider.OPENAI,
                totalEntries = 10,
                extractedConversations = 2,
                totalMessages = 4,
                messagesWithText = 4,
                skippedMalformed = 8,
            )
        assertFalse(report.isHealthy)
    }

    @Test
    fun `PP-52 isHealthy false when message text ratio below threshold`() {
        val report =
            ParseHealthReport(
                provider = ConversationProvider.ANTHROPIC,
                totalEntries = 5,
                extractedConversations = 5,
                totalMessages = 20,
                messagesWithText = 3,
                skippedMalformed = 0,
            )
        assertFalse(report.isHealthy)
    }

    @Test
    fun `PP-53 empty corpus is considered healthy`() {
        val report =
            ParseHealthReport(
                provider = ConversationProvider.OPENAI,
                totalEntries = 0,
                extractedConversations = 0,
                totalMessages = 0,
                messagesWithText = 0,
                skippedMalformed = 0,
            )
        assertTrue(report.isHealthy)
        assertEquals(1f, report.conversationExtractionRatio)
        assertEquals(1f, report.messageTextRatio)
    }
}
