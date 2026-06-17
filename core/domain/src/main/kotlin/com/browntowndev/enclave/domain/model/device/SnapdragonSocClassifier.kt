package com.browntowndev.enclave.domain.model.device

/**
 * Classifies Qualcomm Snapdragon SoC tier from a lowercase device property haystack.
 */
object SnapdragonSocClassifier {
    // SM8650 = 8 Gen 3, SM8750 = 8 Elite, SM8845 = 8s Gen 3; pineapple is the 8 Gen 3 codename
    private val gen3Markers = setOf("sm8650", "sm8750", "sm8845", "pineapple")
    private val gen2Markers = setOf("sm8550", "kalama")
    private val gen1Markers = setOf("sm8450", "sm8350", "lahaina")

    fun tier(haystack: String): SnapdragonTier {
        val lower = haystack.lowercase()
        return when {
            gen3Markers.any { lower.contains(it) } -> SnapdragonTier.GEN3_PLUS
            gen2Markers.any { lower.contains(it) } -> SnapdragonTier.GEN2
            gen1Markers.any { lower.contains(it) } -> SnapdragonTier.GEN1
            else -> SnapdragonTier.UNKNOWN
        }
    }
}
