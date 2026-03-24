package com.browntowndev.pocketcrew.core.data.download.remote

import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * R2-based URL provider.
 * - Config comes from R2 bucket
 * - Model downloads also come from R2 bucket
 */
@Singleton
class R2ModelUrlProvider @Inject constructor() : ModelUrlProviderPort {

    companion object {
        // Cloudflare R2 bucket URL - using custom domain
        private const val R2_BUCKET_URL = "https://config.pocketcrew.app"
    }

    override fun getConfigUrl(): String = "$R2_BUCKET_URL/model_config.json"

    override fun getModelDownloadUrl(config: ModelConfiguration): String {
        return "$R2_BUCKET_URL/${config.metadata.remoteFileName}"
    }
}
