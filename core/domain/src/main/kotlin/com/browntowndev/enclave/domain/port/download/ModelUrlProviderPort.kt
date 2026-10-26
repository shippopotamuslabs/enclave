package com.browntowndev.enclave.domain.port.download

import com.browntowndev.enclave.domain.model.download.DownloadFileSpec

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
     * Get the URL for fetching the trusted domains whitelist configuration (trusted_domains.json).
     * This should always point to your R2 bucket.
     */
    fun getTrustedDomainsUrl(): String

    /**
     * Get the download URL for a specific file spec.
     * Uses the spec's source and huggingFaceModelName to determine the URL.
     * CLOUDFLARE_R2 specs mint a short-lived presigned URL via the
     * `mintModelDownloadUrl` Firebase callable, so this is suspend.
     * @param spec The download file spec containing remote file info and source
     * @return The full URL to download the file
     */
    suspend fun getModelDownloadUrl(spec: DownloadFileSpec): String
}
