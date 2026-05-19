package com.browntowndev.pocketcrew.feature.inference.llama

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks loaded model state and context-window pressure for JNI llama.
 */
internal class LlamaModelLifecycle(
    private val getContextSize: () -> Int,
    private val getContextUsage: () -> Int,
    private val compressContext: (seqId: Int, factor: Int) -> Boolean,
    private val getState: () -> ByteArray?,
    private val setState: (ByteArray) -> Boolean,
) {
    companion object {
        private const val TAG = "LlamaModelLifecycle"
        private const val COMPRESSION_THRESHOLD_RATIO = 0.8f
    }

    var currentConfig: LlamaModelConfig? = null
    val loaded = AtomicBoolean(false)

    var lastPromptTokens = 0
    var lastGeneratedTokens = 0

    fun recordGenerationComplete(
        promptTokens: Int,
        generatedTokens: Int,
    ) {
        lastPromptTokens = promptTokens
        lastGeneratedTokens = generatedTokens
    }

    fun clearAfterUnload() {
        currentConfig = null
        loaded.set(false)
        lastPromptTokens = 0
        lastGeneratedTokens = 0
    }

    /**
     * Estimates whether the current prompt plus response will exceed the compression threshold.
     * Does not mutate llama.cpp KV positions — conversation management stays at turn boundaries.
     */
    fun isContextExceeded(): Boolean {
        if (!loaded.get()) return false

        return try {
            val contextSize = getContextSize()
            if (contextSize <= 0) {
                Log.w(TAG, "Cannot check context: size=$contextSize")
                return false
            }

            val nativeUsage = getContextUsage()
            val fallbackUsage = lastPromptTokens + lastGeneratedTokens
            val totalTokensUsed =
                when {
                    nativeUsage > 0 -> nativeUsage
                    fallbackUsage > 0 -> fallbackUsage
                    else -> 0
                }

            if (totalTokensUsed <= 0) {
                Log.w(TAG, "No token usage data available yet")
                return false
            }

            val usageRatio = totalTokensUsed.toFloat() / contextSize.toFloat()
            Log.i(TAG, "Context usage: $totalTokensUsed / $contextSize tokens (${(usageRatio * 100).toInt()}%)")

            if (usageRatio > COMPRESSION_THRESHOLD_RATIO) {
                Log.i(
                    TAG,
                    "Context at ${(usageRatio * 100).toInt()}%; deferring management to turn-boundary history rebuild",
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking context", e)
            false
        }
    }

    fun checkAndCompressContext(): Boolean = isContextExceeded()

    fun applyCompression(factor: Int): Boolean =
        try {
            compressContext(0, factor)
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing context", e)
            false
        }

    fun saveState(): ByteArray? =
        try {
            getState()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving state", e)
            null
        }

    fun restoreState(state: ByteArray): Boolean =
        try {
            setState(state)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring state", e)
            false
        }

    fun readContextUsage(): Int =
        try {
            getContextUsage()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting context usage", e)
            0
        }

    fun readContextSize(): Int =
        try {
            getContextSize()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting context size", e)
            0
        }
}
