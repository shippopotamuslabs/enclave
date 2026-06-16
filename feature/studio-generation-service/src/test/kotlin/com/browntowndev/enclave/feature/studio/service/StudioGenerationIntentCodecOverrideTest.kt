package com.browntowndev.enclave.feature.studio.service

import android.content.Intent
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioGenerationIntentCodecOverrideTest {
    @Test
    fun `S04-4 and E06b — MediaProvider override round trips through intent extras`() {
        val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")
        val intent = Intent()

        StudioGenerationIntentCodec.writeOverride(intent, override)

        assertEquals(override, StudioGenerationIntentCodec.readOverride(intent))
    }

    @Test
    fun `E06b-absence — Intent without override extras returns null`() {
        val intent = Intent()
        intent.putExtra(StudioGenerationIntentCodec.EXTRA_SETTINGS_KIND, "image")

        assertNull(StudioGenerationIntentCodec.readOverride(intent))
    }

    @Test
    fun `E06b-local — Local override round trips through intent extras`() {
        val override = StudioModelOverride.Local(LocalModelConfigurationId("sd-config"), "My SD Pack")
        val intent = Intent()

        StudioGenerationIntentCodec.writeOverride(intent, override)

        assertEquals(override, StudioGenerationIntentCodec.readOverride(intent))
    }

    @Test
    fun `E06b-api — Api override round trips through intent extras`() {
        val override = StudioModelOverride.Api(ApiModelConfigurationId("api-config"), "GPT-Image")
        val intent = Intent()

        StudioGenerationIntentCodec.writeOverride(intent, override)

        assertEquals(override, StudioGenerationIntentCodec.readOverride(intent))
    }
}
