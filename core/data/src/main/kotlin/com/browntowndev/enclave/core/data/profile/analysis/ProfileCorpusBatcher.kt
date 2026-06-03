package com.browntowndev.enclave.core.data.profile.analysis

/**
 * Groups items into batches where the cumulative "size" of a batch does not exceed [budget].
 *
 * Items are added greedily in order. If a single item's size exceeds [budget] it is placed
 * in its own batch (never dropped). Empty input returns an empty list.
 *
 * @param T the item type
 * @param budget maximum total size per batch (inclusive)
 * @param sizeOf returns the size of a single item; must be ≥ 0
 */
internal object ProfileCorpusBatcher {
    fun <T> pack(
        items: List<T>,
        budget: Int,
        sizeOf: (T) -> Int,
    ): List<List<T>> {
        if (items.isEmpty()) return emptyList()

        val batches = mutableListOf<List<T>>()
        val current = mutableListOf<T>()
        var currentSize = 0

        for (item in items) {
            val size = sizeOf(item).coerceAtLeast(0)
            val wouldExceed = current.isNotEmpty() && (currentSize + size > budget)
            if (wouldExceed) {
                batches.add(current.toList())
                current.clear()
                currentSize = 0
            }
            current.add(item)
            currentSize += size
        }

        if (current.isNotEmpty()) batches.add(current.toList())
        return batches
    }
}
