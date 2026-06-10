package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ActiveStudioGenerationCodecTest {
    @Test
    fun encodeDecode_roundTripsActiveGeneration() {
        val original =
            ActiveStudioGeneration(
                id = "gen-42",
                prompt = "sunset over mountains",
                mediaType = MediaCapability.IMAGE,
                settings = ImageGenerationSettings(generationCount = 2),
            )

        val encoded = ActiveStudioGenerationCodec.encode(original)
        val decoded = ActiveStudioGenerationCodec.decode(encoded)

        val roundTripped = requireNotNull(decoded) { "decode failed for: $encoded" }
        assertEquals(original.id, roundTripped.id)
        assertEquals(original.prompt, roundTripped.prompt)
        assertEquals(original.mediaType, roundTripped.mediaType)
        val originalSettings = original.settings as ImageGenerationSettings
        val decodedSettings = roundTripped.settings as ImageGenerationSettings
        assertEquals(originalSettings.generationCount, decodedSettings.generationCount)
    }
}