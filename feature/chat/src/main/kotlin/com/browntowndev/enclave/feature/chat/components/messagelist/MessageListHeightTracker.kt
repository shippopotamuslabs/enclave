package com.browntowndev.enclave.feature.chat.components.messagelist

internal class MessageListHeightTracker {
    private val heights = mutableMapOf<String, Int>()

    fun get(heightKey: String): Int? = heights[heightKey]

    fun onSizeChanged(
        heightKey: String,
        newHeightPx: Int,
    ): Boolean {
        val previous = heights[heightKey]
        if (!shouldCommitMessageHeightPx(previous, newHeightPx)) {
            return false
        }
        heights[heightKey] = newHeightPx
        return true
    }
}
