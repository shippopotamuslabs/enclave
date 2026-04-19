package com.browntowndev.pocketcrew.domain.port.download

import com.browntowndev.pocketcrew.domain.model.download.DownloadFileSpec

/**
 * Port (interface) for providing model download URLs.
 * Abstracts the storage backend (R2, HuggingFace, S3, etc.) from the domain.
 *
 * The config URL (for model_config.json) always comes from R2 (your Cloudflare bucket).
 * Model download URLs can come from different backends based on implementation.
 */
interface ModelUrlProviderPort {
    /**
     * Get the URL for fetching the model configuration file (model_config.json).
     * This should always point to your R2 bucket.
     */
    fun getConfigUrl(): String

    /**
     * Get the download URL for a specific file spec.
     * Uses the spec's source and huggingFaceModelName to determine the URL.
     * @param spec The download file spec containing remote file info and source
     * @return The full URL to download the file
     */
    fun getModelDownloadUrl(spec: DownloadFileSpec): String
}