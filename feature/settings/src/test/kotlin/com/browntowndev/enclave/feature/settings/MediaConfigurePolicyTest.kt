package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaConfigurePolicyTest {
    @Test
    fun `showsMediaBaseUrlOverride is only true for OpenAI`() {
        assertTrue(ApiProvider.OPENAI.showsMediaBaseUrlOverride())
        assertFalse(ApiProvider.FAL.showsMediaBaseUrlOverride())
        assertFalse(ApiProvider.XAI.showsMediaBaseUrlOverride())
        assertFalse(ApiProvider.GOOGLE.showsMediaBaseUrlOverride())
    }

    @Test
    fun `locksMediaTaskToggles is only true for fal ai`() {
        assertTrue(ApiProvider.FAL.locksMediaTaskToggles())
        assertFalse(ApiProvider.OPENAI.locksMediaTaskToggles())
        assertFalse(ApiProvider.XAI.locksMediaTaskToggles())
        assertFalse(ApiProvider.GOOGLE.locksMediaTaskToggles())
    }

    @Test
    fun `isMediaProviderSaveEnabled requires at least one image task for image capability`() {
        assertFalse(
            isMediaProviderSaveEnabled(
                displayName = "Fal Images",
                modelName = "fal-ai/flux",
                capability = MediaCapability.IMAGE,
                tasks = emptySet(),
                apiKeyPresent = true,
            ),
        )
        assertTrue(
            isMediaProviderSaveEnabled(
                displayName = "Fal Images",
                modelName = "fal-ai/flux",
                capability = MediaCapability.IMAGE,
                tasks = setOf(MediaTask.TEXT_TO_IMAGE),
                apiKeyPresent = true,
            ),
        )
    }

    @Test
    fun `isMediaProviderSaveEnabled requires at least one video task for video capability`() {
        assertFalse(
            isMediaProviderSaveEnabled(
                displayName = "Fal Video",
                modelName = "fal-ai/kling",
                capability = MediaCapability.VIDEO,
                tasks = setOf(MediaTask.TEXT_TO_IMAGE),
                apiKeyPresent = true,
            ),
        )
        assertTrue(
            isMediaProviderSaveEnabled(
                displayName = "Fal Video",
                modelName = "fal-ai/kling",
                capability = MediaCapability.VIDEO,
                tasks = setOf(MediaTask.IMAGE_TO_VIDEO),
                apiKeyPresent = true,
            ),
        )
    }

    @Test
    fun `isMediaProviderSaveEnabled does not require tasks for music capability`() {
        assertTrue(
            isMediaProviderSaveEnabled(
                displayName = "Music",
                modelName = "fal-ai/music",
                capability = MediaCapability.MUSIC,
                tasks = emptySet(),
                apiKeyPresent = true,
            ),
        )
    }
}