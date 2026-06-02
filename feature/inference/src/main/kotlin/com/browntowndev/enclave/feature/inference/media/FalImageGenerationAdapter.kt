package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.VisualGenerationSettings
import com.browntowndev.enclave.domain.model.media.withClampedGenerationCount
import com.browntowndev.enclave.feature.inference.fal.FalQueuePoller
import com.browntowndev.enclave.feature.inference.fal.FalService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Base64
import javax.inject.Inject

class FalImageGenerationAdapter(
    private val falService: FalService,
    private val pollDelayMillis: Long,
) {
    @Inject
    constructor(falService: FalService) : this(falService, DEFAULT_POLL_DELAY_MILLIS)

    suspend fun generateImage(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray? = null,
    ): Result<List<ByteArray>> =
        withContext(Dispatchers.IO) {
            try {
                val visualSettings =
                    settings as? VisualGenerationSettings
                        ?: throw IllegalArgumentException("Settings must be VisualGenerationSettings")
                val generationCount =
                    (settings as? ImageGenerationSettings)
                        ?.withClampedGenerationCount()
                        ?.generationCount
                        ?: 1
                val request =
                    falService.submitQueue(
                        apiKey = apiKey,
                        modelId = modelId,
                        baseUrl = baseUrl,
                        body =
                            buildJsonObject {
                                put("prompt", prompt)
                                put("num_images", generationCount)
                                put("aspect_ratio", visualSettings.aspectRatio.ratio)
                                put("output_format", "png")
                                (settings as? ImageGenerationSettings)
                                    ?.imageResolution
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { put("resolution", it) }
                                if (referenceImage != null) {
                                    val dataUri = "data:image/png;base64,${Base64.getEncoder().encodeToString(referenceImage)}"
                                    put("image_url", dataUri)
                                    putJsonObject("image") { put("url", dataUri) }
                                }
                            },
                    )
                val poller = FalQueuePoller(falService, pollDelayMillis, "fal image generation")
                val result = poller.pollUntilComplete(apiKey = apiKey, request = request)
                val urls = falService.primaryMediaUrls(result).ifEmpty { falService.mediaUrls(result) }
                if (urls.isEmpty()) {
                    throw IllegalStateException("No fal image URLs returned")
                }
                if (urls.size < generationCount) {
                    throw IllegalStateException("Expected $generationCount images from fal, but received ${urls.size}")
                }
                Result.success(urls.take(generationCount).map { url -> falService.resolveMediaBytes(url, apiKey) })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }
        }

    private companion object {
        private const val DEFAULT_POLL_DELAY_MILLIS = 5_000L
    }
}
