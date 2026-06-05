package com.browntowndev.enclave.domain.model.download

data class SdxlQnnPackVariant(
    val zipFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
) {
    val localZipFileName: String = zipFileName
}

object SdxlQnnPackCatalog {
    private val default =
        SdxlQnnPackVariant(
            zipFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
            sha256 = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83",
            sizeInBytes = 3_504_485_672L,
        )

    fun defaultVariant(): SdxlQnnPackVariant = default

    fun allVariants(): List<SdxlQnnPackVariant> = listOf(default)

    fun findBySha256(sha256: String): SdxlQnnPackVariant? =
        allVariants().firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }
}
