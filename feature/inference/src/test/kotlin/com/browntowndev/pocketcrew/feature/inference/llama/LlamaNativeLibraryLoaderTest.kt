package com.browntowndev.pocketcrew.feature.inference.llama

import org.junit.jupiter.api.Test

class LlamaNativeLibraryLoaderTest {
    @Test
    fun `loadOptimalLibrary swallows cpudetect failure without throwing`() {
        LlamaNativeLibraryLoader.loadOptimalLibrary { 0 }
    }
}
