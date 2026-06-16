package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ActiveStudioGenerationCodecOverrideTest {
    @Test
    @DisplayName("E06a-forward — Encode+decode preserves a MediaProvider override")
    fun e06a_overrideRoundTrip() {
        val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")
        val original =
            ActiveStudioGeneration(
                id = "gen-42",
                prompt = "sunset over mountains",
                mediaType = MediaCapability.IMAGE,
                settings = ImageGenerationSettings(generationCount = 2),
                override = override,
            )

        val encoded = ActiveStudioGenerationCodec.encode(original)
        val decoded = ActiveStudioGenerationCodec.decode(encoded)

        val roundTripped = requireNotNull(decoded) { "decode failed for: $encoded" }
        assertEquals(override, roundTripped.override)
    }

    @Test
    @DisplayName("E06a-backward — Old payload (no override key) decodes with override == null")
    fun e06a_backwardCompatibleDecode() {
        val oldPayload =
            """{"id":"gen-1","prompt":"old prompt","mediaType":"IMAGE","settingsKind":"image","aspectRatio":"ONE_ONE","quality":"SPEED","seed":"","generationCount":1,"img2ImgDenoiseStrength":0.5}"""

        val decoded = ActiveStudioGenerationCodec.decode(oldPayload)

        assertNotNull(decoded)
        assertNull(decoded?.override)
    }
}
