package com.browntowndev.pocketcrew.core.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.port.media.MediaSaverPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.URI
import javax.inject.Inject

class AndroidMediaSaver @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaSaverPort {

    override suspend fun saveToGallery(localUri: String, mediaType: MediaCapability): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = URI(localUri)
            val file = File(uri)
            if (!file.exists()) {
                throw IllegalStateException("Local file not found: $localUri")
            }

            val contentValues = ContentValues().apply {
                val timestamp = System.currentTimeMillis() / 1000
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.DATE_ADDED, timestamp)
                put(MediaStore.MediaColumns.DATE_MODIFIED, timestamp)

                if (mediaType == MediaCapability.IMAGE) {
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PocketCrew")
                } else {
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PocketCrew")
                }
            }

            val collectionUri = if (mediaType == MediaCapability.IMAGE) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val resolver = context.contentResolver
            val outputUri = resolver.insert(collectionUri, contentValues)
                ?: throw IllegalStateException("Failed to create new MediaStore record")

            resolver.openOutputStream(outputUri)?.use { output ->
                FileInputStream(file).use { input ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Failed to open output stream to MediaStore")

            outputUri.toString()
        }
    }
}
