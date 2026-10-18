package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMusicGenerationPort
import com.browntowndev.enclave.core.testing.FakeVideoGenerationPort
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.usecase.media.test.FakeDefaultModelRepositoryWithCalls
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GenerateVideoMusicOverrideTest {
    private val defaultModelRepository = FakeDefaultModelRepositoryWithCalls()
    private val resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase()
    private val mediaProviderRepository = FakeMediaProviderRepository()
    private val videoGenerationPort = FakeVideoGenerationPort()
    private val musicGenerationPort = FakeMusicGenerationPort()

    private lateinit var videoUseCase: GenerateVideoUseCase
    private lateinit var musicUseCase: GenerateMusicUseCase

    @BeforeEach
    fun setup() {
        // Canned providers for the override ids so a faithful Green override branch succeeds.
        val videoProvider =
            MediaProviderAsset(
                id = MediaProviderId("fal-v1"),
                displayName = "Fal Video",
                provider = ApiProvider.OPENAI,
                capability = MediaCapability.VIDEO,
                credentialAlias = "fal-v1-alias",
            )
        val musicProvider =
            MediaProviderAsset(
                id = MediaProviderId("fal-m1"),
                displayName = "Fal Music",
                provider = ApiProvider.OPENAI,
                capability = MediaCapability.MUSIC,
                credentialAlias = "fal-m1-alias",
            )
        mediaProviderRepository.emitProviders(listOf(videoProvider, musicProvider))
        videoGenerationPort.result = Result.success(byteArrayOf(1))
        musicGenerationPort.result = Result.success(byteArrayOf(1))

        // Wire the REAL video resolver so a lazy Green impl that drops the override
        // falls through to getDefault(VIDEO_GENERATION), tripping the MD01 assertion.
        val resolveAssignedMediaProviderUseCase =
            ResolveAssignedMediaProviderUseCase(
                defaultModelRepository = defaultModelRepository,
                mediaProviderRepository = mediaProviderRepository,
            )
        videoUseCase =
            GenerateVideoUseCase(
                resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                resolveAssignedMediaProviderUseCase = resolveAssignedMediaProviderUseCase,
                videoGenerationPort = videoGenerationPort,
            )
        musicUseCase =
            GenerateMusicUseCase(
                defaultModelRepository = defaultModelRepository,
                mediaProviderRepository = mediaProviderRepository,
                musicGenerationPort = musicGenerationPort,
            )
    }

    @Nested
    @DisplayName("S05d — Video MediaProvider override bypasses default slot")
    inner class S05dVideo {
        @Test
        @DisplayName("S05d — Video override resolves provider; default slot untouched")
        fun s05d_videoMediaProviderOverride() =
            runTest {
                val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-v1"), "Fal Video")

                val result = videoUseCase("prompt", VideoGenerationSettings(), override = override)

                assertTrue(result.isSuccess)
                assertFalse(defaultModelRepository.getDefaultCalls.contains(ModelType.VIDEO_GENERATION))
            }
    }

    @Nested
    @DisplayName("S05e — Music MediaProvider override bypasses default slot")
    inner class S05eMusic {
        @Test
        @DisplayName("S05e/MD01-music — Music override resolves provider; default slot untouched")
        fun s05e_musicMediaProviderOverride() =
            runTest {
                val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-m1"), "Fal Music")

                val result = musicUseCase("prompt", MusicGenerationSettings(), override = override)

                assertTrue(result.isSuccess)
                assertFalse(defaultModelRepository.getDefaultCalls.contains(ModelType.MUSIC_GENERATION))
            }
    }
}
