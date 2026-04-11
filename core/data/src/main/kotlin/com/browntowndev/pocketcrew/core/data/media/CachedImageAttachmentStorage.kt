package com.browntowndev.pocketcrew.core.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.browntowndev.pocketcrew.domain.port.media.ImageAttachmentStoragePort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CachedImageAttachmentStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImageAttachmentStoragePort {

    companion object {
        private const val MAX_DIMENSION = 512
        private const val JPEG_QUALITY = 85
        private const val STORAGE_DIR_NAME = "chat-images"
    }

    override suspend fun stageImage(sourceUri: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourceUri)
        val bitmap = decodeBitmap(uri)
        val storageDir = File(context.filesDir, STORAGE_DIR_NAME).apply { mkdirs() }
        val outputFile = File(storageDir, "chat-image-${UUID.randomUUID()}.jpg")

        FileOutputStream(outputFile).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "Failed to compress selected image"
            }
            output.flush()
        }

        Uri.fromFile(outputFile).toString()
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open image stream" }
            BitmapFactory.decodeStream(input, null, bounds)
        }

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to reopen image stream" }
            return requireNotNull(BitmapFactory.decodeStream(input, null, decodeOptions)) {
                "Failed to decode selected image"
            }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var candidateWidth = width
        var candidateHeight = height

        while (candidateWidth > MAX_DIMENSION || candidateHeight > MAX_DIMENSION) {
            candidateWidth /= 2
            candidateHeight /= 2
            sampleSize *= 2
        }

        return sampleSize.coerceAtLeast(1)
    }
}
