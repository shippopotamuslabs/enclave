package com.browntowndev.pocketcrew.core.data.download.remote

import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.port.download.FileDownloaderPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject

class HttpFileDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val logger: LoggingPort
) : FileDownloaderPort {

    companion object {
        private const val TAG = "HttpFileDownloader"

        // Sync to disk every 64MB to balance durability with download performance.
        // Per-chunk fsync (every 8KB) caused 20-30x slowdown due to blocking I/O.
        private const val SYNC_INTERVAL_BYTES = 64L * 1024 * 1024
    }

    override suspend fun downloadFile(
        config: FileDownloaderPort.FileDownloadConfig,
        downloadUrl: String,
        targetDir: File,
        existingBytes: Long,
        progressCallback: FileDownloaderPort.ProgressCallback?
    ): FileDownloaderPort.DownloadResult = withContext(Dispatchers.IO) {
        // Log model info for debugging
        logger.info(TAG, "[DOWNLOAD_START] url=$downloadUrl, sizeBytes=${config.expectedSizeBytes}, existingBytes=$existingBytes filename=${config.filename}")

        val requestUrl = DownloadSecurity.requireTrustedDownloadUrl(downloadUrl)
        val filename = DownloadSecurity.requireSafeFileName(config.filename)
        val downloadPaths = DownloadSecurity.resolveDownloadPaths(targetDir, filename)
        val targetFile = downloadPaths.targetFile
        val tempFile = downloadPaths.tempFile
        val metaFile = downloadPaths.metaFile

        metaFile.writeText("${config.expectedSizeBytes}\n${config.expectedSha256}")

        val request = Request.Builder()
            .url(requestUrl)
            .apply {
                if (existingBytes > 0) {
                    addHeader("Range", "bytes=$existingBytes-")
                }
            }
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.priorResponse?.isRedirect == true) {
                DownloadSecurity.requireTrustedRedirect(requestUrl, response.request.url)
            }

            if (response.code == 416) {
                logger.warning(
                    TAG,
                    "HTTP 416 for $filename: Range not satisfiable. Deleting temp files and restarting."
                )
                tempFile.delete()
                metaFile.delete()
                val retryRequest = Request.Builder()
                    .url(requestUrl)
                    .build()
                return@withContext downloadWithRetry(retryRequest, config, targetDir, progressCallback)
            }

            if (!response.isSuccessful) {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                logger.error(TAG, "Download failed for $filename: $errorMsg")
                throw Exception(errorMsg)
            }

            val isResuming = response.code == 206
            val actualExistingBytes = if (isResuming) existingBytes else 0L

            val body = response.body ?: run {
                logger.error(TAG, "Download failed for $filename: Empty response body")
                throw IOException("Empty response body")
            }

            // Use Content-Length when no resume (existingBytes == 0)
            // Use Content-Range total when resuming (existingBytes > 0)
            val serverContentLength = response.header("Content-Length")?.toLongOrNull()
            val contentRange = response.header("Content-Range")
            val contentRangeTotal = contentRange?.split("/")?.lastOrNull()?.toLongOrNull()

            val actualTotalSize = contentRangeTotal ?: serverContentLength ?: throw Exception("No Content-Length or Content-Range found")
            logger.info(TAG, "[SIZE] Server Content-Length: $serverContentLength bytes")
            logger.info(TAG, "[SIZE] Using $actualTotalSize bytes for progress")

            var totalBytesRead = actualExistingBytes

            val digest = MessageDigest.getInstance("SHA-256")

            // If resuming, seed the digest with existing bytes from the temp file
            if (isResuming && actualExistingBytes > 0 && tempFile.exists()) {
                tempFile.inputStream().use { fis ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (totalRead < actualExistingBytes) {
                        val toRead = minOf(buffer.size.toLong(), actualExistingBytes - totalRead).toInt()
                        bytesRead = fis.read(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        digest.update(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                }
            }

            // Use FileChannel with force(true) for atomic write+flush to disk
            val channel = FileChannel.open(
                tempFile.toPath(),
                if (isResuming) StandardOpenOption.CREATE else StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            )

            try {
                DigestInputStream(body.byteStream(), digest).use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var bytesSinceLastSync = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        val byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead)
                        channel.write(byteBuffer)
                        totalBytesRead += bytesRead
                        bytesSinceLastSync += bytesRead

                        // Periodic sync to disk (every 64MB) for durability
                        if (bytesSinceLastSync >= SYNC_INTERVAL_BYTES) {
                            channel.force(true)
                            bytesSinceLastSync = 0L
                        }

                        // Report progress
                        progressCallback?.onProgress(totalBytesRead, actualTotalSize.coerceAtLeast(config.expectedSizeBytes))
                    }
                }

                // Final sync before hash verification to ensure data is durable
                channel.force(true)
            } finally {
                channel.close()
            }

            // Verify SHA-256 hash after download completes
            val computedHash = digest.digest().joinToString("") { "%02x".format(it) }
            val expectedHash = config.expectedSha256.lowercase()
            if (computedHash != expectedHash) {
                logger.error(TAG, "SHA-256 mismatch for ${config.filename}. Expected: $expectedHash, Got: $computedHash")
                tempFile.delete()
                metaFile.delete()
                throw IOException("SHA-256 hash mismatch after download")
            }
            logger.info(TAG, "SHA-256 verified for ${config.filename}")

            if (totalBytesRead != config.expectedSizeBytes) {
                logger.error(TAG, "[MISMATCHED FILE SIZE] Download failed for ${config.filename}")
                logger.error(TAG, "[MISMATCHED FILE SIZE] Config Size: ${config.expectedSizeBytes}")
                logger.error(TAG, "[MISMATCHED FILE SIZE] Downloaded Size: $totalBytesRead")
                throw Exception("Incomplete download")
            } else {
                logger.info(TAG, "[COMPLETED DOWNLOAD] ${config.filename}. Size: $totalBytesRead")
            }

            // Rename temp file to target file
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renameSucceeded = tempFile.renameTo(targetFile)
            if (!renameSucceeded) {
                logger.error(TAG, "Failed to rename temp file to target for ${config.filename}")
                // If rename fails, throw exception since we can't return a valid file
                if (tempFile.exists()) {
                    throw Exception("Failed to rename downloaded file to target location")
                }
            } else {
                metaFile.delete()
            }

            FileDownloaderPort.DownloadResult(
                file = targetFile,
                bytesDownloaded = totalBytesRead,
                totalBytes = actualTotalSize.coerceAtLeast(config.expectedSizeBytes),
                isResumed = isResuming
            )
        }
    }

    private suspend fun downloadWithRetry(
        request: Request,
        config: FileDownloaderPort.FileDownloadConfig,
        targetDir: File,
        progressCallback: FileDownloaderPort.ProgressCallback?
    ): FileDownloaderPort.DownloadResult = withContext(Dispatchers.IO) {
        val filename = DownloadSecurity.requireSafeFileName(config.filename)
        val downloadPaths = DownloadSecurity.resolveDownloadPaths(targetDir, filename)
        val targetFile = downloadPaths.targetFile
        val tempFile = downloadPaths.tempFile
        val metaFile = downloadPaths.metaFile

        metaFile.writeText("${config.expectedSizeBytes}\n${config.expectedSha256}")

        okHttpClient.newCall(request).execute().use { response ->
            if (response.priorResponse?.isRedirect == true) {
                DownloadSecurity.requireTrustedRedirect(request.url, response.request.url)
            }

            if (!response.isSuccessful) {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                logger.error(TAG, "Download retry failed for $filename: $errorMsg")
                throw Exception(errorMsg)
            }

            val body = response.body ?: run {
                logger.error(TAG, "Download retry failed for $filename: Empty response body")
                throw IOException("Empty response body")
            }

            val serverContentLength = response.header("Content-Length")?.toLongOrNull()
            val contentRange = response.header("Content-Range")
            val contentRangeTotal = contentRange?.split("/")?.lastOrNull()?.toLongOrNull()

            val actualTotalSize = contentRangeTotal ?: serverContentLength ?: config.expectedSizeBytes
            logger.info(TAG, "[SIZE] Non-resume path - Using: $actualTotalSize")

            var totalBytesRead = 0L
            val digest = MessageDigest.getInstance("SHA-256")

            val channel = FileChannel.open(
                tempFile.toPath(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            )

            try {
                DigestInputStream(body.byteStream(), digest).use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var bytesSinceLastSync = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        val byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead)
                        channel.write(byteBuffer)
                        totalBytesRead += bytesRead
                        bytesSinceLastSync += bytesRead

                        // Periodic sync to disk (every 64MB) for durability
                        if (bytesSinceLastSync >= SYNC_INTERVAL_BYTES) {
                            channel.force(true)
                            bytesSinceLastSync = 0L
                        }

                        // Report progress
                        progressCallback?.onProgress(totalBytesRead, actualTotalSize)
                    }
                }

                // Final sync before hash verification to ensure data is durable
                channel.force(true)
            } finally {
                channel.close()
            }

            // Verify SHA-256 hash after download completes
            val computedHash = digest.digest().joinToString("") { "%02x".format(it) }
            val expectedHash = config.expectedSha256.lowercase()
            if (computedHash != expectedHash) {
                logger.error(TAG, "SHA-256 mismatch for ${config.filename}. Expected: $expectedHash, Got: $computedHash")
                tempFile.delete()
                metaFile.delete()
                throw IOException("SHA-256 hash mismatch after download")
            }
            logger.info(TAG, "SHA-256 verified for ${config.filename}")

            // Rename temp file to target file
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renameSucceeded = tempFile.renameTo(targetFile)
            if (!renameSucceeded) {
                logger.error(TAG, "Failed to rename temp file to target for ${config.filename}")
                // If rename fails, throw exception since we can't return a valid file
                if (tempFile.exists()) {
                    throw Exception("Failed to rename downloaded file to target location")
                }
            } else {
                metaFile.delete()
            }

            FileDownloaderPort.DownloadResult(
                file = targetFile,
                bytesDownloaded = totalBytesRead,
                totalBytes = actualTotalSize,
                isResumed = false
            )
        }
    }

    override suspend fun getServerFileSize(url: String): Long? = withContext(Dispatchers.IO) {
        try {
            val requestUrl = DownloadSecurity.requireTrustedDownloadUrl(url)
            val request = Request.Builder()
                .url(requestUrl)
                .head()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.priorResponse?.isRedirect == true) {
                    DownloadSecurity.requireTrustedRedirect(requestUrl, response.request.url)
                }
                if (response.isSuccessful) {
                    response.header("Content-Length")?.toLongOrNull()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
