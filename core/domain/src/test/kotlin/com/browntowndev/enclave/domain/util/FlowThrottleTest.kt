package com.browntowndev.enclave.domain.util

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowThrottleTest {
    // ---------------------------------------------------------------------------
    // throttleLatest — basic emission semantics
    // ---------------------------------------------------------------------------

    @Test
    fun `throttleLatest emits a single value immediately`() =
        runTest {
            val result = mutableListOf<Int>()

            flow { emit(42) }
                .throttleLatest(1_000L)
                .collect { result.add(it) }

            assertEquals(listOf(42), result)
        }

    @Test
    fun `throttleLatest emits first value of rapid burst immediately`() =
        runTest {
            val result = mutableListOf<Int>()

            // All three values arrive before any throttle window expires.
            // Value 1 is the first so it is delivered to the for-loop's waiting receiver directly
            // (CONFLATED direct handoff). Values 2 and 3 arrive while the loop is delayed;
            // only 3 is kept.
            flow {
                emit(1)
                kotlinx.coroutines.delay(5)
                emit(2)
                kotlinx.coroutines.delay(5)
                emit(3)
            }.throttleLatest(16L)
                .collect { result.add(it) }

            assertEquals(1, result.first(), "First token must be emitted immediately")
            assertEquals(3, result.last(), "Last token in window must be emitted at period boundary")
        }

    @Test
    fun `throttleLatest keeps only the most recent value within each window`() =
        runTest {
            val result = mutableListOf<Int>()

            flow {
                emit(1) // t=0 → emitted immediately
                kotlinx.coroutines.delay(5)
                emit(2) // t=5  \
                kotlinx.coroutines.delay(5)
                emit(3) // t=10  > all within 16ms window, only 3 kept
                // upstream ends at t=10, well within first window
            }.throttleLatest(16L)
                .collect { result.add(it) }

            assertEquals(listOf(1, 3), result)
        }

    // ---------------------------------------------------------------------------
    // throttleLatest — rate limiting
    // ---------------------------------------------------------------------------

    @Test
    fun `throttleLatest limits emission count to roughly one per period`() =
        runTest {
            val result = mutableListOf<Int>()

            flow {
                repeat(100) { i ->
                    emit(i)
                    kotlinx.coroutines.delay(1) // 100 values over ~100ms virtual time
                }
            }.throttleLatest(16L)
                .collect { result.add(it) }

            // 100 ms / 16 ms ≈ 6 windows; allow generous upper bound for off-by-one on boundaries
            assertTrue(result.size <= 10, "Expected ≤10 emissions but got ${result.size}")
            assertEquals(0, result.first(), "First value must be 0 (immediate emit)")
            assertEquals(99, result.last(), "Final value must be 99 (flushed on completion)")
        }

    // ---------------------------------------------------------------------------
    // throttleLatest — final value flush
    // ---------------------------------------------------------------------------

    @Test
    fun `throttleLatest flushes final pending value when upstream completes`() =
        runTest {
            val result = mutableListOf<Int>()

            flow {
                emit(1) // t=0 → emitted immediately
                kotlinx.coroutines.delay(5)
                emit(2) // t=5   \
                kotlinx.coroutines.delay(5) //        >  arrive during 16ms window
                emit(99) // t=10  /   99 is the latest pending value
                // upstream ends at t=10, before the 16ms window elapses
            }.throttleLatest(16L)
                .collect { result.add(it) }

            assertTrue(99 in result, "Final pending value 99 must be flushed on upstream completion")
        }

    @Test
    fun `throttleLatest emits every value when all arrive spaced by more than period`() =
        runTest {
            val result = mutableListOf<Int>()

            flow {
                emit(1)
                kotlinx.coroutines.delay(20)
                emit(2)
                kotlinx.coroutines.delay(20)
                emit(3)
            }.throttleLatest(16L)
                .collect { result.add(it) }

            // Each value arrives after the current window expires → all emitted, none dropped
            assertEquals(listOf(1, 2, 3), result)
        }

    // ---------------------------------------------------------------------------
    // throttleLatest — interaction with distinctUntilChanged
    // ---------------------------------------------------------------------------

    @Test
    fun `distinctUntilChanged after throttleLatest suppresses duplicate consecutive emissions`() =
        runTest {
            val result = mutableListOf<Int>()

            flow {
                emit(1)
                kotlinx.coroutines.delay(20)
                emit(1) // same value, 20ms after the previous — would be a fresh emission
                kotlinx.coroutines.delay(20)
                emit(2)
            }.throttleLatest(16L)
                .distinctUntilChanged()
                .collect { result.add(it) }

            assertEquals(listOf(1, 2), result)
        }
}
