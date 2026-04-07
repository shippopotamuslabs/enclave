package com.browntowndev.pocketcrew.core.data.download.remote

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import java.net.URL
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
                val url = "$HF_BASE_URL/$modelName/resolve/main/$fileName"
                
                if (!isHuggingFaceUrl(url)) {
                    throw SecurityException("Invalid Hugging Face URL constructed: $url")
                }
                
                url
            }
        }
    }

    private fun isHuggingFaceUrl(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            if (!url.protocol.equals("https", ignoreCase = true)) {
                return false
            }
            val host = url.host.lowercase()
            host == "huggingface.co" || host.endsWith(".huggingface.co")
        } catch (e: Exception) {
            false
        }
    }
}
