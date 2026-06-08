package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.inference.ToolSearchThresholds
import com.browntowndev.enclave.domain.port.inference.MonotonicToolActivationPort
import com.browntowndev.enclave.domain.port.inference.NoopMonotonicToolActivationPort

/**
 * Single append-only activation registry for one inference host session.
 *
 * - **API (RustLoop):** reset at each [seedTurn] from preparer [GenerationOptions.activeTools].
 * - **LiteRT:** seeded each [getConversation] from preparer ∪ prior in-engine discoveries.
 *
 * Provider wire names and LiteRT native tool providers both read [orderedActiveNames].
 * [applySearchActivations] persists to [MonotonicToolActivationPort] when [chatId] is set.
 */
class ToolSessionActivationRegistry(
    private val monotonicToolActivation: MonotonicToolActivationPort = NoopMonotonicToolActivationPort,
    private val onActiveNamesUpdated: (List<String>) -> Unit = {},
    private val onSearchDiscoveredNewTools: () -> Unit = {},
) {
    private val orderedNames = ArrayList<String>()

    fun orderedActiveNames(): List<String> = synchronized(orderedNames) { orderedNames.toList() }

    fun activeNameSet(): Set<String> = synchronized(orderedNames) { orderedNames.toSet() }

    /** Replaces registry with [preloadNames] (typically preparer active tools for this turn). */
    fun seedTurn(preloadNames: Collection<String>) {
        val updated = synchronized(orderedNames) {
            orderedNames.clear()
            preloadNames.forEach { addUnique(it) }
            orderedNames.toList()
        }
        onActiveNamesUpdated(updated)
    }

    /** Merges [preloadNames] with any names already in the registry (LiteRT cross-turn in engine). */
    fun seedTurnPreservingDiscovered(preloadNames: Collection<String>) {
        val updated = synchronized(orderedNames) {
            val preserved = orderedNames.toList()
            orderedNames.clear()
            (preloadNames + preserved).forEach { addUnique(it) }
            orderedNames.toList()
        }
        onActiveNamesUpdated(updated)
    }

    fun applySearchActivations(
        activatedNames: List<String>,
        chatId: ChatId?,
    ): List<String> {
        if (activatedNames.isEmpty()) return orderedActiveNames()
        var changed = false
        val persistedNames = mutableListOf<String>()
        val updated = synchronized(orderedNames) {
            activatedNames.forEach { name ->
                if (orderedNames.size >= ToolSearchThresholds.MAX_ACTIVATED_TOOLS_PER_SESSION) return@forEach
                if (addUnique(name)) {
                    changed = true
                    persistedNames += name
                }
            }
            orderedNames.toList()
        }
        chatId?.let { id ->
            if (persistedNames.isNotEmpty()) {
                monotonicToolActivation.recordActivation(id, persistedNames.toSet())
            }
        }
        if (changed) {
            onActiveNamesUpdated(updated)
            onSearchDiscoveredNewTools()
        }
        return updated
    }

    private fun addUnique(name: String): Boolean {
        if (name in orderedNames) return false
        orderedNames.add(name)
        return true
    }
}
