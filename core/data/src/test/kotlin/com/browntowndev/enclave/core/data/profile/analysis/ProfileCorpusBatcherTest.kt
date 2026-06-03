package com.browntowndev.enclave.core.data.profile.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileCorpusBatcherTest {
    private fun pack(
        items: List<String>,
        budget: Int,
    ): List<List<String>> = ProfileCorpusBatcher.pack(items, budget) { it.length }

    @Test
    fun `PB-100 empty input returns empty list`() {
        assertTrue(pack(emptyList(), 100).isEmpty())
    }

    @Test
    fun `PB-101 all items fit in one batch when total size is within budget`() {
        val items = listOf("ab", "cd", "ef") // 2+2+2=6 ≤ budget 10
        val batches = pack(items, 10)
        assertEquals(1, batches.size)
        assertEquals(items, batches[0])
    }

    @Test
    fun `PB-102 items split into multiple batches when cumulative size exceeds budget`() {
        // Each item is 3 chars; budget=4 → first item fits (3 ≤ 4), second would push to 6 > 4.
        val items = listOf("aaa", "bbb", "ccc")
        val batches = pack(items, 4)
        assertEquals(3, batches.size)
        assertEquals(listOf("aaa"), batches[0])
        assertEquals(listOf("bbb"), batches[1])
        assertEquals(listOf("ccc"), batches[2])
    }

    @Test
    fun `PB-103 single oversized item goes into its own batch`() {
        // Item is 20 chars but budget is only 5 — must still be included.
        val items = listOf("a".repeat(20))
        val batches = pack(items, 5)
        assertEquals(1, batches.size)
        assertEquals(items, batches[0])
    }

    @Test
    fun `PB-104 oversized item after normal items starts a new batch`() {
        val items = listOf("ab", "cd", "a".repeat(20), "ef")
        val batches = pack(items, 5)
        // "ab"+"cd"=4 ≤ 5 → batch 1; "aaa..." oversized → batch 2; "ef" → batch 3
        assertEquals(3, batches.size)
        assertEquals(listOf("ab", "cd"), batches[0])
        assertEquals(listOf("a".repeat(20)), batches[1])
        assertEquals(listOf("ef"), batches[2])
    }

    @Test
    fun `PB-105 multiple items that exactly fill budget end up in same batch`() {
        // 3+3=6 == budget 6 → fits; adding a third (3 chars) would exceed → new batch.
        val items = listOf("aaa", "bbb", "ccc")
        val batches = pack(items, 6)
        assertEquals(2, batches.size)
        assertEquals(listOf("aaa", "bbb"), batches[0])
        assertEquals(listOf("ccc"), batches[1])
    }

    @Test
    fun `PB-106 items with zero budget still include first item in each batch`() {
        // budget=0: every item starts a new batch except first (empty current guard).
        val items = listOf("x", "y", "z")
        val batches = pack(items, 0)
        assertEquals(3, batches.size)
    }

    @Test
    fun `PB-107 single item within budget produces one batch`() {
        val batches = pack(listOf("hello"), 100)
        assertEquals(1, batches.size)
        assertEquals(listOf("hello"), batches[0])
    }
}
