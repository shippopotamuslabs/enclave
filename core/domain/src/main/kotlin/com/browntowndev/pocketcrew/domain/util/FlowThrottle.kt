package com.browntowndev.pocketcrew.domain.util

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * Emits the most recent upstream value at most once per [periodMs].
 *
 * The first value is emitted immediately (zero-delay on initial delivery).
 * Values arriving within the subsequent [periodMs] window replace each other;
 * only the latest is emitted at the window boundary.  Any value pending at
 * upstream completion is flushed before the returned flow closes.
 *
 * Uses coroutine [delay] internally so [kotlinx.coroutines.test.runTest] virtual
 * time works correctly in tests — no wall-clock calls.
 */
fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> =
    channelFlow {
        val incoming = Channel<T>(Channel.CONFLATED)
        launch {
            collect { value -> incoming.send(value) }
            incoming.close()
        }
        for (value in incoming) {
            send(value)
            kotlinx.coroutines.delay(periodMs)
        }
    }
