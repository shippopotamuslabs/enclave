package com.browntowndev.enclave.feature.studio.service

import android.content.Intent
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioGenerationIntentCodecTest {
    @Test
    fun `music settings round trip through intent extras`() {
        val intent = Intent()
        val settings = MusicGenerationSettings(duration = 45, tempo = 95)

        StudioGenerationIntentCodec.writeSettings(intent, settings)

        assertEquals(settings, StudioGenerationIntentCodec.readSettings(intent))
    }

    @Test
    fun `video settings round trip through intent extras`() {
        val intent = Intent()
        val settings =
            VideoGenerationSettings(
                aspectRatio = AspectRatio.SIXTEEN_NINE,
                quality = GenerationQuality.HIGH,
                videoDuration = 12,
                videoResolution = "720p",
                autoGenerateVideo = true,
                referenceImageUri = "content://image",
                seed = "42",
            )

        StudioGenerationIntentCodec.writeSettings(intent, settings)

        assertEquals(settings, StudioGenerationIntentCodec.readSettings(intent))
    }

    @Test
    fun `image settings round trip through intent extras`() {
        val intent = Intent()
        val settings =
            ImageGenerationSettings(
                aspectRatio = AspectRatio.THREE_FOUR,
                quality = GenerationQuality.QUALITY,
                referenceImageUri = "content://ref",
                seed = "seed",
                generationCount = 3,
            )

        StudioGenerationIntentCodec.writeSettings(intent, settings)

        assertEquals(settings, StudioGenerationIntentCodec.readSettings(intent))
    }
}
