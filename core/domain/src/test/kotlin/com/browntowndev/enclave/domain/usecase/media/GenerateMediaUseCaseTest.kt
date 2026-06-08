package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.MediaGenerationOrigin
import com.browntowndev.enclave.domain.model.media.MediaGenerationRequest
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.util.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateMediaUseCaseTest {
    private val generateImageUseCase = mockk<GenerateImageUseCase>()
    private val generateVideoUseCase = mockk<GenerateVideoUseCase>()
    private val generateMusicUseCase = mockk<GenerateMusicUseCase>()
    private val resolveMediaModelTypeUseCase = mockk<ResolveMediaModelTypeUseCase>()
    private val resolveAssignedImageGenerationTargetUseCase = mockk<ResolveAssignedImageGenerationTargetUseCase>()
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val clock = FakeClock()

    private lateinit var useCase: GenerateMediaUseCase

    @BeforeEach
    fun setUp() {
        clock.now = 0L
        every { resolveMediaModelTypeUseCase(any(), any()) } returns ModelType.IMAGE_GENERATION
        coEvery { resolveAssignedImageGenerationTargetUseCase(any()) } returns
            Result.success(
                ImageGenerationTarget.Remote(
                    providerAssetId = com.browntowndev.enclave.domain.model.config.MediaProviderId("remote"),
                    providerDisplayName = "Remote",
                ),
            )
        useCase =
            GenerateMediaUseCase(
                generateImageUseCase = generateImageUseCase,
                generateVideoUseCase = generateVideoUseCase,
                generateMusicUseCase = generateMusicUseCase,
                resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                studioRepository = studioRepository,
                clock = clock,
            )
    }

    @Test
    fun `S-GM-01 IMAGE dispatches to GenerateImageUseCase with same prompt and settings`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            coEvery { generateImageUseCase("p1", settings, any()) } returns Result.success(listOf(byteArrayOf(1)))

            useCase(
                MediaGenerationRequest(
                    prompt = "p1",
                    capability = MediaCapability.IMAGE,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                ),
            )

            coVerify(exactly = 1) { generateImageUseCase("p1", settings, any()) }
        }

    @Test
    fun `S-GM-02 VIDEO dispatches to GenerateVideoUseCase with same prompt and settings`() =
        runTest {
            val settings = VideoGenerationSettings(videoDuration = 6)
            coEvery { generateVideoUseCase("clip", settings) } returns Result.success(byteArrayOf(2))

            useCase(
                MediaGenerationRequest(
                    prompt = "clip",
                    capability = MediaCapability.VIDEO,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                ),
            )

            coVerify(exactly = 1) { generateVideoUseCase("clip", settings) }
        }

    @Test
    fun `S-GM-03 MUSIC dispatches to GenerateMusicUseCase with same prompt and settings`() =
        runTest {
            val settings = MusicGenerationSettings(duration = 30)
            coEvery { generateMusicUseCase("beat", settings) } returns Result.success(byteArrayOf(3))

            useCase(
                MediaGenerationRequest(
                    prompt = "beat",
                    capability = MediaCapability.MUSIC,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                ),
            )

            coVerify(exactly = 1) { generateMusicUseCase("beat", settings) }
        }

    @Test
    fun `S-GM-04 Success caches with IMAGE mediaType and addEphemeralMedia once per byte array`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            coEvery { generateImageUseCase(any(), any(), any()) } returns Result.success(listOf(byteArrayOf(9), byteArrayOf(8)))
            coEvery { studioRepository.cacheEphemeralMedia(byteArrayOf(9), "IMAGE") } returns "file:///cache/a"
            coEvery { studioRepository.cacheEphemeralMedia(byteArrayOf(8), "IMAGE") } returns "file:///cache/b"

            useCase(
                MediaGenerationRequest(
                    prompt = "p",
                    capability = MediaCapability.IMAGE,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                ),
            )

            coVerify { studioRepository.cacheEphemeralMedia(byteArrayOf(9), "IMAGE") }
            coVerify { studioRepository.cacheEphemeralMedia(byteArrayOf(8), "IMAGE") }
            coVerify(exactly = 2) { studioRepository.addEphemeralMedia(any()) }
        }

    @Test
    fun `S-GM-05 IMAGE multi-byte success returns two GeneratedMediaAsset entries`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            coEvery { generateImageUseCase(any(), any(), any()) } returns Result.success(listOf(byteArrayOf(9), byteArrayOf(8)))
            coEvery { studioRepository.cacheEphemeralMedia(byteArrayOf(9), "IMAGE") } returns "file:///cache/a"
            coEvery { studioRepository.cacheEphemeralMedia(byteArrayOf(8), "IMAGE") } returns "file:///cache/b"

            val result =
                useCase(
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = settings,
                        origin = MediaGenerationOrigin.CHAT_TOOL,
                    ),
                )

            assertTrue(result.isSuccess)
            val assets = result.getOrThrow()
            assertEquals(2, assets.size)
            assertEquals("file:///cache/a", assets[0].localUri)
            assertEquals("file:///cache/b", assets[1].localUri)
            assertEquals("IMAGE", assets[0].mediaType)
            assertEquals("IMAGE", assets[1].mediaType)
        }

    @Test
    fun `S-GM-06 Delegate failure returns failure without cache or addEphemeralMedia`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            coEvery { generateImageUseCase(any(), any(), any()) } returns
                Result.failure(IllegalStateException("No image generation provider assigned"))

            val result =
                useCase(
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = settings,
                        origin = MediaGenerationOrigin.CHAT_TOOL,
                    ),
                )

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { studioRepository.cacheEphemeralMedia(any(), any()) }
            coVerify(exactly = 0) { studioRepository.addEphemeralMedia(any()) }
            assertEquals("No image generation provider assigned", result.exceptionOrNull()?.message)
        }

    @Test
    fun `S-GM-07 CHAT_TOOL origin uses id prefix chat_media_`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            coEvery { generateImageUseCase(any(), any(), any()) } returns Result.success(listOf(byteArrayOf(1)))
            coEvery { studioRepository.cacheEphemeralMedia(any(), any()) } returns "file:///cache/a"

            val result =
                useCase(
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = settings,
                        origin = MediaGenerationOrigin.CHAT_TOOL,
                    ),
                )

            assertTrue(
                result
                    .getOrThrow()
                    .first()
                    .id
                    .startsWith("chat_media_"),
            )
        }

    @Test
    fun `S-GM-08 STUDIO_FOREGROUND origin uses createdAt 1000 then 1001 for two assets`() =
        runTest {
            clock.now = 1000L
            val settings = ImageGenerationSettings(generationCount = 1)
            coEvery { generateImageUseCase(any(), any(), any()) } returns Result.success(listOf(byteArrayOf(1), byteArrayOf(2)))
            coEvery { studioRepository.cacheEphemeralMedia(any(), any()) } returnsMany
                listOf("file:///cache/a", "file:///cache/b")

            val result =
                useCase(
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = settings,
                        origin = MediaGenerationOrigin.STUDIO_FOREGROUND,
                    ),
                )

            val assets = result.getOrThrow()
            assertEquals(1000L, assets[0].createdAt)
            assertEquals(1001L, assets[1].createdAt)
            assertFalse(assets[0].id.startsWith("chat_media_"))
        }

    @Test
    fun `E-GM-01 Mismatched settings type returns IllegalArgumentException`() =
        runTest {
            val result =
                useCase(
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = MusicGenerationSettings(duration = 30),
                        origin = MediaGenerationOrigin.CHAT_TOOL,
                    ),
                )

            assertTrue(result.isFailure)
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
            assertEquals(
                "Settings type does not match capability IMAGE",
                result.exceptionOrNull()?.message,
            )
        }

    @Test
    fun `M-GM-01 GenerateMediaUseCase IMAGE path never calls ImageGenerationPort`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            coEvery { generateImageUseCase("p1", settings, any()) } returns Result.success(listOf(byteArrayOf(1)))
            coEvery { studioRepository.cacheEphemeralMedia(any(), any()) } returns "file:///cache/a"

            useCase(
                MediaGenerationRequest(
                    prompt = "p1",
                    capability = MediaCapability.IMAGE,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                ),
            )

            coVerify(exactly = 1) { generateImageUseCase("p1", settings, any()) }
        }

    @Test
    fun `S-GM-09 STUDIO_FOREGROUND local generationCount 3 generates sequentially and adds incrementally`() =
        runTest {
            clock.now = 1000L
            val settings = ImageGenerationSettings(generationCount = 3)
            val localTarget = mockk<ImageGenerationTarget.LocalStableDiffusion>()
            coEvery { resolveAssignedImageGenerationTargetUseCase(ModelType.IMAGE_GENERATION) } returns
                Result.success(localTarget)
            coEvery { generateImageUseCase(any(), any(), any()) } returnsMany
                listOf(
                    Result.success(listOf(byteArrayOf(1))),
                    Result.success(listOf(byteArrayOf(2))),
                    Result.success(listOf(byteArrayOf(3))),
                )
            coEvery { studioRepository.cacheEphemeralMedia(any(), any()) } returnsMany
                listOf("file:///cache/a", "file:///cache/b", "file:///cache/c")

            val result =
                useCase(
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = settings,
                        origin = MediaGenerationOrigin.STUDIO_FOREGROUND,
                    ),
                )

            assertTrue(result.isSuccess)
            assertEquals(3, result.getOrThrow().size)
            coVerify(exactly = 3) {
                generateImageUseCase(
                    "p",
                    match { settings ->
                        settings is ImageGenerationSettings && settings.generationCount == 1
                    },
                    any(),
                )
            }
            coVerify(exactly = 3) { studioRepository.addEphemeralMedia(any()) }
            coVerify(exactly = 0) { generateImageUseCase("p", settings, any()) }
        }

    @Test
    fun `IMAGE forwards progress callback to GenerateImageUseCase`() =
        runTest {
            val settings = ImageGenerationSettings()
            val progressSlot = slot<(ImageGenerationProgress) -> Unit>()
            coEvery { generateImageUseCase(any(), any(), capture(progressSlot)) } coAnswers {
                progressSlot.captured(ImageGenerationProgress.LoadingModel)
                Result.success(listOf(byteArrayOf(1)))
            }
            coEvery { studioRepository.cacheEphemeralMedia(any(), any()) } returns "file:///cache/a"
            val observed = mutableListOf<ImageGenerationProgress>()

            useCase(
                request =
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = settings,
                        origin = MediaGenerationOrigin.STUDIO_FOREGROUND,
                    ),
                onImageProgress = observed::add,
            )

            assertEquals(listOf(ImageGenerationProgress.LoadingModel), observed)
        }

    private class FakeClock : Clock {
        var now: Long = 0L

        override fun currentTimeMillis(): Long = now
    }
}
