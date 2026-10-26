package com.browntowndev.enclave.core.data.update

import com.browntowndev.enclave.domain.model.update.LatestRelease
import com.browntowndev.enclave.domain.port.update.UpdateMetadataPort
import com.browntowndev.enclave.domain.qualifier.DownloadHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class LatestReleaseJson(
    val versionCode: Int,
    val versionName: String,
    val apkFileName: String,
)

/**
 * [UpdateMetadataPort] over the stable GitHub Releases `latest.json` document
 * (ENC-248 contract, ENC-250).
 *
 * Failure-quiet by contract: any exception, non-2xx status, malformed JSON, or
 * missing field yields `null`, never a throw.
 */
@Singleton
class GitHubLatestReleaseClient
    @Inject
    constructor(
        @param:DownloadHttpClient private val okHttpClient: OkHttpClient,
    ) : UpdateMetadataPort {
        companion object {
            const val LATEST_JSON_URL =
                "https://github.com/shippopotamuslabs/enclave/releases/latest/download/latest.json"
            const val DOWNLOAD_BASE_URL =
                "https://github.com/shippopotamuslabs/enclave/releases/latest/download/"
            private val json =
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
        }

        override suspend fun fetchLatestRelease(): LatestRelease? =
            withContext(Dispatchers.IO) {
                runCatching { fetchAndParse() }.getOrNull()
            }

        /**
         * The file name is concatenated onto [DOWNLOAD_BASE_URL], so it must be
         * a plain `.apk` file name — never a path, URL, or traversal payload.
         */
        private fun isValidApkFileName(name: String): Boolean {
            if (name.isBlank()) return false
            if (!name.endsWith(".apk", ignoreCase = true)) return false
            if (name.startsWith(".")) return false
            if ('/' in name || '\\' in name) return false
            if (".." in name) return false
            if ("://" in name) return false
            if (name.any { it.isWhitespace() || it.isISOControl() }) return false
            return true
        }

        private fun fetchAndParse(): LatestRelease? {
            val request =
                Request
                    .Builder()
                    .url(LATEST_JSON_URL)
                    .header("Cache-Control", "no-cache")
                    .build()
            return okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                val body = response.body?.string() ?: return null
                val payload = json.decodeFromString<LatestReleaseJson>(body)
                require(payload.versionCode > 0) { "versionCode must be positive" }
                require(payload.versionName.isNotBlank()) { "versionName must not be blank" }
                require(isValidApkFileName(payload.apkFileName)) { "apkFileName is not a plain .apk file name" }
                LatestRelease(
                    versionCode = payload.versionCode,
                    versionName = payload.versionName,
                    apkFileName = payload.apkFileName,
                    downloadUrl = DOWNLOAD_BASE_URL + payload.apkFileName,
                )
            }
        }
    }
