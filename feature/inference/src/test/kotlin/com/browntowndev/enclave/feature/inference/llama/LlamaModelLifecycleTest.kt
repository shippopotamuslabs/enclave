package com.browntowndev.enclave.feature.inference.llama

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LlamaModelLifecycleTest {
    @Test
    fun `checkAndCompressContext reports native context usage without mutating KV positions`() {
        var compressCalls = 0
        val compress: (Int, Int) -> Boolean = { _, _ ->
            compressCalls++
            true
        }
        val lifecycle =
            LlamaModelLifecycle(
                getContextSize = { 100 },
                getContextUsage = { 85 },
                compressContext = compress,
                getState = { null },
                setState = { true },
            )
        lifecycle.loaded.set(true)
        lifecycle.lastPromptTokens = 10
        lifecycle.lastGeneratedTokens = 10

        lifecycle.checkAndCompressContext()

        assertEquals(0, compressCalls)
    }

    @Test
    fun `checkAndCompressContext falls back to tracked token counts without mutating KV positions`() {
        var compressCalls = 0
        val compress: (Int, Int) -> Boolean = { _, _ ->
            compressCalls++
            true
        }
        val lifecycle =
            LlamaModelLifecycle(
                getContextSize = { 100 },
                getContextUsage = { 0 },
                compressContext = compress,
                getState = { null },
                setState = { true },
            )
        lifecycle.loaded.set(true)
        lifecycle.lastPromptTokens = 60
        lifecycle.lastGeneratedTokens = 25

        lifecycle.checkAndCompressContext()

        assertEquals(0, compressCalls)
    }
}
