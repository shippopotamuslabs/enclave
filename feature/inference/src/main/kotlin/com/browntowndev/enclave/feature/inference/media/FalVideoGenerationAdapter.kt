package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.feature.inference.fal.FalMediaResponseDiagnostics
import com.browntowndev.enclave.feature.inference.fal.FalOutputSchemaResolver
import com.browntowndev.enclave.feature.inference.fal.FalQueuePoller
import com.browntowndev.enclave.feature.inference.fal.FalRequest
import com.browntowndev.enclave.feature.inference.fal.FalService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import javax.inject.Inject

open class FalVideoGenerationAdapter(
    private val falService: FalService,
    private val outputSchemaResolver: FalOutputSchemaResolver,
    private val pollDelayMillis: Long,
) {
    @Inject
    constructor(
        falService: FalService,
        outputSchemaResolver: FalOutputSchemaResolver,
    ) : this(falService, outputSchemaResolver, DEFAULT_POLL_DELAY_MILLIS)

    open suspend fun generateVideo(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray? = null,
        supportsImageToVideo: Boolean = false,
    ): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                require(settings is VideoGenerationSettings) {
                    "Settings must be VideoGenerationSettings"
                }
                val videoSettings = settings
                require(!supportsImageToVideo || referenceImage != null) {
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
                                put("duration", videoSettings.falDurationValue())
                                put("aspect_ratio", videoSettings.aspectRatio.ratio)
                                put("resolution", videoSettings.falResolution())
                                if (referenceImage != null && supportsImageToVideo) {
                                    put("image_url", "data:image/png;base64,${Base64.getEncoder().encodeToString(referenceImage)}")
                                }
                            },
                    )
                val outputSchema = outputSchemaResolver.resolve(modelId = modelId, apiKey = apiKey, baseUrl = baseUrl)
                val poller = FalQueuePoller(falService, pollDelayMillis, "fal video generation")
                val result = poller.pollUntilComplete(apiKey = apiKey, request = request)
                FalMediaResponseDiagnostics.extractPayloadErrorMessage(result)?.let { message ->
                    throw IllegalStateException("fal video generation failed: $message")
                }
                val videoUrl =
                    falService.primaryVideoUrls(result, outputSchema).firstOrNull()
                        ?: falService.primaryMediaUrls(result, outputSchema).firstOrNull()
                        ?: falService.mediaUrls(result, outputSchema).firstOrNull()
                        ?: throwNoVideoUrl(
                            modelId = modelId,
                            request = request,
                            payload = result,
                            outputSchema = outputSchema,
                        )
                Result.success(falService.resolveMediaBytes(videoUrl, apiKey))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }
        }

    private companion object {
        private const val DEFAULT_POLL_DELAY_MILLIS = 2_000L
        private val FAL_VIDEO_RESOLUTIONS = setOf("720p", "1080p", "4k")

        private fun VideoGenerationSettings.falResolution(): String = videoResolution.takeIf { it in FAL_VIDEO_RESOLUTIONS } ?: "1080p"

        /** Fal video models often use string duration enums (e.g. Seedance `"6"`, `"auto"`). */
        private fun VideoGenerationSettings.falDurationValue(): String = apiDuration ?: videoDuration.toString()

        private fun throwNoVideoUrl(
            modelId: String,
            request: FalRequest,
            payload: JsonObject,
            outputSchema: ModelGenerationOutputSchema?,
        ): Nothing {
            val resultFetchUrl =
                request.responseUrl ?: FalService.deriveResponseUrl(request.statusUrl)
            FalMediaResponseDiagnostics.logVideoUrlExtractionFailure(
                modelId = modelId,
                request = request,
                payload = payload,
                outputSchema = outputSchema,
                resultFetchFailed = false,
                resultFetchUrl = resultFetchUrl,
            )
            throw IllegalStateException(
                FalMediaResponseDiagnostics.buildVideoUrlMissingMessage(
                    modelId = modelId,
                    payload = payload,
                    resultFetchFailed = false,
                    resultFetchUrl = resultFetchUrl,
                ),
            )
        }
    }
}
