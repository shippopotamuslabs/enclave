package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.feature.inference.fal.FalOutputSchemaResolver
import com.browntowndev.enclave.feature.inference.fal.FalQueuePoller
import com.browntowndev.enclave.feature.inference.fal.FalService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class FalMusicGenerationAdapter(
    private val falService: FalService,
    private val outputSchemaResolver: FalOutputSchemaResolver,
    private val pollDelayMillis: Long,
) {
    @Inject
    constructor(
        falService: FalService,
        outputSchemaResolver: FalOutputSchemaResolver,
    ) : this(falService, outputSchemaResolver, DEFAULT_POLL_DELAY_MILLIS)

    suspend fun generateMusic(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
    ): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val musicSettings =
                    settings as? MusicGenerationSettings
                        ?: throw IllegalArgumentException("Settings must be MusicGenerationSettings")
                val request =
                    falService.submitQueue(
                        apiKey = apiKey,
                        modelId = modelId,
                        baseUrl = baseUrl,
                        body =
                            buildJsonObject {
                                put("prompt", prompt)
                                put("duration", musicSettings.duration)
                                put("tempo", musicSettings.tempo)
                            },
                    )
                val outputSchema = outputSchemaResolver.resolve(modelId = modelId, apiKey = apiKey, baseUrl = baseUrl)
                val poller = FalQueuePoller(falService, pollDelayMillis, "fal music generation")
                val result = poller.pollUntilComplete(apiKey = apiKey, request = request)
                val mediaUrl =
                    falService.primaryMediaUrls(result, outputSchema).firstOrNull()
                        ?: falService.mediaUrls(result, outputSchema).firstOrNull()
                        ?: throw IllegalStateException("No fal music URL returned")
                Result.success(falService.resolveMediaBytes(mediaUrl, apiKey))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }
        }

    private companion object {
        private const val DEFAULT_POLL_DELAY_MILLIS = 2_000L
    }
}
