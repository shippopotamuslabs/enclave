package com.browntowndev.enclave.feature.inference.appendonly

import com.browntowndev.enclave.feature.inference.openai.PreRetrievalContextSidecar

/**
 * Session-scoped append-only provider context (Pi invariant).
 */
internal class ProviderAppendOnlyContext(
    private val onLogCleared: ((reason: String) -> Unit)? = null,
) {
    private var stablePrefix: StablePrefixSnapshot? = null
    private val log = AppendOnlyMessageLog()
    private val frozenSidecarSnapshots = mutableListOf<FrozenSidecarSnapshot>()
    private var lastSyncCount = 0
    private var syncedDigest: UInt = 0u
    private var pendingTurnBoundaryReanchor = false
    private var lastShippedSidecarText: String? = null
    private var lastSeenRevision: Long? = null
    var clearCount: Int = 0
        private set

    /** Reason for the most recent [clearLogState] (excluding [invalidate]). */
    var lastClearReason: String? = null
        private set

    fun ensureStablePrefix(
        systemPrompt: String,
        toolsFingerprint: String,
    ): StablePrefixSnapshot {
        val current = stablePrefix
        if (
            current != null &&
            current.systemPrompt == systemPrompt &&
            current.toolsFingerprint == toolsFingerprint
        ) {
            return current
        }
        val nextVersion = (current?.version ?: 0) + 1
        val snapshot =
            StablePrefixSnapshot(
                systemPrompt = systemPrompt,
                toolsFingerprint = toolsFingerprint,
                version = nextVersion,
            )
        stablePrefix = snapshot
        return snapshot
    }

    /**
     * Re-anchor digest cursor at a user-turn boundary without clearing frozen sidecars or the log.
     * The next [syncMessages] skips prefix [digest_mismatch] detection (committed rehydrate drift).
     */
    fun markTurnBoundaryForReanchor() {
        pendingTurnBoundaryReanchor = true
    }

    fun syncMessages(
        normalized: List<ProviderNormalizedMessage>,
        transcriptRevision: Long? = null,
    ) {
        // Proactively clear on Rust compaction boundary before comparing digests.
        // The async CompactionBoundaryApplied event may not have been processed yet
        // when completeSingleTurn delivers the compacted messages, so we detect
        // the revision bump here to avoid a false-positive digest_mismatch that
        // would wipe all frozen sidecars and destroy the prompt-cache prefix.
        if (transcriptRevision != null) {
            val last = lastSeenRevision
            if (last != null && transcriptRevision > last) {
                clearLogState(reason = "rust_compaction_boundary")
            }
            lastSeenRevision = transcriptRevision
        }
        val skipDigestMismatchOnReanchor = pendingTurnBoundaryReanchor
        if (pendingTurnBoundaryReanchor) {
            pendingTurnBoundaryReanchor = false
        }
        if (shouldClearOnDigestMismatch(skipDigestMismatchOnReanchor, normalized)) {
            clearLogState(reason = "digest_mismatch")
        }
        if (normalized.size < lastSyncCount) {
            clearLogState(reason = "compaction_shorter_array")
        }
        val newMessages = normalized.drop(lastSyncCount)
        log.extend(newMessages)
        lastSyncCount = normalized.size
        syncedDigest = ProviderMessageDigest.compute(normalized)
    }

    fun committedMessageCount(): Int = log.size

    fun committedVolatileSidecarTexts(): List<String> = frozenSidecarSnapshots.map { it.text }

    fun frozenSidecarsForWireReplay(): List<FrozenSidecarSnapshot> = frozenSidecarSnapshots.toList()

    fun resolveContextSidecarsForWire(built: String?): String? {
        val trimmed = built?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed == lastShippedSidecarText) return null
        lastShippedSidecarText = trimmed
        return trimmed
    }

    fun hasFrozenPreRetrievalSidecar(): Boolean = frozenSidecarSnapshots.any { PreRetrievalContextSidecar.isSidecarText(it.text) }

    fun clearOnRustCompactionBoundary(revisionAfter: Long? = null) {
        if (revisionAfter != null) lastSeenRevision = revisionAfter
        clearLogState(reason = "rust_compaction_boundary")
    }

    fun appendVolatileSidecar(
        sidecarText: String,
        delivery: VolatileContextDelivery,
    ) {
        if (sidecarText.isBlank()) return
        val mappedInsertIndex =
            when (delivery) {
                is VolatileContextDelivery.OpenAiTailBeforeLastSessionMessage ->
                    delivery.sessionLastIndex.coerceAtLeast(0)
                else -> 0
            }
        frozenSidecarSnapshots.add(
            FrozenSidecarSnapshot(
                text = sidecarText,
                mappedInsertIndex = mappedInsertIndex,
            ),
        )
    }

    fun endTurnFreezeSidecar(
        sidecarText: String,
        mappedInsertIndex: Int,
    ) {
        if (sidecarText.isBlank()) return
        frozenSidecarSnapshots.add(
            FrozenSidecarSnapshot(
                text = sidecarText,
                mappedInsertIndex = mappedInsertIndex,
            ),
        )
        lastShippedSidecarText = sidecarText.trim()
    }

    fun logMessages(): List<ProviderNormalizedMessage> = log.toMessages()

    fun invalidate() {
        stablePrefix = null
        clearLogState(reason = "session_invalidate")
        frozenSidecarSnapshots.clear()
        lastSeenRevision = null
    }

    private fun shouldClearOnDigestMismatch(
        skipDigestMismatchOnReanchor: Boolean,
        normalized: List<ProviderNormalizedMessage>,
    ): Boolean {
        if (skipDigestMismatchOnReanchor || lastSyncCount <= 0) {
            return false
        }
        if (lastSyncCount > normalized.size) {
            return false
        }
        val prefixDigest = ProviderMessageDigest.compute(normalized.take(lastSyncCount))
        return prefixDigest != syncedDigest
    }

    private fun clearLogState(reason: String) {
        val hadCommitted = log.size > 0 || lastSyncCount > 0
        log.clear()
        frozenSidecarSnapshots.clear()
        lastShippedSidecarText = null
        lastSyncCount = 0
        syncedDigest = 0u
        clearCount += 1
        if (reason != "session_invalidate") {
            lastClearReason = reason
        }
        if (hadCommitted && reason != "session_invalidate") {
            onLogCleared?.invoke(reason)
        }
    }
}
