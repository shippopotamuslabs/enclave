package com.browntowndev.pocketcrew.core.ui.util

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

/**
 * A custom Coil [Fetcher] that handles transparent decryption of media assets
 * downloaded from Firebase Storage.
 */
class StudioMediaFetcher(
    private val url: String,
    private val cryptoManager: CryptoManagerPort,
    private val okHttpClient: OkHttpClient,
) : Fetcher {
    override suspend fun fetch(): FetchResult? =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }

            val responseBody = response.body
            if (responseBody == null) {
                response.close()
                return@withContext null
            }
            val contentType = responseBody.contentType()?.toString()
            val encryptedBytes = responseBody.bytes()
            responseBody.close()

            // Firebase Storage payloads are encrypted with [CryptoManagerPort.encryptBytes] using default
            // (null) AAD in [MediaCloudSyncWorker]; byte-level crypto is not row-bound like Room string fields.
            val decryptionResult = cryptoManager.decryptBytes(encryptedBytes)

            if (decryptionResult.isFailure) {
                return@withContext null
            }

            val decryptedBytes = decryptionResult.getOrThrow()
            val buffer = Buffer().write(decryptedBytes)

            SourceFetchResult(
                source =
                    ImageSource(
                        source = buffer,
                        fileSystem = okio.FileSystem.SYSTEM,
                    ),
                mimeType = contentType,
                dataSource = DataSource.NETWORK,
            )
        }

    class Factory(
        private val cryptoManager: CryptoManagerPort,
        private val okHttpClient: OkHttpClient,
    ) : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val urlString = data.toString()
            // Intercept Firebase Storage media URLs which are known to be encrypted
            // The pattern matches our storage path: users/{userId}/media/{mediaId}
            if (urlString.contains("firebasestorage.googleapis.com") && urlString.contains("%2Fmedia%2F")) {
                return StudioMediaFetcher(urlString, cryptoManager, okHttpClient)
            }
            return null
        }
    }
}
