package com.browntowndev.pocketcrew.data.download.remote

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
        // Cloudflare R2 bucket URL
        private const val R2_BUCKET_URL = "https://pub-83e071f6f35749ed990eeb3058fc863d.r2.dev"
    }

    override fun getConfigUrl(): String = "$R2_BUCKET_URL/model_config.json"

    override fun getModelDownloadUrl(config: ModelConfiguration): String {
        return "$R2_BUCKET_URL/${config.metadata.remoteFileName}"
    }
}
