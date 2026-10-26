package com.browntowndev.enclave.core.data.download.remote

import com.browntowndev.enclave.domain.model.download.DownloadFileSpec
import com.browntowndev.enclave.domain.port.download.ModelUrlProviderPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * R2-based URL provider.
 * - Config comes from R2 bucket
 * - Model downloads also come from R2 bucket
 */
@Singleton
class R2ModelUrlProvider
    @Inject
    constructor() : ModelUrlProviderPort {
        companion object {
            // Cloudflare R2 bucket URL - using custom domain
            private const val R2_BUCKET_URL = "https://config.enclave-app.com"
        }

        override fun getConfigUrl(): String = "$R2_BUCKET_URL/model_config.json"

        override fun getTrustedDomainsUrl(): String = "$R2_BUCKET_URL/trusted_domains.json"

        override suspend fun getModelDownloadUrl(spec: DownloadFileSpec): String = "$R2_BUCKET_URL/${spec.remoteFileName}"
    }
