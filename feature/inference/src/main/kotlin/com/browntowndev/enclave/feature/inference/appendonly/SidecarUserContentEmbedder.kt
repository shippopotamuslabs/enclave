package com.browntowndev.enclave.feature.inference.appendonly

/**
 * Embeds UUID-stamped sidecar markers as suffix annotations on user message
 * content, and strips them for display/diagnostics.
 *
 * Wire shape per user message:
 * ```
 * {user-visible text}
 *
 * [Metadata: Sent at ...]
 *
 * [Sidecar-<uuid>: {body}]
 * ```
 * Metadata suffixes already in [baseContent] are left in place and precede
 * the sidecar blocks (INV-2).
 */
object SidecarUserContentEmbedder {
    fun embed(baseContent: String, markers: List<SidecarMarker>): String {
        if (markers.isEmpty()) return baseContent
        val builder = StringBuilder(baseContent)
        for (marker in markers) {
            builder.append("\n\n")
            builder.append(marker.wireBlock())
        }
        return builder.toString()
    }

    /**
     * Removes every `[Sidecar-<uuid>: ...]` block from [content], leaving
     * `[Metadata:]` and all other content (including user-typed `[Sidecar:`
     * literals without a UUID) intact. Blocks are matched by [SidecarMarkerRegex]
     * and extend to the closing `]` on the same nesting level.
     */
    fun stripSidecarBlocks(content: String): String {
        val result = StringBuilder(content.length)
        var cursor = 0
        var span: SidecarMarkerSpan? = findNextSidecarMarkerSpan(content, startIndex = cursor) ?: return content
        while (span != null) {
            val currentSpan = span
            val blockStart = currentSpan.blockStart
            // Strip the preceding "\n\n" that separated the block from
            // prior content, so removing the block does not leave blank lines.
            val prefixEnd = if (blockStart >= 2 && content[blockStart - 1] == '\n' && content[blockStart - 2] == '\n') {
                blockStart - 2
            } else {
                blockStart
            }
            result.append(content, cursor, prefixEnd)
            cursor = currentSpan.blockEndInclusive + 1
            span = findNextSidecarMarkerSpan(content, startIndex = cursor)
        }
        result.append(content, cursor, content.length)
        return result.toString().trimEnd()
    }
}
