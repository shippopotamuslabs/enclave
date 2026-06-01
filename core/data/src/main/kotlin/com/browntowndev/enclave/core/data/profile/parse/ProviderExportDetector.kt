package com.browntowndev.enclave.core.data.profile.parse

import com.browntowndev.enclave.domain.port.profile.ConversationExportParser

/**
 * Picks the right [ConversationExportParser] for a given zip entry.
 *
 * Each parser's [canHandle] is asked in priority order. [GenericFallbackParser] is last.
 */
class ProviderExportDetector(
    private val parsers: List<ConversationExportParser>,
    private val fallback: GenericFallbackParser,
) {
    fun detect(entryName: String, contentPreview: String): ConversationExportParser? {
        val specific = parsers.firstOrNull { it.canHandle(entryName, contentPreview) }
        if (specific != null) return specific
        if (fallback.canHandle(entryName, contentPreview)) return fallback
        return null
    }

    companion object {
        /** Content preview length to pass into [canHandle]. */
        const val PREVIEW_CHARS = 512
    }
}
