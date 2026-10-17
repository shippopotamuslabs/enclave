package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import com.browntowndev.enclave.feature.studio.test.FakeStudioGenerationDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StudioPromptCoordinatorTest {
    private val studioRepository = FakeStudioRepository()
    private val generationCoordinator =
        StudioGenerationCoordinator(studioRepository, FakeStudioGenerationDispatcher())

    @Test
    fun `restoreLastMediaType sets media type and matching default settings`() {
        val coordinator = StudioPromptCoordinator(generationCoordinator, studioRepository)

        coordinator.restoreLastMediaType(MediaCapability.VIDEO)

        assertEquals(MediaCapability.VIDEO, coordinator.mediaType.value)
        assertTrue(coordinator.settings.value is VideoGenerationSettings)
    }

    @Test
    fun `restoreLastMediaType for music sets music settings`() {
        val coordinator = StudioPromptCoordinator(generationCoordinator, studioRepository)

        coordinator.restoreLastMediaType(MediaCapability.MUSIC)

        assertEquals(MediaCapability.MUSIC, coordinator.mediaType.value)
        assertTrue(coordinator.settings.value is MusicGenerationSettings)
    }

    @Test
    fun `restoreLastMediaType is a no-op when current type already matches`() {
        val coordinator = StudioPromptCoordinator(generationCoordinator, studioRepository)
        // Default mediaType is IMAGE; restoring IMAGE should not replace settings reference.
        val initialSettings = coordinator.settings.value

        coordinator.restoreLastMediaType(MediaCapability.IMAGE)

        assertEquals(MediaCapability.IMAGE, coordinator.mediaType.value)
        assertEquals(initialSettings, coordinator.settings.value)
    }

    @Test
    fun `restoreLastMediaType is skipped once an active generation has been restored`() {
        val coordinator = StudioPromptCoordinator(generationCoordinator, studioRepository)
        coordinator.restoreActiveGeneration(
            ActiveStudioGeneration(
                id = "gen-1",
                prompt = "p",
                mediaType = MediaCapability.MUSIC,
                settings = MusicGenerationSettings(),
            ),
        )

        coordinator.restoreLastMediaType(MediaCapability.VIDEO)

        // Active generation's media type wins; persisted restore must not clobber it.
        assertEquals(MediaCapability.MUSIC, coordinator.mediaType.value)
        assertTrue(coordinator.settings.value is MusicGenerationSettings)
    }

    @Test
    fun `restoreActiveGeneration sets media type and settings`() {
        val coordinator = StudioPromptCoordinator(generationCoordinator, studioRepository)
        val musicSettings = MusicGenerationSettings()

        coordinator.restoreActiveGeneration(
            ActiveStudioGeneration(
                id = "gen-1",
                prompt = "p",
                mediaType = MediaCapability.VIDEO,
                settings = musicSettings,
            ),
        )

        assertEquals(MediaCapability.VIDEO, coordinator.mediaType.value)
        assertEquals(musicSettings, coordinator.settings.value)
    }

    @Test
    fun `restoreLastMediaType applies when no active generation is restored`() {
        val coordinator = StudioPromptCoordinator(generationCoordinator, studioRepository)

        coordinator.restoreLastMediaType(MediaCapability.VIDEO)
        coordinator.restoreLastMediaType(MediaCapability.IMAGE)

        assertEquals(MediaCapability.IMAGE, coordinator.mediaType.value)
        assertTrue(coordinator.settings.value is ImageGenerationSettings)
    }
}
