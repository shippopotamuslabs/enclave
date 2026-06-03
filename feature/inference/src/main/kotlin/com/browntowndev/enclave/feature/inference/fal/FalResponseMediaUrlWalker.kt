package com.browntowndev.enclave.feature.inference.fal

import com.browntowndev.enclave.domain.model.media.OutputMediaKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class FalMediaUrlCandidate(
    val url: String,
    val priority: Int,
    val index: Int,
    val key: String? = null,
    val kind: OutputMediaKind = OutputMediaKind.ANY,
)

internal object FalResponseMediaUrlWalker {
    val resultPayloadWrapperKeys = listOf("data", "output", "response", "payload", "result")

    fun unwrapResultPayload(response: JsonObject): JsonObject {
        val wrapperKey =
            resultPayloadWrapperKeys.firstOrNull { key ->
                (response[key] as? JsonObject)?.let { nested ->
                    nested.containsMediaArtifactKey() ||
                        collectUrlCandidates(nested).any { candidate -> candidate.url.isMediaUrl() }
                } == true
            }
        val nested = wrapperKey?.let { response[it] as? JsonObject }
        return if (nested != null && nested != response) unwrapResultPayload(nested) else response
    }

    fun collectUrlCandidates(root: JsonElement): List<FalMediaUrlCandidate> {
        val candidates = mutableListOf<FalMediaUrlCandidate>()

        fun visit(
            element: JsonElement,
            sequenceIndex: () -> Int,
        ) {
            when (element) {
                is JsonObject -> {
                    for ((key, value) in element.entries) {
                        when {
                            key.isKnownMediaUrlKey() && value is JsonPrimitive -> {
                                value.contentOrNull?.let { url ->
                                    if (!key.isQueueControlUrlKey()) {
                                        candidates +=
                                            FalMediaUrlCandidate(
                                                url = url,
                                                priority = key.mediaUrlPriority(),
                                                index = sequenceIndex(),
                                                key = key,
                                                kind = key.toOutputMediaKind(),
                                            )
                                    }
                                }
                            }
                            key.isKnownMediaContainerKey() && value is JsonPrimitive -> {
                                value.contentOrNull?.takeIf { it.isMediaUrl() }?.let { url ->
                                    candidates +=
                                        FalMediaUrlCandidate(
                                            url = url,
                                            priority = PRIMARY_MEDIA_URL_PRIORITY,
                                            index = sequenceIndex(),
                                            key = key,
                                            kind = key.toOutputMediaKind(),
                                        )
                                }
                            }
                            key.isVideoArtifactKey() || key.isImageArtifactKey() || key.isAudioArtifactKey() ->
                                candidates.addArtifactCandidates(key, value, sequenceIndex)
                        }
                        visit(value, sequenceIndex)
                    }
                }
                is JsonArray -> element.forEach { visit(it, sequenceIndex) }
                else -> Unit
            }
        }

        var index = 0
        visit(root) { index++ }
        return candidates
    }

    private fun JsonObject.containsMediaArtifactKey(): Boolean =
        keys.any { key ->
            key.isKnownMediaContainerKey() ||
                key.isKnownMediaUrlKey() ||
                key.equals("images", ignoreCase = true) ||
                key.equals("videos", ignoreCase = true)
        }

    private fun MutableList<FalMediaUrlCandidate>.addArtifactCandidates(
        key: String,
        value: JsonElement,
        sequenceIndex: () -> Int,
    ) {
        val urls =
            when (value) {
                is JsonObject -> listOfNotNull(value.optionalString("url")?.takeIf { it.isMediaUrl() })
                is JsonArray ->
                    value.mapNotNull { element ->
                        (element as? JsonObject)?.optionalString("url")?.takeIf { it.isMediaUrl() }
                    }
                else -> emptyList()
            }
        urls.forEach { url ->
            add(
                FalMediaUrlCandidate(
                    url = url,
                    priority = PRIMARY_MEDIA_URL_PRIORITY,
                    index = sequenceIndex(),
                    key = key,
                    kind = key.toOutputMediaKind(),
                ),
            )
        }
    }

