package com.browntowndev.pocketcrew.core.data.media

import android.content.Context
import android.net.Uri
import com.browntowndev.pocketcrew.domain.port.media.ImageAttachmentStoragePort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CachedImageAttachmentStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImageAttachmentStoragePort {

    companion object {
        private const val STORAGE_DIR_NAME = "chat-images"
    }

    override suspend fun stageImage(sourceUri: String): String = withContext(Dispatchers.IO) {
        val uri = URI(sourceUri)
        val storageDir = File(context.filesDir, STORAGE_DIR_NAME).apply { mkdirs() }
        val outputFile = File(storageDir, buildOutputFileName(uri))

        openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open image stream" }
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
                output.flush()
            }
        }

        outputFile.toURI().toString()
    }

    private fun buildOutputFileName(uri: URI): String {
        val suffix = when (uri.scheme?.lowercase()) {
            "content" -> when (context.contentResolver.getType(Uri.parse(uri.toString()))) {
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                "image/gif" -> ".gif"
                "image/heic" -> ".heic"
                "image/heif" -> ".heif"
                "image/jpeg" -> ".jpg"
                else -> extractSuffix(uri) ?: ".img"
            }
            else -> when (extractSuffix(uri)) {
                ".png", ".webp", ".gif", ".heic", ".heif", ".jpg", ".jpeg" -> extractSuffix(uri)
                else -> ".img"
            }
        }
        return "chat-image-${UUID.randomUUID()}$suffix"
    }

    private fun openInputStream(uri: URI) = when (uri.scheme?.lowercase()) {
        "file", null, "" -> File(uri).inputStream()
        else -> context.contentResolver.openInputStream(Uri.parse(uri.toString()))
    }

    private fun extractSuffix(uri: URI): String? {
        val path = uri.path ?: return null
        val fileName = File(path).name
        val index = fileName.lastIndexOf('.')
        return if (index >= 0 && index < fileName.lastIndex) fileName.substring(index) else null
    }

}
