package com.browntowndev.enclave.feature.inference.fal

import com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema
import com.browntowndev.enclave.domain.model.media.OutputMediaKind
import com.browntowndev.enclave.domain.model.media.OutputMediaUrlPath
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object FalResponseMediaUrls {
    fun mediaUrls(
        response: JsonObject,
        outputSchema: ModelGenerationOutputSchema? = null,
    ): List<String> = rankedUrls(response, outputSchema) { true }

    fun primaryMediaUrls(
        response: JsonObject,
        outputSchema: ModelGenerationOutputSchema? = null,
    ): List<String> =
        rankedUrls(response, outputSchema) { candidate ->
            candidate.isPrimaryMediaUrl()
        }

    fun primaryVideoUrls(
        response: JsonObject,
        outputSchema: ModelGenerationOutputSchema? = null,
    ): List<String> =
        rankedUrls(response, outputSchema) { candidate ->
            candidate.isPrimaryMediaUrl() &&
                !candidate.key.equals("image_url", ignoreCase = true) &&
                candidate.kind != OutputMediaKind.IMAGE
        }

    private fun rankedUrls(
        response: JsonObject,
        outputSchema: ModelGenerationOutputSchema?,
        predicate: (FalMediaUrlCandidate) -> Boolean,
    ): List<String> =
        collectCandidates(response, outputSchema)
            .filter(predicate)
            .sortedWith(compareBy<FalMediaUrlCandidate> { it.priority }.thenBy { it.index })
            .distinctBy(FalMediaUrlCandidate::url)
            .map(FalMediaUrlCandidate::url)

    private fun collectCandidates(
        response: JsonObject,
        outputSchema: ModelGenerationOutputSchema?,
    ): List<FalMediaUrlCandidate> {
        val unwrapped = FalResponseMediaUrlWalker.unwrapResultPayload(response)
        val schemaCandidates =
            outputSchema
                ?.mediaUrlPaths
                ?.flatMap { path ->
                    listOf(unwrapped, response).flatMap { root ->
                        root.extractUrlsAtPath(path)
                    }
                }.orEmpty()
                .filter { it.url.isResolvableMediaUrl() }
        if (schemaCandidates.isNotEmpty()) {
            return schemaCandidates
        }
        val generic = FalResponseMediaUrlWalker.collectUrlCandidates(unwrapped)
        if (generic.isNotEmpty()) {
            return generic
        }
        return extractCdnUrlsFromRawJson(response.toString())
    }

    private fun JsonObject.extractUrlsAtPath(path: OutputMediaUrlPath): List<FalMediaUrlCandidate> {
        var elements: List<JsonElement> = listOf(this)
        path.segments.forEach { segment ->
            elements =
                elements.flatMap { element ->
                    when (segment) {
                        OutputMediaUrlPath.ARRAY_ANY ->
                            (element as? JsonArray)?.toList().orEmpty()
                        else -> (element as? JsonObject)?.get(segment)?.let { listOf(it) }.orEmpty()
                    }
                }
        }
        return elements.mapIndexedNotNull { index, element ->
            val url =
                (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isResolvableMediaUrl() }
                    ?: return@mapIndexedNotNull null
            FalMediaUrlCandidate(
                url = url,
                priority = path.priority,
                index = index,
                key = path.segments.lastOrNull(),
                kind = path.kind,
            )
        }
    }

    private fun extractCdnUrlsFromRawJson(rawJson: String): List<FalMediaUrlCandidate> =
        FAL_CDN_URL_REGEX
            .findAll(rawJson)
            .mapIndexed { index, match ->
                FalMediaUrlCandidate(
                    url = match.value,
                    priority = CDN_FALLBACK_PRIORITY,
                    index = index,
                    key = "regex",
                    kind = OutputMediaKind.ANY,
                )
            }.toList()

    private fun FalMediaUrlCandidate.isPrimaryMediaUrl(): Boolean = url.isResolvableMediaUrl() && priority < PREVIEW_MEDIA_URL_PRIORITY

    private fun String.isResolvableMediaUrl(): Boolean =
        startsWith("http://") ||
            startsWith("https://") ||
            startsWith("data:") ||
            startsWith("//") ||
            contains("fal.media", ignoreCase = true)

    private val FAL_CDN_URL_REGEX =
        Regex("""https?://(?:[a-z0-9.-]*\.)?fal\.media/[^\s"'\\]+""", RegexOption.IGNORE_CASE)
    private const val PREVIEW_MEDIA_URL_PRIORITY = 100
    private const val CDN_FALLBACK_PRIORITY = 200
}
