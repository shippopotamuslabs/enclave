package com.browntowndev.pocketcrew.app.tts

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.TtsMediaStream
import com.browntowndev.pocketcrew.domain.port.media.TtsMediaStreamFactoryPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackRegistryPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackRequest
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/**
 * Custom Media3 [DataSource] that resolves `pocketcrew-tts://play/{requestId}` URIs
 * to audio streams provided by TTS adapters (OpenAI, xAI, Google).
 *
 * When ExoPlayer opens a URI on its loading thread, this DataSource:
 * 1. Parses the requestId from the URI path
 * 2. Looks up the [TtsPlaybackRequest] in the registry
 * 3. Obtains the API key for the provider
 * 4. Creates a [TtsMediaStreamServicePort][com.browntowndev.pocketcrew.domain.port.media.TtsMediaStreamServicePort] through the factory
 * 5. Opens the stream and feeds bytes to ExoPlayer
 *
 * Note: [runBlocking] is used in [open] because this method is called by ExoPlayer
 * on its internal loading thread, NOT the UI/main thread. This is the standard
 * pattern for DataSource implementations that perform IO.
 */
class TtsDataSource(
    private val registry: TtsPlaybackRegistryPort,
    private val mediaStreamFactory: TtsMediaStreamFactoryPort,
    private val apiKeyProvider: ApiKeyProviderPort,
    private val logger: LoggingPort,
) : BaseDataSource(true) {
    private var openedDataSpec: DataSpec? = null
    private var mediaStream: TtsMediaStream? = null
    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        openedDataSpec = dataSpec
        val uri = dataSpec.uri
        logger.debug(TAG, "Opening TTS data source for URI: $uri")

        val requestId =
            extractRequestId(uri)
                ?: throw IOException("Invalid TTS URI format: $uri. Expected: pocketcrew-tts://play/{requestId}")

        val request =
            registry.resolve(requestId)
                ?: throw IOException("TTS request not found in registry: $requestId. It may have expired or been removed.")

        val apiKey =
            apiKeyProvider.getApiKey(request.credentialAlias)
                ?: throw IOException("API key not found for credential alias: ${request.credentialAlias}")

        val streamService =
            mediaStreamFactory.create(
                provider = request.provider,
                apiKey = apiKey,
                baseUrl = request.baseUrl,
            ) ?: throw IOException("No TTS media stream adapter for provider: ${request.provider}")

        transferInitializing(dataSpec)

        // Called on ExoPlayer's loading thread, not the UI thread
        mediaStream =
            try {
                runBlocking {
                    streamService.openStream(
                        text = request.text,
                        voice = request.voiceName,
                        modelId = request.modelName,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IOException("Failed to open TTS stream for provider ${request.provider}: ${e.message}", e)
            }

        val stream = mediaStream ?: throw IOException("TTS media stream was not initialized")
        inputStream = stream.stream
        opened = true

        bytesRemaining = stream.length?.takeIf { it > 0 } ?: C.LENGTH_UNSET.toLong()

        transferStarted(dataSpec)

        logger.debug(TAG, "TTS data source opened: mimeType=${stream.mimeType}, length=${stream.length}")

        return bytesRemaining
    }

    override fun getUri(): Uri? = openedDataSpec?.uri

    override fun close() {
        if (opened) {
            try {
                inputStream?.close()
            } catch (e: IOException) {
                logger.error(TAG, "Error closing TTS input stream", e)
            }
            inputStream = null
            mediaStream = null
            opened = false
            transferEnded()
        }
        openedDataSpec = null
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0

        val stream = inputStream ?: throw IOException("TTS data source is not open")

        val bytesRead = stream.read(buffer, offset, length)
        if (bytesRead == -1) {
            logger.debug(TAG, "TTS data source reached end of input")
            return C.RESULT_END_OF_INPUT
        }

        bytesRemaining =
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining - bytesRead
            } else {
                C.LENGTH_UNSET.toLong()
            }

        bytesTransferred(bytesRead)
        return bytesRead
    }

    private fun extractRequestId(uri: Uri): String? = extractRequestId(uri.toString())

    companion object {
        private const val TAG = "TtsDataSource"

        /**
         * Pure-Kotlin URI parsing for testability.
         * Extracts the request ID from a `pocketcrew-tts://play/{requestId}` URI string.
         */
        @JvmStatic
        fun extractRequestId(uriString: String?): String? {
            if (uriString == null) return null
            val prefix = "${TtsPlaybackRequest.SCHEME}://${TtsPlaybackRequest.HOST}/"
            if (!uriString.startsWith(prefix)) return null
            val id = uriString.removePrefix(prefix)
            return id.ifBlank { null }
        }
    }

    /**
     * Factory for creating [TtsDataSource] instances.
     *
     * This is injected via Hilt and set as the DataSource.Factory on ExoPlayer.Builder.
     */
    class Factory
        @Inject
        constructor(
            private val registry: TtsPlaybackRegistryPort,
            private val mediaStreamFactory: TtsMediaStreamFactoryPort,
            private val apiKeyProvider: ApiKeyProviderPort,
            private val logger: LoggingPort,
        ) : DataSource.Factory {
            override fun createDataSource(): DataSource = TtsDataSource(registry, mediaStreamFactory, apiKeyProvider, logger)
        }
}
