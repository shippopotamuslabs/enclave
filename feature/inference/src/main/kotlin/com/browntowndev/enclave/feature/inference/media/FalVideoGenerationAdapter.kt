package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.feature.inference.fal.FalQueuePoller
import com.browntowndev.enclave.feature.inference.fal.FalService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import javax.inject.Inject

class FalVideoGenerationAdapter(
    private val falService: FalService,
    private val pollDelayMillis: Long,
) {
    @Inject
    constructor(falService: FalService) : this(falService, DEFAULT_POLL_DELAY_MILLIS)

    suspend fun generateVideo(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray? = null,
    ): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                require(settings is VideoGenerationSettings) {
                    "Settings must be VideoGenerationSettings"
                }
                val videoSettings = settings
                require(!isImageToVideoModel(modelId) || referenceImage != null) {
                    "Model '$modelId' requires a reference image but none was provided."
                }
                val request =
                    falService.submitQueue(
                        apiKey = apiKey,
                        modelId = modelId,
                        baseUrl = baseUrl,
                        body =
                            buildJsonObject {
                                put("prompt", prompt)
                                videoSettings.apiDuration?.let { put("duration", it) }
                                    ?: put("duration", videoSettings.videoDuration)
                                put("aspect_ratio", videoSettings.aspectRatio.ratio)
                                put("resolution", videoSettings.falResolution())
                                if (referenceImage != null) {
                                    put("image_url", "data:image/png;base64,${Base64.getEncoder().encodeToString(referenceImage)}")
                                }
                            },
                    )
                val poller = FalQueuePoller(falService, pollDelayMillis, "fal video generation")
                val result = poller.pollUntilComplete(apiKey = apiKey, request = request)
                val videoUrl =
                    falService.primaryMediaUrls(result).firstOrNull()
                        ?: falService.mediaUrls(result).firstOrNull()
                        ?: throw IllegalStateException("No fal video URL returned")
                Result.success(falService.resolveMediaBytes(videoUrl, apiKey))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }
        }

    private companion object {
        private const val DEFAULT_POLL_DELAY_MILLIS = 5_000L
        private val FAL_VIDEO_RESOLUTIONS = setOf("720p", "1080p", "4k")

        private fun VideoGenerationSettings.falResolution(): String =
            videoResolution.takeIf { it in FAL_VIDEO_RESOLUTIONS } ?: "1080p"

        fun isImageToVideoModel(modelId: String): Boolean =
            modelId.contains("image-to-video", ignoreCase = true) ||
                modelId.contains("/i2v", ignoreCase = true) ||
                modelId.contains("img2vid", ignoreCase = true) ||
                modelId.contains("img-to-video", ignoreCase = true) ||
                modelId.contains("image2video", ignoreCase = true)
    }
}
