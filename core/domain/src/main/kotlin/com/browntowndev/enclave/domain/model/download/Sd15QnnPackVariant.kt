package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.device.SnapdragonTier

/**
 * Hugging Face zip variant for SD 1.5 image-generation weights. NPU (QNN) variants come from
 * `xororz/sd-qnn`; the CPU (MNN) variant comes from `xororz/sd-mnn`.
 * There is no published `8gen3` archive; Gen3+ devices use the `8gen2` build.
 */
data class Sd15QnnPackVariant(
    val zipFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
    val hfRepo: String = Sd15QnnPackCatalog.HF_REPO,
    /** True when the zip holds the MNN CPU pipeline (clip_v2/unet/vae `.mnn` graphs). */
    val isCpuMnn: Boolean = false,
) {
    val localZipFileName: String = zipFileName
}

object Sd15QnnPackCatalog {
    const val HF_REPO = "xororz/sd-qnn"
    const val HF_CPU_REPO = "xororz/sd-mnn"
    private const val STYLE = "AnythingV5_qnn2.28"

    /**
     * The QNN packs ship Hexagon V73 graphs (Snapdragon 8 Gen 2+). Older tiers cannot run them
     * on the DSP, so they download the MNN CPU pack instead.
     */
    fun variantFor(tier: SnapdragonTier): Sd15QnnPackVariant =
        when (tier) {
            SnapdragonTier.GEN3_PLUS,
            SnapdragonTier.GEN2,
            -> GEN2
            SnapdragonTier.GEN1,
            SnapdragonTier.UNKNOWN,
            -> CPU_MNN
        }

    fun allVariants(): List<Sd15QnnPackVariant> = listOf(GEN2, MIN, CPU_MNN)

    fun findBySha256(sha256: String): Sd15QnnPackVariant? =
        allVariants().firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }

    /** Resolves the variant for a downloaded asset by sha, falling back to the zip name. */
    fun variantForAsset(
        sha256: String,
        zipFileName: String,
    ): Sd15QnnPackVariant? =
        findBySha256(sha256)
            ?: allVariants().firstOrNull { it.zipFileName.equals(zipFileName, ignoreCase = true) }

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

    private val CPU_MNN =
        Sd15QnnPackVariant(
            zipFileName = "AnythingV5.zip",
            sha256 = "6183835e6b3f438a743ee16d4f929cb8bd2f20e50dc56d1a8eab0185e0734bbf",
            sizeInBytes = 1_191_044_427L,
            hfRepo = HF_CPU_REPO,
            isCpuMnn = true,
        )

    /** Catalog template (Gen2); [ResolveSd15QnnPackCatalogAssetUseCase] may substitute per device. */
    val catalogTemplate: Sd15QnnPackVariant = GEN2
}
