package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.model.media.isEligibleFor
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StudioModelOverrideEligibilityTest {
    private val providers =
        listOf(
            provider("fal-t2i", MediaCapability.IMAGE, setOf(MediaTask.TEXT_TO_IMAGE)),
            provider("fal-edit", MediaCapability.IMAGE, setOf(MediaTask.EDIT_IMAGE)),
            provider("fal-t2v", MediaCapability.VIDEO, setOf(MediaTask.TEXT_TO_VIDEO)),
            provider("fal-i2v", MediaCapability.VIDEO, setOf(MediaTask.IMAGE_TO_VIDEO)),
        )

    @Test
    @DisplayName("TEXT_TO_IMAGE provider is eligible for IMAGE_GENERATION but not IMAGE_EDIT")
    fun textToImageProviderSlotTransition() {
        val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-t2i"), "Fal T2I")

        assertTrue(override.isEligibleFor(ModelType.IMAGE_GENERATION, providers))
        assertFalse(override.isEligibleFor(ModelType.IMAGE_EDIT, providers))
    }

    @Test
    @DisplayName("EDIT_IMAGE provider is eligible for IMAGE_EDIT but not IMAGE_GENERATION")
    fun editImageProviderSlotTransition() {
        val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-edit"), "Fal Edit")

        assertTrue(override.isEligibleFor(ModelType.IMAGE_EDIT, providers))
        assertFalse(override.isEligibleFor(ModelType.IMAGE_GENERATION, providers))
    }

    @Test
    @DisplayName("TEXT_TO_VIDEO provider is eligible for VIDEO_GENERATION but not IMAGE_TO_VIDEO")
    fun textToVideoProviderSlotTransition() {
        val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-t2v"), "Fal T2V")

        assertTrue(override.isEligibleFor(ModelType.VIDEO_GENERATION, providers))
        assertFalse(override.isEligibleFor(ModelType.IMAGE_TO_VIDEO, providers))
    }

    @Test
    @DisplayName("Local override remains eligible across image generation and edit slots")
    fun localOverrideSurvivesImageSlotFlip() {
        val override =
            StudioModelOverride.Local(
                configId = LocalModelConfigurationId("sd-config"),
                displayName = "SD Pack",
            )

        assertTrue(override.isEligibleFor(ModelType.IMAGE_GENERATION, providers))
        assertTrue(override.isEligibleFor(ModelType.IMAGE_EDIT, providers))
        assertFalse(override.isEligibleFor(ModelType.VIDEO_GENERATION, providers))
    }

    private fun provider(
        id: String,
        capability: MediaCapability,
        tasks: Set<MediaTask>,
    ): MediaProviderAsset =
        MediaProviderAsset(
            id = MediaProviderId(id),
            displayName = id,
            provider = ApiProvider.OPENAI,
            capability = capability,
            credentialAlias = "$id-alias",
            tasks = tasks,
        )
}