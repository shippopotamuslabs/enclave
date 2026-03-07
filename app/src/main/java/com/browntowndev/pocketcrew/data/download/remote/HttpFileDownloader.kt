package com.browntowndev.pocketcrew.data.download.remote

import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.ModelConfig
import com.browntowndev.pocketcrew.domain.port.download.FileDownloaderPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import javax.inject.Inject

class HttpFileDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val logger: LoggingPort
) : FileDownloaderPort {

    companion object {
        private const val TAG = "HttpFileDownloader"
    }

    override suspend fun downloadFile(
        config: ModelConfiguration,
        downloadUrl: String,
        targetDir: File,
        existingBytes: Long,
        progressCallback: FileDownloaderPort.ProgressCallback?
    ): FileDownloaderPort.DownloadResult = withContext(Dispatchers.IO) {
        // Log model info for debugging
        logger.info(TAG, "[DOWNLOAD_START] url=$downloadUrl, sizeBytes=${config.metadata.sizeInBytes}, existingBytes=$existingBytes filename=${config.metadata.localFileName}")

        // Use localFileName for the target file (Option 2: save as-is)
        val filename = config.metadata.localFileName
        val targetFile = File(targetDir, filename)
        val tempFile = File(targetDir, "$filename${ModelConfig.TEMP_EXTENSION}")

        val request = Request.Builder()
            .url(downloadUrl)
            .apply {
                if (existingBytes > 0) {
                    addHeader("Range", "bytes=$existingBytes-")
                }
            }
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 416) {
                logger.warning(
                    TAG,
                    "HTTP 416 for $filename: Range not satisfiable. Deleting temp files and restarting."
                )
                tempFile.delete()
                val retryRequest = Request.Builder()
                    .url(downloadUrl)
                    .build()
                return@withContext downloadWithRetry(retryRequest, config, downloadUrl, targetDir, progressCallback)
            }

            if (!response.isSuccessful) {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                logger.error(TAG, "Download failed for $filename: $errorMsg")
                throw Exception(errorMsg)
            }

            val isResuming = response.code == 206
            val actualExistingBytes = if (isResuming) existingBytes else 0L

            val body = response.body ?: run {
                logger.error(
                    TAG,
                    "Download failed for $filename: Empty response body"
                )
                throw Exception("Empty response body")
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

            // Use FileChannel with force(true) for atomic write+flush to disk
            val channel = FileChannel.open(
                tempFile.toPath(),
                if (isResuming) StandardOpenOption.CREATE else StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            )

            try {
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        val byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead)
                        channel.write(byteBuffer)
                        totalBytesRead += bytesRead

                        // Force write to physical disk (atomic write+flush)
                        channel.force(true)

                        // Report progress
                        progressCallback?.onProgress(totalBytesRead, actualTotalSize.coerceAtLeast(config.metadata.sizeInBytes))
                    }
                }
            } finally {
                channel.close()
            }

            if (totalBytesRead != config.metadata.sizeInBytes) {
                logger.error(TAG, "[MISMATCHED FILE SIZE] Download failed for ${config.metadata.localFileName}")
                logger.error(TAG, "[MISMATCHED FILE SIZE] Config Size: ${config.metadata.sizeInBytes}")
                logger.error(TAG, "[MISMATCHED FILE SIZE] Downloaded Size: $totalBytesRead")
                throw Exception("Incomplete download")
            } else {
                logger.info(TAG, "[COMPLETED DOWNLOAD] ${config.metadata.localFileName}. Size: $totalBytesRead")
            }

            // Rename temp file to target file
            if (targetFile.exists()) {
                targetFile.delete()
            }
            tempFile.renameTo(targetFile)

            FileDownloaderPort.DownloadResult(
                file = targetFile,
                bytesDownloaded = totalBytesRead,
                totalBytes = actualTotalSize.coerceAtLeast(config.metadata.sizeInBytes),
                isResumed = isResuming
            )
        }
    }

    private suspend fun downloadWithRetry(
        request: Request,
        config: ModelConfiguration,
        downloadUrl: String,
        targetDir: File,
        progressCallback: FileDownloaderPort.ProgressCallback?
    ): FileDownloaderPort.DownloadResult = withContext(Dispatchers.IO) {
        val filename = config.metadata.localFileName
        val targetFile = File(targetDir, filename)
        val tempFile = File(targetDir, "$filename${ModelConfig.TEMP_EXTENSION}")

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                logger.error(TAG, "Download retry failed for $filename: $errorMsg")
                throw Exception(errorMsg)
            }

            val body = response.body ?: run {
                logger.error(
                    TAG,
                    "Download retry failed for $filename: Empty response body"
                )
                throw Exception("Empty response body")
            }

            val serverContentLength = response.header("Content-Length")?.toLongOrNull()
            val contentRange = response.header("Content-Range")
            val contentRangeTotal = contentRange?.split("/")?.lastOrNull()?.toLongOrNull()

            val actualTotalSize = contentRangeTotal ?: serverContentLength ?: config.metadata.sizeInBytes
            logger.info(TAG, "[SIZE] Non-resume path - Using: $actualTotalSize")

            var totalBytesRead = 0L

            val channel = FileChannel.open(
                tempFile.toPath(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            )

            try {
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        val byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead)
                        channel.write(byteBuffer)
                        totalBytesRead += bytesRead

                        // Force write to physical disk
                        channel.force(true)

                        // Report progress
                        progressCallback?.onProgress(totalBytesRead, actualTotalSize)
                    }
                }
            } finally {
                channel.close()
            }

            // Rename temp file to target file
            if (targetFile.exists()) {
                targetFile.delete()
            }
            tempFile.renameTo(targetFile)

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
            val request = Request.Builder()
                .url(url)
                .head()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
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
