package com.browntowndev.pocketcrew.core.data.remote.firestore

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingDocumentRetryQueueTest {
    private val drops = mutableListOf<Pair<String, Throwable>>()
    private fun newQueue(maxAttempts: Int = 3) =
        PendingDocumentRetryQueue(maxAttempts = maxAttempts) { key, cause ->
            drops += key to cause
        }

    @Test
    fun `drain runs queued action and removes on success`() =
        runTest {
            val queue = newQueue()
            var ran = 0
            queue.enqueue("a") { ran++ }

            queue.drain()

            assertEquals(1, ran)
            assertEquals(0, queue.size())
        }

    @Test
    fun `entry that throws FK stays queued for next drain`() =
        runTest {
            val queue = newQueue()
            var attempts = 0
            queue.enqueue("a") {
                attempts++
                if (attempts < 2) throw SQLiteConstraintException("FK")
            }

            queue.drain()
            assertEquals(1, attempts)
            assertEquals(1, queue.size())

            queue.drain()
            assertEquals(2, attempts)
            assertEquals(0, queue.size())
        }

    @Test
    fun `dependent children resolve in single drain when parent enqueued first`() =
        runTest {
            val queue = newQueue()
            val applied = mutableListOf<String>()
            var parentApplied = false
            // Child fails FK until parent applies.
            queue.enqueue("child") {
                if (!parentApplied) throw SQLiteConstraintException("FK")
                applied += "child"
            }
            queue.enqueue("parent") {
                parentApplied = true
                applied += "parent"
            }

            queue.drain()

            assertEquals(listOf("parent", "child"), applied)
            assertEquals(0, queue.size())
        }

    @Test
    fun `entry dropped after maxAttempts of repeated FK failure`() =
        runTest {
            val queue = newQueue(maxAttempts = 3)
            queue.enqueue("a") { throw SQLiteConstraintException("FK") }

            // Each drain represents one retry pass (one new parent arrival). Drive the queue
            // until the cap is reached.
            repeat(3) { queue.drain() }

            assertEquals(0, queue.size())
            assertEquals(1, drops.size)
            assertEquals("a", drops[0].first)
            assertTrue(drops[0].second is SQLiteConstraintException)
        }

    @Test
    fun `non-FK exception drops entry immediately via onDrop`() =
        runTest {
            val queue = newQueue()
            val boom = IllegalStateException("bad")
            queue.enqueue("a") { throw boom }

            queue.drain()

            assertEquals(0, queue.size())
            assertEquals(1, drops.size)
            assertEquals(boom, drops[0].second)
        }

    @Test
    fun `enqueue with duplicate key replaces previous entry`() =
        runTest {
            val queue = newQueue()
            var ranV1 = 0
            var ranV2 = 0
            queue.enqueue("a") { ranV1++ }
            queue.enqueue("a") { ranV2++ }

            queue.drain()

            assertEquals(0, ranV1)
            assertEquals(1, ranV2)
        }
}
