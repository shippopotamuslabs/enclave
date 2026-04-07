package com.browntowndev.pocketcrew.domain.model.download

/**
 * Configuration for model downloads.
 * Models are now fetched dynamically from R2 via model_config.json.
 */
object ModelConfig {
    // Cloudflare R2 bucket URL - using public R2.dev URL from dashboard
    const val R2_BUCKET_URL = "https://config.pocketcrew.app"

    // Storage requirements
    const val REQUIRED_FREE_SPACE_BYTES = 15L * 1024 * 1024 * 1024 // 15 GB
    const val CONCURRENT_DOWNLOADS = 3
    const val MAX_RETRY_ATTEMPTS = 3

    // Directory names
    const val MODELS_DIR = "models"
    const val TEMP_EXTENSION = ".tmp"
    const val TEMP_META_EXTENSION = ".tmp.meta"

    /**
     * WorkManager tag for model download work
     */
    const val WORK_TAG = "model_download"
}
