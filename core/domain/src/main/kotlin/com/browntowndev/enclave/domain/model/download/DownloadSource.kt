package com.browntowndev.enclave.domain.model.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Enum representing the source of model file downloads.
 * - HUGGING_FACE: Download from Hugging Face resolve URL.
 * - CLOUDFLARE_R2: Download from Cloudflare R2 bucket.
 */
@Serializable
enum class DownloadSource(
    val sourceName: String,
) {
    @SerialName("HF")
    HUGGING_FACE("HF"),

    @SerialName("R2")
    CLOUDFLARE_R2("R2"),
    ;

    companion object {
        fun fromSourceName(name: String?): DownloadSource =
            entries.find { it.sourceName.equals(name, ignoreCase = true) }
                ?: HUGGING_FACE
    }
}
