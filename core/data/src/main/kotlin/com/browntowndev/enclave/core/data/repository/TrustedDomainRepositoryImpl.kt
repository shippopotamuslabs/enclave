package com.browntowndev.enclave.core.data.repository

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.port.download.ModelUrlProviderPort
import com.browntowndev.enclave.domain.port.repository.TrustedDomainRepositoryPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class TrustedDomainsResponse(
    val version: Int = 1,
    val trusted_domains: List<String> = emptyList(),
    val trusted_wildcards: List<String> = emptyList(),
)

@Singleton
class TrustedDomainRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val modelUrlProvider: ModelUrlProviderPort,
) : TrustedDomainRepositoryPort {

    companion object {
        private const val TAG = "TrustedDomainRepo"
        private const val FILE_NAME = "trusted_domains.json"

        private val fallbackDomains = setOf(
            "api.openai.com",
            "generativelanguage.googleapis.com",
            "api.anthropic.com",
            "api.x.ai",
            "api.cohere.com",
            "api.cohere.ai",
            "api.perplexity.ai",
            "dashscope.aliyuncs.com",
            "dashscope-intl.aliyuncs.com",
            "api.minimax.io",
            "api.minimaxi.com"
        )

        private val fallbackWildcards = setOf(
            ".googleapis.com",
            ".openai.azure.com"
        )

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    private val mutex = Mutex()
    private var cachedDomains: Set<String>? = null
    private var cachedWildcards: Set<String>? = null

    private val cacheFile: File
        get() = File(context.filesDir, FILE_NAME)

    override suspend fun getTrustedDomains(): Set<String> = mutex.withLock {
        cachedDomains ?: loadFromCacheOrFallback().first
    }

    override suspend fun getTrustedWildcards(): Set<String> = mutex.withLock {
        cachedWildcards ?: loadFromCacheOrFallback().second
    }

    override suspend fun syncTrustedDomains(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = modelUrlProvider.getTrustedDomainsUrl()
            Log.i(TAG, "Syncing trusted domains from: $url")
            val request = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body.string()
                val data = json.decodeFromString<TrustedDomainsResponse>(body)

                mutex.withLock {
                    cachedDomains = data.trusted_domains.toSet()
                    cachedWildcards = data.trusted_wildcards.toSet()
                }

                // Cache locally
                runCatching {
                    cacheFile.writeText(body)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to write local cache file: ${e.message}", e)
                }

                Log.i(TAG, "Successfully synced ${data.trusted_domains.size} trusted domains")
            }
            Unit
        }.onFailure { e ->
            Log.e(TAG, "Failed to sync trusted domains: ${e.message}", e)
        }
    }

    private fun loadFromCacheOrFallback(): Pair<Set<String>, Set<String>> {
        if (cacheFile.exists()) {
            val loaded = runCatching {
                val text = cacheFile.readText()
                val parsed = json.decodeFromString<TrustedDomainsResponse>(text)
                val domains = parsed.trusted_domains.toSet()
                val wildcards = parsed.trusted_wildcards.toSet()
                domains to wildcards
            }.getOrNull()

            if (loaded != null) {
                cachedDomains = loaded.first
                cachedWildcards = loaded.second
                return loaded
            }
        }

        // Fallback
        cachedDomains = fallbackDomains
        cachedWildcards = fallbackWildcards
        return fallbackDomains to fallbackWildcards
    }
}
