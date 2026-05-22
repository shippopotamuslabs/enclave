package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LyriaMusicPromptTest {
    @Test
    fun `buildLyriaPrompt includes duration and tempo for pro model`() {
        val settings = MusicGenerationSettings(duration = 60, tempo = 140)

        val prompt = buildLyriaPrompt("dark trap beat", settings, "lyria-3-pro-preview")

        assertEquals("Create a 60-second track at 140 BPM. dark trap beat", prompt)
    }

    @Test
    fun `buildLyriaPrompt uses fixed clip duration for clip model`() {
        val settings = MusicGenerationSettings(duration = 15, tempo = 85)

        val prompt = buildLyriaPrompt("lofi beat", settings, "lyria-3-clip-preview")

        assertEquals("Create a 30-second track at 85 BPM. lofi beat", prompt)
    }
}
