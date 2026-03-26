package com.browntowndev.pocketcrew.core.testing

import com.browntowndev.pocketcrew.domain.util.Clock

/**
 * Test implementation of [Clock] that allows controlling time for deterministic testing.
 */
class TestClock(initialTime: Long = 0L) : Clock {
    private var currentTime: Long = initialTime

    override fun currentTimeMillis(): Long = currentTime

    /**
     * Advances the clock by the specified number of milliseconds.
     */
    fun advanceTime(millis: Long) {
        currentTime += millis
    }

    /**
     * Sets the clock to a specific time.
     */
    fun setTime(time: Long) {
        currentTime = time
    }

    /**
     * Resets the clock to zero.
     */
    fun reset() {
        currentTime = 0L
    }
}
