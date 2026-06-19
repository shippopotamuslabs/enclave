package com.browntowndev.enclave.feature.inference.appendonly

import java.util.UUID

/**
 * UUID-stamped sidecar marker for wire embedding. The [markerId] is a UUID
 * generated once at live-ship time and frozen for the life of the snapshot
 * so frozen replays render byte-identical suffix bytes (INV-1).
 */
data class SidecarMarker(val markerId: String, val body: String)

/**
 * Result of resolving a live sidecar for wire: the trimmed sidecar text and
 * the markerId allocated for this ship, carried into the freeze call so the
 * live→frozen transition preserves the same marker (D7).
 */
data class ResolvedSidecar(val text: String, val markerId: String)

object SidecarMarkerFactory {
    fun newMarker(body: String): SidecarMarker =
        SidecarMarker(markerId = UUID.randomUUID().toString(), body = body)
}

fun SidecarMarker.wireBlock(): String = "[Sidecar-$markerId: $body]"

object SidecarMarkerRegex {
    /**
     * Matches the opening of a UUID-stamped sidecar block: `[Sidecar-<uuid>:`.
     * Used for stripping and diagnostics span detection. The UUID constraint
     * prevents false matches on user-typed `[Sidecar:...]` literals (D7).
     */
    val SPAN: Regex = Regex("\\[Sidecar-($UUID_PATTERN):")
}

internal data class SidecarMarkerSpan(
    val markerId: String,
    val blockStart: Int,
    val blockEndInclusive: Int,
)

internal fun findNextSidecarMarkerSpan(
    content: String,
    startIndex: Int,
): SidecarMarkerSpan? {
    val match = SidecarMarkerRegex.SPAN.find(content, startIndex) ?: return null
    val blockStart = match.range.first
    return SidecarMarkerSpan(
        markerId = match.groupValues[UUID_MATCH_GROUP],
        blockStart = blockStart,
        blockEndInclusive = findSidecarBlockEnd(content, blockStart),
    )
}

/**
 * Finds the index of the `]` that closes the sidecar block starting at [openIndex].
 * Tracks bracket nesting so JSON arrays or nested marker-like text stay inside the
 * enclosing sidecar block.
 */
internal fun findSidecarBlockEnd(content: String, openIndex: Int): Int {
    var depth = 0
    var index = openIndex
    while (index < content.length) {
        when (content[index]) {
            '[' -> depth++
            ']' -> {
                depth--
                if (depth == 0) return index
            }
        }
        index++
    }
    return content.length - 1
}

private const val UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
    "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
private const val UUID_MATCH_GROUP = 1
