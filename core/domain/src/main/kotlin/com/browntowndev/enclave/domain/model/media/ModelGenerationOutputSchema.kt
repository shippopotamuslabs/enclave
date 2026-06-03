package com.browntowndev.enclave.domain.model.media

/**
 * Normalized output paths for extracting generated media URLs from provider responses
 * (e.g. fal OpenAPI output schemas).
 */
data class ModelGenerationOutputSchema(
    val mediaUrlPaths: List<OutputMediaUrlPath> = emptyList(),
)

/**
 * JSON path from the (possibly unwrapped) result object to a media URL.
 *
 * Use [ARRAY_ANY] as a segment to iterate all elements of a JSON array.
 */
data class OutputMediaUrlPath(
    val segments: List<String>,
    val kind: OutputMediaKind = OutputMediaKind.ANY,
    val priority: Int = 0,
) {
    init {
        require(segments.isNotEmpty()) { "OutputMediaUrlPath requires at least one segment" }
    }

    companion object {
        const val ARRAY_ANY = "*"
    }
}

enum class OutputMediaKind {
    VIDEO,
    IMAGE,
    AUDIO,
    ANY,
}
