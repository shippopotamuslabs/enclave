package com.browntowndev.pocketcrew.domain.util

/**
 * Abstraction for time retrieval to enable deterministic testing.
 */
interface Clock {
    /**
     * Returns the current time in milliseconds.
     */
    fun currentTimeMillis(): Long
}

/**
 * Production implementation using System.currentTimeMillis().
 */
class SystemClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
