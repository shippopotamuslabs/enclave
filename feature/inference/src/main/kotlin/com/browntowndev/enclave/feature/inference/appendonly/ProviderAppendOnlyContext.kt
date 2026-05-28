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
    var clearCount: Int = 0
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

    fun syncMessages(normalized: List<ProviderNormalizedMessage>) {
        if (
            lastSyncCount > 0 &&
            lastSyncCount <= normalized.size &&
            ProviderMessageDigest.compute(normalized.take(lastSyncCount)) != syncedDigest
        ) {
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

    fun hasFrozenPreRetrievalSidecar(): Boolean = frozenSidecarSnapshots.any { PreRetrievalContextSidecar.isSidecarText(it.text) }

    fun clearOnRustCompactionBoundary() {
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
    }

    fun logMessages(): List<ProviderNormalizedMessage> = log.toMessages()

    fun invalidate() {
        stablePrefix = null
        clearLogState(reason = "session_invalidate")
        frozenSidecarSnapshots.clear()
    }

    private fun clearLogState(reason: String) {
        val hadCommitted = log.size > 0 || lastSyncCount > 0
        log.clear()
        frozenSidecarSnapshots.clear()
        lastSyncCount = 0
        syncedDigest = 0u
        clearCount += 1
        if (hadCommitted && reason != "session_invalidate") {
            onLogCleared?.invoke(reason)
        }
    }
}
