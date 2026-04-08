package com.browntowndev.pocketcrew.core.data.download.remote

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Dynamic URL provider supporting multiple download sources.
 * - Config comes from R2 bucket (for your model_config.json)
 * - Model downloads come from HuggingFace or Cloudflare R2 based on asset source
 */
@Singleton
class DynamicModelUrlProvider @Inject constructor() : ModelUrlProviderPort {

    companion object {
        private val R2_BUCKET_URL = "https://config.pocketcrew.app".toHttpUrl()
        private val HF_BASE_URL = "https://huggingface.co".toHttpUrl()
    }

    override fun getConfigUrl(): String = R2_BUCKET_URL.newBuilder()
        .addPathSegment("model_config.json")
        .build()
        .toString()

    override fun getModelDownloadUrl(asset: LocalModelAsset): String {
        return when (asset.metadata.source) {
            DownloadSource.CLOUDFLARE_R2 -> R2_BUCKET_URL.newBuilder()
                .addPathSegment(DownloadSecurity.requireSafeFileName(asset.metadata.remoteFileName, "remoteFileName"))
                .build()
                .toString()
            DownloadSource.HUGGING_FACE -> {
                val (owner, repo) = DownloadSecurity.requireHuggingFaceRepoId(asset.metadata.huggingFaceModelName)
                val fileName = DownloadSecurity.requireSafeFileName(asset.metadata.remoteFileName, "remoteFileName")

                HF_BASE_URL.newBuilder()
                    .addPathSegment(owner)
                    .addPathSegment(repo)
                    .addPathSegment("resolve")
                    .addPathSegment("main")
                    .addPathSegment(fileName)
                    .build()
                    .toString()
            }
        }
    }
}
