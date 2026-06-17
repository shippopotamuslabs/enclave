package com.browntowndev.enclave.feature.inference.llama

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LlamaNativeLibraryLoaderTest {
    @Test
    fun `loadOptimalLibrary swallows cpudetect failure without throwing`() {
        LlamaNativeLibraryLoader.loadOptimalLibrary { 0 }
    }

    @Test
    fun `resolvePreferredLibrary uses SVE when bits are wide enough`() {
        assertEquals(
            LlamaNativeLibraryLoader.LIBRARY_SVE,
            LlamaNativeLibraryLoader.resolvePreferredLibrary(sveBits = 512, isBlacklisted = false),
        )
    }

    @Test
    fun `resolvePreferredLibrary uses NEON for narrow SVE`() {
        assertEquals(
            LlamaNativeLibraryLoader.LIBRARY_NEON,
            LlamaNativeLibraryLoader.resolvePreferredLibrary(sveBits = 128, isBlacklisted = false),
        )
    }

    @Test
    fun `resolvePreferredLibrary uses NEON when blacklisted`() {
        assertEquals(
            LlamaNativeLibraryLoader.LIBRARY_NEON,
            LlamaNativeLibraryLoader.resolvePreferredLibrary(sveBits = 512, isBlacklisted = true),
        )
    }

    @Test
    fun `loadLibraryWithFallback returns null when both variants are missing`() {
        val loaded =
            LlamaNativeLibraryLoader.loadLibraryWithFallback(
                preferred = "llama-jni-library-that-does-not-exist",
                fallback = "llama-jni-fallback-that-does-not-exist",
            )

        assertNull(loaded)
    }
}