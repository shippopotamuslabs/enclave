package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiClientProviderPort
import com.google.genai.Client
import com.google.genai.types.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

open class GoogleVideoGenerationAdapter(
    private val okHttpClient: OkHttpClient,
    private val clientProvider: GoogleGenAiClientProviderPort,
    private val pollDelayMillis: Long,
) {
    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        clientProvider: GoogleGenAiClientProviderPort,
    ) : this(okHttpClient, clientProvider, DEFAULT_POLL_DELAY_MILLIS)

    suspend fun generateVideo(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray? = null,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val videoSettings = settings as? VideoGenerationSettings
                ?: throw IllegalArgumentException("Settings must be VideoGenerationSettings")

            val client = clientProvider.getClient(apiKey, baseUrl)
            val config = GenerateVideosConfig.builder()
                .aspectRatio(videoSettings.aspectRatio.toGoogleAspectRatio())
                .durationSeconds(videoSettings.videoDuration)
                .resolution(videoSettings.videoResolution)
                .generateAudio(true)
                .build()
            val source = GenerateVideosSource.builder().prompt(prompt).build()
            val operation = generateVideos(client, modelId, source, config)
            
            val completedOp = pollSdkOperationUntilComplete(client, operation)
            val response = try {
                val method = completedOp.javaClass.methods.find { it.name == "response" }
                val optional = method?.invoke(completedOp) as? java.util.Optional<GenerateVideosResponse>
                optional?.orElse(null)
            } catch (e: Exception) {
                null
            } ?: throw IllegalStateException("Google video operation finished but returned no response")
            
            val generatedVideos = response.generatedVideos().orElse(emptyList())
            val videoUrl = generatedVideos.firstOrNull()?.let { genVideo ->
                try {
                    val videoMethod = genVideo.javaClass.methods.find { it.name == "video" }
                    val videoOptional = videoMethod?.invoke(genVideo) as? java.util.Optional<Any>
                    val videoObj = videoOptional?.orElse(null)
                    
                    val uriMethod = videoObj?.javaClass?.methods?.find { it.name == "uri" }
                    val uriOptional = uriMethod?.invoke(videoObj) as? java.util.Optional<String>
                    uriOptional?.orElse(null)
                } catch (e: Exception) {
                    null
                }
            } ?: throw IllegalStateException("No video URL returned by SDK")
            
            return@runCatching downloadVideo(videoUrl, apiKey)
        }
    }

    private fun AspectRatio.toGoogleAspectRatio(): String = when (this) {
        AspectRatio.ONE_ONE,
        AspectRatio.NINE_SIXTEEN,
        AspectRatio.SIXTEEN_NINE -> ratio
        else -> AspectRatio.SIXTEEN_NINE.ratio
    }

    protected open fun generateVideos(
        client: Client,
        modelId: String,
        source: GenerateVideosSource,
        config: GenerateVideosConfig,
    ): GenerateVideosOperation = client.models.generateVideos(modelId, source, config)

    private suspend fun pollSdkOperationUntilComplete(client: Client, operation: GenerateVideosOperation): GenerateVideosOperation {
        repeat(MAX_POLL_ATTEMPTS) {
            val ops = client.javaClass.getField("operations").get(client) as com.google.genai.Operations
            val getMethod = ops.javaClass.methods.find { it.name == "getVideosOperation" }
            val currentOp = getMethod?.invoke(ops, operation, GetOperationConfig.builder().build()) as GenerateVideosOperation
            
            val isDone = try {
                val method = currentOp.javaClass.methods.find { it.name == "done" }
                val optional = method?.invoke(currentOp) as? java.util.Optional<Boolean>
                optional?.orElse(false) ?: false
            } catch (e: Exception) {
                false
            }
            
            if (isDone) {
                val error = try {
                    val method = currentOp.javaClass.methods.find { it.name == "error" }
                    val optional = method?.invoke(currentOp) as? java.util.Optional<Any>
                    optional?.orElse(null)
                } catch (e: Exception) {
                    null
                }
                if (error != null) {
                    throw IllegalStateException("Google video generation failed: $error")
                }
                return currentOp
            }
            delay(pollDelayMillis)
        }
        throw IllegalStateException("Google video generation timed out")
    }

    protected open suspend fun downloadVideo(url: String, apiKey: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Google video download failed: ${response.code}")
            }
            return response.body?.bytes() ?: throw IllegalStateException("Empty response body")
        }
    }

    private companion object {
        const val DEFAULT_POLL_DELAY_MILLIS = 10_000L
        const val MAX_POLL_ATTEMPTS = 120
    }
}
