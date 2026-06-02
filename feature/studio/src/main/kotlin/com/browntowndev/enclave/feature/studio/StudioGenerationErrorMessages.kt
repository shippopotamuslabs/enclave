package com.browntowndev.enclave.feature.studio

/**
 * Maps provider/queue technical failures into short copy for Studio snackbars.
 */
internal object StudioGenerationErrorMessages {
    private const val DEFAULT_MESSAGE = "Generation failed. Try again."
    private const val MAX_SNACKBAR_CHARS = 240

    private val FAL_HTTP_ERROR =
        Regex("""fal queue result failed:\s*\d+\s+(.+)""", RegexOption.IGNORE_CASE)
    private val FAL_GENERATION_FAILED =
        Regex("""fal (?:video|image|music)?\s*generation failed:\s*(.+)""", RegexOption.IGNORE_CASE)

    fun toSnackbarMessage(raw: String?): String {
        if (raw.isNullOrBlank()) return DEFAULT_MESSAGE
        extractProviderMessage(raw)?.let { return mapProviderMessage(it) }
        return raw.truncateForSnackbar()
    }

    private fun extractProviderMessage(raw: String): String? {
        nestedParentheticalMessages(raw).forEach { candidate ->
            extractFromSegment(candidate)?.let { return it }
        }
        return extractFromSegment(raw)
    }

    private fun nestedParentheticalMessages(raw: String): List<String> =
        Regex("""\(([^()]+)\)""")
            .findAll(raw)
            .map { it.groupValues[1].trim() }
            .toList()
            .asReversed()

    private fun extractFromSegment(segment: String): String? =
        when {
            segment.contains("could not fetch queue result", ignoreCase = true) -> falHttpMessage(segment)
            else ->
                falHttpMessage(segment)
                    ?: falGenerationMessage(segment)
                    ?: segment.takeIf { isShortNonUrlSegment(it) }
        }

    private fun falHttpMessage(segment: String): String? =
        FAL_HTTP_ERROR
            .find(segment)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun falGenerationMessage(segment: String): String? =
        FAL_GENERATION_FAILED
            .find(segment)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { message ->
                !message.contains("http", ignoreCase = true) && message.length <= MAX_SNACKBAR_CHARS
            }

    private fun isShortNonUrlSegment(segment: String): Boolean =
        segment.length <= MAX_SNACKBAR_CHARS &&
            !segment.contains("http://", ignoreCase = true) &&
            !segment.contains("https://", ignoreCase = true)

    private fun mapProviderMessage(apiMessage: String): String =
        when {
            apiMessage.contains("sensitive content", ignoreCase = true) ||
                apiMessage.contains("content_policy", ignoreCase = true) ->
                "Generation blocked: the provider flagged this output for sensitive content. Try a different prompt or image."
            else -> apiMessage.truncateForSnackbar()
        }

    private fun String.truncateForSnackbar(): String =
        if (length <= MAX_SNACKBAR_CHARS) {
            this
        } else {
            take(MAX_SNACKBAR_CHARS) + "…"
        }
}
