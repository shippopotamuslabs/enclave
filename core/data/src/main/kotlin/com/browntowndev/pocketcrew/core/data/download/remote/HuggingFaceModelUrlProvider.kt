package com.browntowndev.pocketcrew.core.data.download.remote

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HuggingFace-based URL provider.
 * - Config comes from R2 bucket (for your model_config.json)
 * - Model downloads come from HuggingFace
 *
 * The HuggingFace model name is stored in the remote config and used to construct
 * the download URL in the format: https://huggingface.co/{modelName}/resolve/main/{filename}
 */
@Singleton
class HuggingFaceModelUrlProvider @Inject constructor() : ModelUrlProviderPort {

    companion object {
        private const val R2_BUCKET_URL = "https://config.pocketcrew.app"
        private const val HF_BASE_URL = "https://huggingface.co"
    }

    override fun getConfigUrl(): String = "$R2_BUCKET_URL/model_config.json"

    override fun getModelDownloadUrl(asset: LocalModelAsset): String {
        // Construct HuggingFace download URL from model name and filename
        // Format: https://huggingface.co/{modelName}/resolve/main/{filename}
        val modelName = asset.metadata.huggingFaceModelName
        val fileName = asset.metadata.remoteFileName
        return "$HF_BASE_URL/$modelName/resolve/main/$fileName"
    }
}
