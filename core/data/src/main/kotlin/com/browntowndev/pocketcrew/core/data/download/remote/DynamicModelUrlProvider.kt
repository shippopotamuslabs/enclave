package com.browntowndev.pocketcrew.core.data.download.remote

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic URL provider supporting multiple download sources.
 * - Config comes from R2 bucket (for your model_config.json)
 * - Model downloads come from HuggingFace or Cloudflare R2 based on asset source
 */
@Singleton
class DynamicModelUrlProvider @Inject constructor() : ModelUrlProviderPort {

    companion object {
        private const val R2_BUCKET_URL = "https://config.pocketcrew.app"
        private const val HF_BASE_URL = "https://huggingface.co"
    }

    override fun getConfigUrl(): String = "$R2_BUCKET_URL/model_config.json"

    override fun getModelDownloadUrl(asset: LocalModelAsset): String {
        return when (asset.metadata.source) {
            DownloadSource.CLOUDFLARE_R2 -> "$R2_BUCKET_URL/${asset.metadata.remoteFileName}"
            DownloadSource.HUGGING_FACE -> {
                // Construct HuggingFace download URL from model name and filename
                // Format: https://huggingface.co/{modelName}/resolve/main/{filename}
                val modelName = asset.metadata.huggingFaceModelName
                if (modelName.isEmpty()) {
                    throw IllegalStateException("HuggingFace model name is required for HUGGING_FACE source")
                }
                val fileName = asset.metadata.remoteFileName
                "$HF_BASE_URL/$modelName/resolve/main/$fileName"
            }
        }
    }
}