    private fun String.isKnownMediaUrlKey(): Boolean =
        !isQueueControlUrlKey() &&
            (
                KNOWN_MEDIA_URL_KEYS.any { equals(it, ignoreCase = true) } ||
                    endsWith("_url", ignoreCase = true)
            )

    private fun String.isKnownMediaContainerKey(): Boolean = KNOWN_MEDIA_CONTAINER_KEYS.any { equals(it, ignoreCase = true) }

    private fun String.isVideoArtifactKey(): Boolean = equals("video", ignoreCase = true) || equals("videos", ignoreCase = true)

    private fun String.isImageArtifactKey(): Boolean = equals("image", ignoreCase = true) || equals("images", ignoreCase = true)

    private fun String.isAudioArtifactKey(): Boolean = equals("audio", ignoreCase = true) || equals("music", ignoreCase = true)

    private fun String.isMediaUrl(): Boolean =
        startsWith("http://") ||
            startsWith("https://") ||
            startsWith(DATA_URI_PREFIX) ||
            startsWith("//") ||
            contains("fal.media", ignoreCase = true)

    private fun String.isQueueControlUrlKey(): Boolean =
        equals("status_url", ignoreCase = true) ||
            equals("response_url", ignoreCase = true) ||
            equals("cancel_url", ignoreCase = true)

    private fun String.toOutputMediaKind(): OutputMediaKind =
        when {
            isVideoArtifactKey() -> OutputMediaKind.VIDEO
            isImageArtifactKey() -> OutputMediaKind.IMAGE
            isAudioArtifactKey() -> OutputMediaKind.AUDIO
            contains("video", ignoreCase = true) -> OutputMediaKind.VIDEO
            contains("image", ignoreCase = true) -> OutputMediaKind.IMAGE
            contains("audio", ignoreCase = true) || contains("music", ignoreCase = true) -> OutputMediaKind.AUDIO
            else -> OutputMediaKind.ANY
        }

    private fun String.mediaUrlPriority(): Int =
        when {
            equals("video_url", ignoreCase = true) -> PRIMARY_MEDIA_URL_PRIORITY
            equals("audio_url", ignoreCase = true) -> PRIMARY_MEDIA_URL_PRIORITY
            equals("music_url", ignoreCase = true) -> PRIMARY_MEDIA_URL_PRIORITY
            equals("image_url", ignoreCase = true) -> IMAGE_REFERENCE_MEDIA_URL_PRIORITY
            equals("file_url", ignoreCase = true) -> PRIMARY_MEDIA_URL_PRIORITY
            equals("url", ignoreCase = true) -> PRIMARY_MEDIA_URL_PRIORITY + 10
            contains("preview", ignoreCase = true) -> PREVIEW_MEDIA_URL_PRIORITY
            contains("thumbnail", ignoreCase = true) -> PREVIEW_MEDIA_URL_PRIORITY
            contains("poster", ignoreCase = true) -> PREVIEW_MEDIA_URL_PRIORITY
            else -> FALLBACK_MEDIA_URL_PRIORITY
        }

    private val KNOWN_MEDIA_URL_KEYS =
        listOf(
            "video_url",
            "image_url",
            "audio_url",
            "music_url",
            "file_url",
            "url",
            "href",
            "download_url",
            "output_url",
            "media_url",
            "signed_url",
        )
    private val KNOWN_MEDIA_CONTAINER_KEYS = listOf("video", "videos", "image", "images", "audio", "file", "output", "result")
    private const val DATA_URI_PREFIX = "data:"
    private const val PRIMARY_MEDIA_URL_PRIORITY = 0
    private const val IMAGE_REFERENCE_MEDIA_URL_PRIORITY = 30
    private const val FALLBACK_MEDIA_URL_PRIORITY = 50
    private const val PREVIEW_MEDIA_URL_PRIORITY = 100
}
