package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResolveMediaModelTypeUseCaseTest {
    private val useCase = ResolveMediaModelTypeUseCase()

    @Test
    fun imageWithoutReference_resolvesImageGeneration() {
        assertEquals(
            ModelType.IMAGE_GENERATION,
            useCase(MediaCapability.IMAGE, ImageGenerationSettings()),
        )
    }

    @Test
    fun imageWithReference_resolvesImageEdit() {
        assertEquals(
            ModelType.IMAGE_EDIT,
            useCase(
                MediaCapability.IMAGE,
                ImageGenerationSettings(referenceImageUri = "file://reference.png"),
            ),
        )
    }

    @Test
    fun videoWithoutReference_resolvesVideoGeneration() {
        assertEquals(
            ModelType.VIDEO_GENERATION,
            useCase(MediaCapability.VIDEO, VideoGenerationSettings()),
        )
    }

    @Test
    fun videoWithReference_resolvesImageToVideo() {
        assertEquals(
            ModelType.IMAGE_TO_VIDEO,
            useCase(
                MediaCapability.VIDEO,
                VideoGenerationSettings(referenceImageUri = "file://reference.png"),
            ),
        )
    }

    @Test
    fun music_resolvesMusicGeneration() {
        assertEquals(
            ModelType.MUSIC_GENERATION,
            useCase(MediaCapability.MUSIC, MusicGenerationSettings()),
        )
    }
}
