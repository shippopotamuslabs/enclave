package com.browntowndev.enclave.core.data.download.remote

import com.browntowndev.enclave.domain.model.download.DownloadFileSpec
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.port.download.ModelUrlProviderPort
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic URL provider supporting multiple download sources.
 * - Config comes from R2 bucket (for your model_config.json)
 * - Model downloads come from HuggingFace or Cloudflare R2 based on spec source
 */
@Singleton
class DynamicModelUrlProvider
    @Inject
    constructor() : ModelUrlProviderPort {
        companion object {
            private val R2_BUCKET_URL = "https://config.enclave-app.com".toHttpUrl()
            private val HF_BASE_URL = "https://huggingface.co".toHttpUrl()
        }

        override fun getConfigUrl(): String =
            R2_BUCKET_URL
                .newBuilder()
                .addPathSegment("model_config.json")
                .build()
                .toString()

        override fun getTrustedDomainsUrl(): String =
            R2_BUCKET_URL
                .newBuilder()
                .addPathSegment("trusted_domains.json")
                .build()
                .toString()

        override fun getModelDownloadUrl(spec: DownloadFileSpec): String {
            val source = resolveSource(spec.source)
            return when (source) {
                DownloadSource.CLOUDFLARE_R2 -> {
                    val builder = R2_BUCKET_URL.newBuilder()
                    spec.huggingFacePath?.takeIf { it.isNotBlank() }?.let { path ->
                        path.split('/').forEach { builder.addPathSegment(it) }
                    }
                    builder
                        .addPathSegment(DownloadSecurity.requireSafeFileName(spec.remoteFileName, "remoteFileName"))
                        .build()
                        .toString()
                }
                DownloadSource.HUGGING_FACE -> {
                    val (owner, repo) = DownloadSecurity.requireHuggingFaceRepoId(spec.huggingFaceModelName)
                    val fileName = DownloadSecurity.requireSafeFileName(spec.remoteFileName, "remoteFileName")

                    val builder =
                        HF_BASE_URL
                            .newBuilder()
                            .addPathSegment(owner)
                            .addPathSegment(repo)
                            .addPathSegment("resolve")
                            .addPathSegment("main")

                    spec.huggingFacePath?.takeIf { it.isNotBlank() }?.let { path ->
                        path.split('/').forEach { builder.addPathSegment(it) }
                    }

                    builder
                        .addPathSegment(fileName)
                        .build()
                        .toString()
                }
            }
        }

        /**
         * Resolves a catalog source token into a [DownloadSource].
         *
         * Accepts both the enum name (`HUGGING_FACE`, `CLOUDFLARE_R2`) used by persisted
         * metadata and the short catalog token (`HF`, `R2`) used directly in `model_config.json`.
         */
        private fun resolveSource(raw: String): DownloadSource =
            runCatching { DownloadSource.valueOf(raw) }
                .getOrNull()
                ?: DownloadSource.entries.firstOrNull { it.sourceName.equals(raw, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Unknown download source: '$raw'. " +
                        "Valid sources: ${DownloadSource.entries.joinToString { "${it.name}/${it.sourceName}" }}",
                )
    }
