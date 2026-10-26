package com.browntowndev.enclave.core.data.download.remote

import com.browntowndev.enclave.domain.model.download.DownloadFileSpec
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.port.download.ModelUrlProviderPort
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Dynamic URL provider supporting multiple download sources.
 * - Config comes from R2 bucket (for your model_config.json)
 * - Model downloads come from HuggingFace or Cloudflare R2 based on spec source
 *
 * CLOUDFLARE_R2 specs no longer resolve to the public bucket URL: the object key
 * is built exactly as before, then exchanged for a short-lived presigned URL via
 * the `mintModelDownloadUrl` Firebase callable. Callable failures propagate so
 * the download fails visibly instead of silently falling back to public R2.
 */
class DynamicModelUrlProvider internal constructor(
    private val urlMinter: ModelDownloadUrlMinter,
) : ModelUrlProviderPort {
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

    override suspend fun getModelDownloadUrl(spec: DownloadFileSpec): String {
        val source = resolveSource(spec.source)
        return when (source) {
            DownloadSource.CLOUDFLARE_R2 -> {
                val objectKey = buildR2ObjectKey(spec)
                urlMinter.mintModelDownloadUrl(objectKey).url
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
     * Builds the R2 object key exactly as the former public bucket URL did:
     * the path after `https://config.enclave-app.com/`, i.e. the optional
     * `huggingFacePath` segments followed by the validated remote file name.
     */
    private fun buildR2ObjectKey(spec: DownloadFileSpec): String {
        val builder = R2_BUCKET_URL.newBuilder()
        spec.huggingFacePath?.takeIf { it.isNotBlank() }?.let { path ->
            path.split('/').forEach { builder.addPathSegment(it) }
        }
        val publicUrl =
            builder
                .addPathSegment(DownloadSecurity.requireSafeFileName(spec.remoteFileName, "remoteFileName"))
                .build()
                .toString()
        // R2_BUCKET_URL stringifies with a trailing slash, which is exactly the
        // separator between the host and the object key in the public URL.
        return publicUrl.removePrefix("$R2_BUCKET_URL")
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
