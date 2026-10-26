package com.browntowndev.enclave.core.data.download.remote

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Short-lived presigned R2 model download URL minted by the backend.
 *
 * @property url The presigned S3 GetObject URL (an `r2.cloudflarestorage.com` host).
 * @property expiresAtMillis Epoch millis at which the URL expires.
 */
internal data class MintedModelDownloadUrl(
    val url: String,
    val expiresAtMillis: Long,
)

/**
 * Seam for the `mintModelDownloadUrl` Firebase callable (v1).
 *
 * Request data: `{ filePath: string }` where `filePath` is the R2 object key
 * exactly as [DynamicModelUrlProvider] builds it today for CLOUDFLARE_R2 specs
 * (path after `https://config.enclave-app.com/`).
 *
 * Response: `{ url: string, expiresAtMillis: number }`. Callable failures
 * (unauthenticated, permission-denied for non-subscribers, invalid-argument,
 * failed-precondition) propagate to the caller so the download fails visibly;
 * there is intentionally no public-R2 fallback.
 */
internal interface ModelDownloadUrlMinter {
    /**
     * Mints a presigned download URL for [filePath].
     * @throws Exception when the callable fails or the response is malformed.
     */
    suspend fun mintModelDownloadUrl(filePath: String): MintedModelDownloadUrl
}

/**
 * [ModelDownloadUrlMinter] backed by the real `mintModelDownloadUrl` callable.
 */
internal class FirebaseModelDownloadUrlMinter(
    private val functions: FirebaseFunctions,
) : ModelDownloadUrlMinter {
    override suspend fun mintModelDownloadUrl(filePath: String): MintedModelDownloadUrl {
        val result =
            functions
                .getHttpsCallable(CALLABLE_NAME)
                .call(mapOf(REQUEST_FILE_PATH to filePath))
                .await()
        val payload = result.data as? Map<*, *>
            ?: throw IllegalStateException("mintModelDownloadUrl returned a malformed response")
        val url = payload[RESPONSE_URL] as? String
            ?: throw IllegalStateException("mintModelDownloadUrl response is missing 'url'")
        require(url.isNotBlank()) { "mintModelDownloadUrl response has a blank 'url'" }
        val expiresAtMillis = (payload[RESPONSE_EXPIRES_AT_MILLIS] as? Number)?.toLong()
            ?: throw IllegalStateException("mintModelDownloadUrl response is missing 'expiresAtMillis'")
        return MintedModelDownloadUrl(url = url, expiresAtMillis = expiresAtMillis)
    }

    companion object {
        const val CALLABLE_NAME = "mintModelDownloadUrl"

        /** Presigned S3 GetObject TTL in seconds, mirroring the backend constant. */
        const val PRESIGN_TTL_SECONDS = 900

        private const val REQUEST_FILE_PATH = "filePath"
        private const val RESPONSE_URL = "url"
        private const val RESPONSE_EXPIRES_AT_MILLIS = "expiresAtMillis"
    }
}
