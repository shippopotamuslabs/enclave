package com.browntowndev.enclave.domain.model.download

data class SdxlQnnPackVariant(
    val zipFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
) {
    val localZipFileName: String = zipFileName
}

object SdxlQnnPackCatalog {
    fun defaultVariant(): SdxlQnnPackVariant = throw NotImplementedError("TDD Red")

    fun allVariants(): List<SdxlQnnPackVariant> = throw NotImplementedError("TDD Red")

    fun findBySha256(sha256: String): SdxlQnnPackVariant? = throw NotImplementedError("TDD Red")
}
