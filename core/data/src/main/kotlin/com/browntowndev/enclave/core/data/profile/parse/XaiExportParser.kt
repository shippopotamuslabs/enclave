package com.browntowndev.enclave.core.data.profile.parse

import com.browntowndev.enclave.domain.model.profile.ConversationProvider
import com.browntowndev.enclave.domain.model.profile.ConversationRecord
import com.browntowndev.enclave.domain.model.profile.ParseHealthReport
import com.browntowndev.enclave.domain.port.profile.ConversationExportParser

/** Stub — xAI Grok export parsing not yet implemented. */
class XaiExportParser
    @javax.inject.Inject
    constructor() : ConversationExportParser {
    override val provider = ConversationProvider.XAI

    override fun canHandle(entryName: String, contentPreview: String): Boolean = false

    override fun parse(entryName: String, content: String): Pair<List<ConversationRecord>, ParseHealthReport> =
        emptyList<ConversationRecord>() to ParseHealthReport(
            provider = provider,
            totalEntries = 0,
            extractedConversations = 0,
            totalMessages = 0,
            messagesWithText = 0,
            skippedMalformed = 0,
        )
}
