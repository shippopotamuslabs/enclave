package com.browntowndev.pocketcrew.domain.model.download

/**
 * Enum representing the source of model file downloads.
 * - HUGGING_FACE: Download from Hugging Face resolve URL.
 * - CLOUDFLARE_R2: Download from Cloudflare R2 bucket.
 */
enum class DownloadSource(val sourceName: String) {
    HUGGING_FACE("HF"),
    CLOUDFLARE_R2("R2");

    companion object {
        fun fromSourceName(name: String?): DownloadSource {
            return entries.find { it.sourceName.equals(name, ignoreCase = true) }
                ?: HUGGING_FACE
        }
    }
}
