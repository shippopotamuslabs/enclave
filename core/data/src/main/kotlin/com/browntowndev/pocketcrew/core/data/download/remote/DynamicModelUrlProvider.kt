package com.browntowndev.pocketcrew.core.data.download.remote

import com.browntowndev.pocketcrew.domain.model.download.DownloadFileSpec
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Dynamic URL provider supporting multiple download sources.
 * - Config comes from R2 bucket (for your model_config.json)
 * - Model downloads come from HuggingFace or Cloudflare R2 based on spec source
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

    override fun getModelDownloadUrl(spec: DownloadFileSpec): String {
        val source = try {
            DownloadSource.valueOf(spec.source)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Unknown download source: '${spec.source}'. Valid sources: ${DownloadSource.entries.joinToString()}", e)
        }
        return when (source) {
            DownloadSource.CLOUDFLARE_R2 -> R2_BUCKET_URL.newBuilder()
                .addPathSegment(DownloadSecurity.requireSafeFileName(spec.remoteFileName, "remoteFileName"))
                .build()
                .toString()
            DownloadSource.HUGGING_FACE -> {
                val (owner, repo) = DownloadSecurity.requireHuggingFaceRepoId(spec.huggingFaceModelName)
                val fileName = DownloadSecurity.requireSafeFileName(spec.remoteFileName, "remoteFileName")

                val builder = HF_BASE_URL.newBuilder()
                    .addPathSegment(owner)
                    .addPathSegment(repo)
                    .addPathSegment("resolve")
                    .addPathSegment("main")

                spec.huggingFacePath?.takeIf { it.isNotBlank() }?.let { path ->
                    path.split('/').forEach { builder.addPathSegment(it) }
                }

                builder.addPathSegment(fileName)
                    .build()
                    .toString()
            }
        }
    }
}