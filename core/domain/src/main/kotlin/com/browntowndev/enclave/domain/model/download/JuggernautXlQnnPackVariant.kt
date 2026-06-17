package com.browntowndev.enclave.domain.model.download

data class JuggernautXlQnnPackVariant(
    val zipFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
) {
    val localZipFileName: String = zipFileName
}

object JuggernautXlQnnPackCatalog {
    private val default =
        JuggernautXlQnnPackVariant(
            zipFileName = "juggernaut_qnn2.28_8gen3.zip",
            sha256 = "b27231823434c4e4184a61f77401227264a5308c6846c6fc628c37583e76413c",
            sizeInBytes = 3_747_687_306L,
        )

    fun defaultVariant(): JuggernautXlQnnPackVariant = default

    fun allVariants(): List<JuggernautXlQnnPackVariant> = listOf(default)

    fun findBySha256(sha256: String): JuggernautXlQnnPackVariant? =
        allVariants().firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }
}
