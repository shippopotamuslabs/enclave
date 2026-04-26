package com.browntowndev.pocketcrew.domain.port.media

import com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

/**
 * Request metadata for a TTS playback session, stored in an in-memory registry
 * and resolved by a custom Media3 DataSource when ExoPlayer opens the URI.
 *
 * The URI shape is `pocketcrew-tts://play/{requestId}` — no text is put in query parameters.
 */
data class TtsPlaybackRequest(
    val requestId: String,
    val text: String,
    val provider: ApiProvider,
    val voiceName: String,
    val modelName: String?,
    val baseUrl: String?,
    val credentialAlias: String,
    val audioMimeType: String = provider.toPlaybackMimeType(),
    val notificationTitle: String = "Pocket Crew",
    val notificationArtist: String = "TTS",
) {
    companion object {
        const val SCHEME = "pocketcrew-tts"
        const val HOST = "play"
        const val MIME_TYPE_MP3 = "audio/mpeg"
        const val MIME_TYPE_WAV = "audio/wav"
    }

    /** Constructs the URI string that Media3 will use to locate this request's audio stream. */
    fun toUriString(): String = "$SCHEME://$HOST/$requestId"
}

/**
 * Factory function to create a [TtsPlaybackRequest] from a [TtsProviderAsset].
 */
fun TtsProviderAsset.toPlaybackRequest(
    requestId: String,
    text: String,
): TtsPlaybackRequest = TtsPlaybackRequest(
    requestId = requestId,
    text = text,
    provider = provider,
    voiceName = voiceName,
    modelName = modelName,
    baseUrl = baseUrl,
    credentialAlias = credentialAlias,
    audioMimeType = provider.toPlaybackMimeType(),
    notificationArtist = displayName,
)

fun ApiProvider.toPlaybackMimeType(): String = when (this) {
    ApiProvider.GOOGLE -> TtsPlaybackRequest.MIME_TYPE_WAV
    else -> TtsPlaybackRequest.MIME_TYPE_MP3
}
