package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GeneratedMediaAsset
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MediaGenerationOrigin
import com.browntowndev.enclave.domain.model.media.MediaGenerationRequest
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.util.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class GenerateMediaUseCaseTest {
    private val generateImageUseCase = mockk<GenerateImageUseCase>()
    private val generateVideoUseCase = mockk<GenerateVideoUseCase>()
    private val generateMusicUseCase = mockk<GenerateMusicUseCase>()
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val clock = FakeClock()

    private lateinit var useCase: GenerateMediaUseCase

    @BeforeEach
    fun setUp() {
        clock.now = 0L
        useCase =
            GenerateMediaUseCase(
                generateImageUseCase = generateImageUseCase,
                generateVideoUseCase = generateVideoUseCase,
                generateMusicUseCase = generateMusicUseCase,
                studioRepository = studioRepository,
                clock = clock,
            )
    }

    @Test
    fun `S-GM-01 IMAGE dispatches to GenerateImageUseCase with same prompt and settings`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            coEvery { generateImageUseCase("p1", settings) } returns Result.success(listOf(byteArrayOf(1)))

            useCase(
                MediaGenerationRequest(
                    prompt = "p1",
                    capability = MediaCapability.IMAGE,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                ),
            )

            coVerify(exactly = 1) { generateImageUseCase("p1", settings) }
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
            coEvery { generateImageUseCase(any(), any()) } returns Result.success(listOf(byteArrayOf(9), byteArrayOf(8)))
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
            coEvery { generateImageUseCase(any(), any()) } returns Result.success(listOf(byteArrayOf(9), byteArrayOf(8)))
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
            coEvery { generateImageUseCase(any(), any()) } returns
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
            coEvery { generateImageUseCase(any(), any()) } returns Result.success(listOf(byteArrayOf(1)))
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

            assertTrue(result.getOrThrow().first().id.startsWith("chat_media_"))
        }

    @Test
    fun `S-GM-08 STUDIO_FOREGROUND origin uses createdAt 1000 then 1001 for two assets`() =
        runTest {
            clock.now = 1000L
            val settings = ImageGenerationSettings(generationCount = 1)
            coEvery { generateImageUseCase(any(), any()) } returns Result.success(listOf(byteArrayOf(1), byteArrayOf(2)))
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
            coEvery { generateImageUseCase("p1", settings) } returns Result.success(listOf(byteArrayOf(1)))
            coEvery { studioRepository.cacheEphemeralMedia(any(), any()) } returns "file:///cache/a"

            useCase(
                MediaGenerationRequest(
                    prompt = "p1",
                    capability = MediaCapability.IMAGE,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                ),
            )

            coVerify(exactly = 1) { generateImageUseCase("p1", settings) }

            val source =
                resolveProductionSource(
                    "src/main/kotlin/com/browntowndev/enclave/domain/usecase/media/GenerateMediaUseCase.kt",
                )
            assertEquals(0, source.readText().split("ImageGenerationPort").size - 1)
        }

    private class FakeClock : Clock {
        var now: Long = 0L

        override fun currentTimeMillis(): Long = now
    }

    private fun resolveProductionSource(relativePath: String): File {
        var dir = File(System.getProperty("user.dir"))
        for (unused in 0 until 6) {
            val candidate = dir.resolve(relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: break
        }
        error("Could not resolve production source at $relativePath")
    }
}
