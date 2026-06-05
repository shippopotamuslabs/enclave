package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.device.SnapdragonTier

/**
 * Hugging Face `xororz/sd-qnn` zip variant for SD 1.5 QNN weights (clip/unet/vae bins).
 * There is no published `8gen3` archive; Gen3+ devices use the `8gen2` build.
 */
data class Sd15QnnPackVariant(
    val zipFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
) {
    val localZipFileName: String = zipFileName
}

object Sd15QnnPackCatalog {
    const val HF_REPO = "xororz/sd-qnn"
    private const val STYLE = "AnythingV5_qnn2.28"

    fun variantFor(tier: SnapdragonTier): Sd15QnnPackVariant =
        when (tier) {
            SnapdragonTier.GEN3_PLUS,
            SnapdragonTier.GEN2,
            SnapdragonTier.GEN1,
            -> GEN2
            SnapdragonTier.UNKNOWN -> MIN
        }

    fun allVariants(): List<Sd15QnnPackVariant> = listOf(GEN2, MIN)

    fun findBySha256(sha256: String): Sd15QnnPackVariant? =
        allVariants().firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }

    private val GEN2 =
        Sd15QnnPackVariant(
            zipFileName = "${STYLE}_8gen2.zip",
            sha256 = "b744c6ec55cf8ce38b641e4b9a1d8d8c9c336f410dca01841be4fe52e290e33f",
            sizeInBytes = 1_057_820_237L,
        )

    private val MIN =
        Sd15QnnPackVariant(
            zipFileName = "${STYLE}_min.zip",
            sha256 = "c00225884b000fdf855ccb6a8759b477a2c732cfafd54cb86a1748ab63ff0731",
            sizeInBytes = 995_100_213L,
        )

    /** Catalog template (Gen2); [ResolveSd15QnnPackCatalogAssetUseCase] may substitute per device. */
    val catalogTemplate: Sd15QnnPackVariant = GEN2
}
