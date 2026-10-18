package com.browntowndev.enclave.feature.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.feature.inference.media.ImageGenerationPortImpl
import com.browntowndev.enclave.feature.inference.media.VideoGenerationPortImpl
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34], shadows = [ShadowFakeNativeLocalImageGenRuntime::class])
class MediaPortTasksRedTest {
    private val apiKeyProvider = FakeApiKeyProvider()
    private val studioRepository = RecordingStudioRepository()
    private val mediaProviderRepository = FakeMediaProviderRepository()
    private val gpuCapabilityPort = KnobbedGpuCapabilityPort(supported = true)
    private val falImageAdapter = FakeFalImageGenerationAdapter()
    private val falVideoAdapter = FakeFalVideoGenerationAdapter()
    private lateinit var imagePort: ImageGenerationPortImpl
    private lateinit var videoPort: VideoGenerationPortImpl

    @BeforeEach
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val runtime = NativeLocalImageGenRuntime(context)
        imagePort =
            ImageGenerationPortImpl(
                apiKeyProvider = apiKeyProvider,
                studioRepository = studioRepository,
                mediaProviderRepository = mediaProviderRepository,
                gpuCapabilityPort = gpuCapabilityPort,
                localStableDiffusionImageGenerator = FakeLocalStableDiffusionImageGenerator(context, runtime),
                openAiAdapter = FakeOpenAiImageGenerationAdapter(),
                googleAdapter = FakeGoogleImageGenerationAdapter(),
                xaiAdapter = FakeXaiImageGenerationAdapter(),
                falAdapter = falImageAdapter,
            )
        videoPort =
            VideoGenerationPortImpl(
                apiKeyProvider = apiKeyProvider,
                studioRepository = studioRepository,
                openAiAdapter = FakeOpenAiVideoGenerationAdapter(),
                googleAdapter = FakeGoogleVideoGenerationAdapter(),
                xaiAdapter = FakeXaiVideoGenerationAdapter(),
                falAdapter = falVideoAdapter,
            )
        apiKeyProvider.saveApiKey("alias", "key")
        studioRepository.mediaBytes["cache://reference.png"] = "png".toByteArray()
        falImageAdapter.result = Result.success(listOf("image".toByteArray()))
        falVideoAdapter.result = Result.success("video".toByteArray())
    }

    @Test
    fun `IMGPORT-01 edit-capable provider loads reference and forwards supportsEdit true`() =
        runTest {
            val settings = ImageGenerationSettings(referenceImageUri = "cache://reference.png")
            val provider = imageProvider(setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE))
            mediaProviderRepository.emitProviders(listOf(provider))
            val target = ImageGenerationTarget.Remote(provider.id, provider.displayName)

            val result = imagePort.generateImage("draw", target, settings)

            assertTrue(result.isSuccess)
            val call = falImageAdapter.calls.single()
            assertEquals("draw", call.prompt)
            assertEquals("key", call.apiKey)
            assertEquals("fal-ai/flux-pro/kontext", call.modelId)
            assertEquals(null, call.baseUrl)
            assertEquals(settings, call.settings)
            assertTrue(call.referenceImage?.contentEquals("png".toByteArray()) == true)
            assertEquals(true, call.supportsEdit)
        }

    @Test
    fun `IMGPORT-02 text-to-image-only provider with reference fails fast`() =
        runTest {
            val provider = imageProvider(setOf(MediaTask.TEXT_TO_IMAGE), modelName = "fal-ai/recraft/v3")
            mediaProviderRepository.emitProviders(listOf(provider))
            val target = ImageGenerationTarget.Remote(provider.id, provider.displayName)
            val result =
                imagePort.generateImage(
                    "draw",
                    target,
                    ImageGenerationSettings(referenceImageUri = "cache://reference.png"),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals("Model 'fal-ai/recraft/v3' does not support image editing.", result.exceptionOrNull()?.message)
            assertTrue(studioRepository.readMediaBytesCalls.isEmpty())
            assertTrue(falImageAdapter.calls.isEmpty())
        }

    @Test
    fun `IMGPORT-03 text-to-image-only provider without reference forwards supportsEdit false`() =
        runTest {
            val provider = imageProvider(setOf(MediaTask.TEXT_TO_IMAGE), modelName = "fal-ai/recraft/v3")
            mediaProviderRepository.emitProviders(listOf(provider))
            val target = ImageGenerationTarget.Remote(provider.id, provider.displayName)
            val result =
                imagePort.generateImage(
                    "draw",
                    target,
                    ImageGenerationSettings(referenceImageUri = null),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("image".toByteArray(), result.getOrThrow().single())
            val call = falImageAdapter.calls.single()
            assertEquals("draw", call.prompt)
            assertEquals("key", call.apiKey)
            assertEquals("fal-ai/recraft/v3", call.modelId)
            assertEquals(null, call.baseUrl)
            assertEquals(null, call.referenceImage)
            assertEquals(false, call.supportsEdit)
        }

    @Test
    fun `VIDPORT-01 image-to-video provider loads reference and forwards supportsImageToVideo true`() =
        runTest {
            val settings = VideoGenerationSettings(referenceImageUri = "cache://reference.png")

            val result =
                videoPort.generateVideo(
                    "animate",
                    videoProvider(setOf(MediaTask.TEXT_TO_VIDEO, MediaTask.IMAGE_TO_VIDEO)),
                    settings,
                )

            assertTrue(result.isSuccess)
            val call = falVideoAdapter.calls.single()
            assertEquals("animate", call.prompt)
            assertEquals("key", call.apiKey)
            assertEquals("fal-ai/minimax/video-01", call.modelId)
            assertEquals(null, call.baseUrl)
            assertEquals(settings, call.settings)
            assertTrue(call.referenceImage?.contentEquals("png".toByteArray()) == true)
            assertEquals(true, call.supportsImageToVideo)
        }

    @Test
    fun `VIDPORT-02 text-to-video-only provider with reference fails fast`() =
        runTest {
            val result =
                videoPort.generateVideo(
                    "animate",
                    videoProvider(setOf(MediaTask.TEXT_TO_VIDEO), modelName = "fal-ai/minimax/video-01"),
                    VideoGenerationSettings(referenceImageUri = "cache://reference.png"),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals("Model 'fal-ai/minimax/video-01' does not support image-to-video.", result.exceptionOrNull()?.message)
            assertTrue(studioRepository.readMediaBytesCalls.isEmpty())
            assertTrue(falVideoAdapter.calls.isEmpty())
        }

    @Test
    fun `VIDPORT-03 text-to-video-only provider without reference forwards supportsImageToVideo false`() =
        runTest {
            val result =
                videoPort.generateVideo(
                    "animate",
                    videoProvider(setOf(MediaTask.TEXT_TO_VIDEO), modelName = "fal-ai/minimax/video-01"),
                    VideoGenerationSettings(referenceImageUri = null),
                )

            assertTrue(result.isSuccess)
            val call = falVideoAdapter.calls.single()
            assertEquals("animate", call.prompt)
            assertEquals("key", call.apiKey)
            assertEquals("fal-ai/minimax/video-01", call.modelId)
            assertEquals(null, call.baseUrl)
            assertEquals(null, call.referenceImage)
            assertEquals(false, call.supportsImageToVideo)
        }

    private fun imageProvider(
        tasks: Set<MediaTask>,
        modelName: String = "fal-ai/flux-pro/kontext",
    ): MediaProviderAsset =
        MediaProviderAsset(
            id = MediaProviderId("image-provider"),
            displayName = modelName,
            provider = ApiProvider.FAL,
            capability = MediaCapability.IMAGE,
            modelName = modelName,
            baseUrl = null,
            credentialAlias = "alias",
            tasks = tasks,
        )

    private fun videoProvider(
        tasks: Set<MediaTask>,
        modelName: String = "fal-ai/minimax/video-01",
    ): MediaProviderAsset =
        MediaProviderAsset(
            id = MediaProviderId("video-provider"),
            displayName = modelName,
            provider = ApiProvider.FAL,
            capability = MediaCapability.VIDEO,
            modelName = modelName,
            baseUrl = null,
            credentialAlias = "alias",
            tasks = tasks,
        )
}
