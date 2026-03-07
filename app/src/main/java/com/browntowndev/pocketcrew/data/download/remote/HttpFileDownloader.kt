package com.browntowndev.pocketcrew.data.download.remote

import com.browntowndev.pocketcrew.domain.model.DownloadWorkerModelFile
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

    // Data class to hold target and temp file pairs for multi-file downloads
    private data class DownloadedFile(
        val targetFile: File,
        val tempFile: File
    )

    override suspend fun downloadFile(
        model: DownloadWorkerModelFile,
        targetDir: File,
        existingBytes: Long,
        progressCallback: FileDownloaderPort.ProgressCallback?
    ): FileDownloaderPort.DownloadResult = withContext(Dispatchers.IO) {
        // Log model info for debugging
        logger.info(TAG, "[DOWNLOAD_START] url=${model.url}, sizeBytes=${model.sizeBytes}, existingBytes=$existingBytes filenames=${model.filenames}")
        val downloadFiles = model.filenames.map { filename ->
            DownloadedFile(
                targetFile = File(targetDir, filename),
                tempFile = File(targetDir, "$filename${ModelConfig.TEMP_EXTENSION}")
            )
        }

        val targetFile = downloadFiles.first().targetFile
        val tempFile = downloadFiles.first().tempFile

        val request = Request.Builder()
            .url(model.url)
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
                    "HTTP 416 for ${model.filenames.first()}: Range not satisfiable. Deleting temp files and restarting."
                )
                tempFile.delete()
                val retryRequest = Request.Builder()
                    .url(model.url)
                    .build()
                return@withContext downloadWithRetry(retryRequest, model, targetDir, progressCallback)
            }

            if (!response.isSuccessful) {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                logger.error(TAG, "Download failed for ${model.filenames.first()}: $errorMsg")
                throw Exception(errorMsg)
            }

            val isResuming = response.code == 206
            val actualExistingBytes = if (isResuming) existingBytes else 0L

            val body = response.body ?: run {
                logger.error(
                    TAG,
                    "Download failed for ${model.filenames.first()}: Empty response body"
                )
                throw Exception("Empty response body")
            }

            // Use Content-Length when no resume (existingBytes == 0)
            // Use Content-Range total when resuming (existingBytes > 0)
            // Content-Range format: "bytes start-end/total" - split by "/" to get total
            val serverContentLength = response.header("Content-Length")?.toLongOrNull()
            val contentRange = response.header("Content-Range")
            val contentRangeTotal = contentRange?.split("/")?.lastOrNull()?.toLongOrNull()
            
            // If resuming (Content-Range present), use that total. Otherwise use Content-Length.
            val actualTotalSize = contentRangeTotal ?: serverContentLength ?: throw Exception("No Content-Length or Content-Range found")
            logger.info(TAG, "[SIZE] Server Content-Length: $serverContentLength bytes")
            logger.info(TAG, "[SIZE] Server Content-Range: $contentRange")
            logger.info(TAG, "[SIZE] Content-Range total (from /): $contentRangeTotal bytes")
            logger.info(TAG, "[SIZE] Using $actualTotalSize bytes for progress")

            var totalBytesRead = actualExistingBytes
            
            // Use FileChannel with force(true) for atomic write+flush to disk
            // This prevents temp file corruption when worker is killed (e.g., permission dialog)
            val channels = downloadFiles.map { file ->
                FileChannel.open(
                    file.tempFile.toPath(),
                    if (isResuming) StandardOpenOption.CREATE else StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
                )
            }
            
            try {
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        // Write the same data to all temp files simultaneously
                        val byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead)
                        channels.forEach { it.write(byteBuffer.duplicate()) }
                        totalBytesRead += bytesRead

                        // Force write to physical disk (atomic write+flush)
                        // This ensures data is persisted even if worker is killed
                        channels.forEach { it.force(true) }

                        // Report progress
                        progressCallback?.onProgress(totalBytesRead, actualTotalSize.coerceAtLeast(model.sizeBytes))
                    }
                }
            } finally {
                channels.forEach { it.close() }
            }

            if (totalBytesRead != model.sizeBytes) {
                logger.error(TAG, "[MISMATCHED FILE SIZE] Download failed for ${model.originalFileName}")
                logger.error(TAG, "[MISMATCHED FILE SIZE] Config Size: ${model.sizeBytes}")
                logger.error(TAG, "[MISMATCHED FILE SIZE] Downloaded Size: $totalBytesRead")
                throw Exception("Incomplete download")
            } else {
                logger.info(TAG, "[COMPLETED DOWNLOAD] ${model.originalFileName}. Size: $totalBytesRead")
            }

            // Rename each temp file to target file
            for (downloadFile in downloadFiles) {
                if (downloadFile.targetFile.exists()) {
                    downloadFile.targetFile.delete()
                }
                downloadFile.tempFile.renameTo(downloadFile.targetFile)
            }

            FileDownloaderPort.DownloadResult(
                file = targetFile,
                bytesDownloaded = totalBytesRead,
                totalBytes = actualTotalSize.coerceAtLeast(model.sizeBytes),
                isResumed = isResuming
            )
        }
    }

    private suspend fun downloadWithRetry(
        request: Request,
        model: DownloadWorkerModelFile,
        targetDir: File,
        progressCallback: FileDownloaderPort.ProgressCallback?
    ): FileDownloaderPort.DownloadResult = withContext(Dispatchers.IO) {
        // Create target/temp file pairs for all filenames this model serves (like old code)
        val downloadFiles = model.filenames.map { filename ->
            DownloadedFile(
                targetFile = File(targetDir, filename),
                tempFile = File(targetDir, "$filename${ModelConfig.TEMP_EXTENSION}")
            )
        }

        val targetFile = downloadFiles.first().targetFile

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                logger.error(TAG, "Download retry failed for ${model.filenames.first()}: $errorMsg")
                throw Exception(errorMsg)
            }

            val body = response.body ?: run {
                logger.error(
                    TAG,
                    "Download retry failed for ${model.filenames.first()}: Empty response body"
                )
                throw Exception("Empty response body")
            }

            // Use Content-Length when no resume (existingBytes == 0)
            // Use Content-Range total when resuming (existingBytes > 0)
            val serverContentLength = response.header("Content-Length")?.toLongOrNull()
            val contentRange = response.header("Content-Range")
            
            // Extract total from Content-Range (simpler: split by "/")
            val contentRangeTotal = contentRange?.split("/")?.lastOrNull()?.toLongOrNull()
            
            // If resuming (Content-Range present), use that total. Otherwise use Content-Length.
            val actualTotalSize = contentRangeTotal ?: serverContentLength ?: model.sizeBytes
            logger.info(TAG, "[SIZE] Non-resume path - Server Content-Length: $serverContentLength")
            logger.info(TAG, "[SIZE] Non-resume path - Server Content-Range: $contentRange")
            logger.info(TAG, "[SIZE] Non-resume path - Content-Range total: $contentRangeTotal")
            logger.info(TAG, "[SIZE] Non-resume path - Using: $actualTotalSize")

            var totalBytesRead = 0L
            
            // Use FileChannel with force(true) for atomic write+flush to disk
            // This prevents temp file corruption when worker is killed (e.g., permission dialog)
            val channels = downloadFiles.map { file ->
                FileChannel.open(
                    file.tempFile.toPath(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
                )
            }
            
            try {
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        // Write the same data to all temp files simultaneously
                        val byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead)
                        channels.forEach { it.write(byteBuffer.duplicate()) }
                        totalBytesRead += bytesRead

                        // Force write to physical disk (atomic write+flush)
                        // This ensures data is persisted even if worker is killed
                        channels.forEach { it.force(true) }

                        // Report progress
                        progressCallback?.onProgress(totalBytesRead, actualTotalSize)
                    }
                }
            } finally {
                channels.forEach { it.close() }
            }

            // Skip size validation - rely 100% on MD5 verification for integrity
            // Physical devices may add metadata/buffer bytes that cause size mismatches

            // Rename each temp file to target file
            for (downloadFile in downloadFiles) {
                if (downloadFile.targetFile.exists()) {
                    downloadFile.targetFile.delete()
                }
                downloadFile.tempFile.renameTo(downloadFile.targetFile)
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
