package com.browntowndev.enclave.domain.port.profile

import com.browntowndev.enclave.domain.model.profile.ConversationProvider
import com.browntowndev.enclave.domain.model.profile.ConversationRecord
import com.browntowndev.enclave.domain.model.profile.ParseHealthReport

interface ConversationExportParser {
    val provider: ConversationProvider

    /**
     * Parse a single entry from a zip archive. [entryName] is the zip entry filename,
     * [content] is the raw UTF-8 text of that entry.
     *
     * Returns null if this parser cannot handle the given entry.
     */
    fun canHandle(entryName: String, contentPreview: String): Boolean

    /**
     * Parse [content] into a list of [ConversationRecord]s plus a [ParseHealthReport].
     * Must never throw — malformed items are skipped and counted in the report.
     */
    fun parse(entryName: String, content: String): Pair<List<ConversationRecord>, ParseHealthReport>
}
