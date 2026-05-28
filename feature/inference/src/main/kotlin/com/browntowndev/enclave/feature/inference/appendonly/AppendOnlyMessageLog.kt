package com.browntowndev.enclave.feature.inference.appendonly

/**
 * Append-only message log. [replaceTail] is reserved for compaction only.
 */
internal class AppendOnlyMessageLog {
    private val entries = mutableListOf<ProviderNormalizedMessage>()

    val size: Int
        get() = entries.size

    fun append(message: ProviderNormalizedMessage) {
        entries.add(message)
    }

    fun extend(messages: List<ProviderNormalizedMessage>) {
        entries.addAll(messages)
    }

    fun replaceTail(replacement: ProviderNormalizedMessage) {
        check(entries.isNotEmpty()) { "replaceTail requires a non-empty log" }
        entries[entries.lastIndex] = replacement
    }

    fun toMessages(): List<ProviderNormalizedMessage> = entries.toList()

    fun clear() {
        entries.clear()
    }
}
