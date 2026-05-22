package com.browntowndev.enclave.feature.inference.llama

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class LlamaModelLifecycleTest {
    @Test
    fun `checkAndCompressContext reports native context usage without mutating KV positions`() {
        val compress = mockk<(Int, Int) -> Boolean>(relaxed = true)
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

        verify(exactly = 0) { compress(any(), any()) }
    }

    @Test
    fun `checkAndCompressContext falls back to tracked token counts without mutating KV positions`() {
        val compress = mockk<(Int, Int) -> Boolean>(relaxed = true)
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

        verify(exactly = 0) { compress(any(), any()) }
    }
}
