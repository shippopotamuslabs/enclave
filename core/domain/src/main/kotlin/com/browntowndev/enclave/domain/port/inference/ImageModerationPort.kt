package com.browntowndev.enclave.domain.port.inference

/**
 * Result of classifying an image for explicit content.
 *
 * @property blocked Whether the image should be withheld from the user (a blocking label exceeded its threshold).
 * @property scoresByLabel Softmax probability per label declared by the classifier's `model_config.json`.
 *  Empty when the classifier degraded silently (e.g. load failure or malformed bytes).
 * @property blockingLabel The label that triggered the block (the highest-scoring label above threshold), or null when not blocked.
 * @property threshold The applied score threshold (informational; per-label thresholds live in [scoresByLabel]'s source config).
 */
data class ImageModerationResult(
    val blocked: Boolean,
    val scoresByLabel: Map<String, Float>,
    val blockingLabel: String?,
    val threshold: Float,
)

/**
 * Port for classifying raw image bytes for explicit/NSFW content.
 *
 * Implementations MUST run off the calling thread and degrade silently (return a non-blocking
 * [ImageModerationResult] with empty scores) on any internal failure, per `INFERENCE_RULES.md §7`.
 */
interface ImageModerationPort {
    suspend fun classify(imageBytes: ByteArray): ImageModerationResult

    companion object {
        val Dummy =
            object : ImageModerationPort {
                override suspend fun classify(imageBytes: ByteArray): ImageModerationResult =
                    ImageModerationResult(
                        blocked = false,
                        scoresByLabel = emptyMap(),
                        blockingLabel = null,
                        threshold = 0f,
                    )
            }
    }
}
