package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.port.inference.ImageModerationPort
import com.browntowndev.enclave.domain.port.inference.ImageModerationResult
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import com.browntowndev.enclave.feature.inference.RecordingImageGenerationPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModeratingImageGenerationPortTest {
    @Test
    fun `generateImage passes through every safe image unchanged`() =
        runTest {
            val safeA = "a".toByteArray()
            val safeB = "b".toByteArray()
            val delegate = stubDelegateReturning(listOf(safeA, safeB))
            val port = buildPort(delegate, blockPredicate = { false }, placeholder = "X".toByteArray())

            val result = port.generateImage("p", anyRemoteTarget(), ImageGenerationSettings())

            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrThrow().size)
            assertArrayEquals(safeA, result.getOrThrow()[0])
            assertArrayEquals(safeB, result.getOrThrow()[1])
        }

    @Test
    fun `generateImage substitutes flagged images with the placeholder`() =
        runTest {
            val flagged = "flagged".toByteArray()
            val safe = "safe".toByteArray()
            val placeholder = "BLOCKED".toByteArray()
            val delegate = stubDelegateReturning(listOf(flagged, safe))
            val port =
                buildPort(
                    delegate,
                    blockPredicate = { it.contentEquals(flagged) },
                    placeholder = placeholder,
                )

            val result = port.generateImage("p", anyRemoteTarget(), ImageGenerationSettings())

            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrThrow().size)
            assertArrayEquals(placeholder, result.getOrThrow()[0])
            assertArrayEquals(safe, result.getOrThrow()[1])
        }

    @Test
    fun `generateImage delegates prompt target and settings to the wrapped port`() =
        runTest {
            val delegate = RecordingImageGenerationPort()
            delegate.result = Result.success(emptyList())
            val port = buildPort(delegate, blockPredicate = { false }, placeholder = ByteArray(0))
            val target = anyRemoteTarget()
            val settings = ImageGenerationSettings()

            port.generateImage("prompt", target, settings)

            val call = delegate.calls.single()
            assertEquals("prompt", call.prompt)
            assertEquals(target, call.target)
            assertEquals(settings, call.settings)
        }

    @Test
    fun `generateImage propagates delegate failure without invoking moderation`() =
        runTest {
            val delegate = RecordingImageGenerationPort()
            val failure = IllegalStateException("boom")
            delegate.result = Result.failure(failure)
            val port = buildPort(delegate, blockPredicate = { true }, placeholder = "BLOCKED".toByteArray())

            val result = port.generateImage("p", anyRemoteTarget(), ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
        }

    @Test
    fun `generateImage moderates an empty batch without throwing`() =
        runTest {
            val delegate = stubDelegateReturning(emptyList())
            val port = buildPort(delegate, blockPredicate = { true }, placeholder = "BLOCKED".toByteArray())

            val result = port.generateImage("p", anyLocalTarget(), ImageGenerationSettings())

            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrThrow().size)
        }

    @Test
    fun `generateImage moderates the local target path identically to remote`() =
        runTest {
            val flagged = "flagged".toByteArray()
            val placeholder = "BLOCKED".toByteArray()
            val delegate = stubDelegateReturning(listOf(flagged))
            val port =
                buildPort(delegate, blockPredicate = { it.contentEquals(flagged) }, placeholder = placeholder)

            val result = port.generateImage("p", anyLocalTarget(), ImageGenerationSettings())

            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrThrow().size)
            assertArrayEquals(placeholder, result.getOrThrow()[0])
        }

    private fun buildPort(
        delegate: ImageGenerationPort,
        blockPredicate: (ByteArray) -> Boolean,
        placeholder: ByteArray,
    ): ModeratingImageGenerationPort {
        val moderator =
            object : ImageModerationPort {
                override suspend fun classify(imageBytes: ByteArray): ImageModerationResult {
                    val isBlocked = blockPredicate(imageBytes)
                    return ImageModerationResult(
                        blocked = isBlocked,
                        scoresByLabel = if (isBlocked) mapOf("porn" to 0.99f) else emptyMap(),
                        blockingLabel = if (isBlocked) "porn" else null,
                        threshold = 0.5f,
                    )
                }
            }
        val placeholderProvider =
            object : BlockedImagePlaceholderProvider() {
                override fun bytes(): ByteArray = placeholder
            }
        return ModeratingImageGenerationPort(
            delegate = delegate,
            imageModerationPort = moderator,
            placeholderProvider = placeholderProvider,
        )
    }

    private fun stubDelegateReturning(images: List<ByteArray>): ImageGenerationPort {
        val delegate = RecordingImageGenerationPort()
        delegate.result = Result.success(images)
        return delegate
    }

    private fun anyRemoteTarget(): ImageGenerationTarget.Remote =
        ImageGenerationTarget.Remote(
            providerAssetId = com.browntowndev.enclave.domain.model.config.MediaProviderId("provider"),
            providerDisplayName = "Provider",
        )

    private fun anyLocalTarget(): ImageGenerationTarget.LocalStableDiffusion =
        ImageGenerationTarget.LocalStableDiffusion(
            localConfigId = com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId("sd15"),
            displayName = "Stable Diffusion 1.5",
            modelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
            modelDir = "/models",
            catalogSha256 = "sha",
            selectedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
            capabilities =
                com.browntowndev.enclave.domain.model.media.ProviderCapabilities(
                    supportedAspectRatios = listOf(com.browntowndev.enclave.domain.model.media.AspectRatio.ONE_ONE),
                    supportedImageQualities = listOf(com.browntowndev.enclave.domain.model.media.GenerationQuality.SPEED),
                    supportedImageResolutions = listOf("512x512"),
                    supportedVideoQualities = emptyList(),
                    supportedVideoResolutions = emptyList(),
                    supportedVideoDurations = emptyList(),
                    supportsVideo = false,
                ),
            deviceCapabilities =
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                ),
        )
}
