package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

/**
 * Utility for downscaling and normalizing images before passing them to inference engines.
 * 
 * Large images (e.g. 12MP camera photos) can cause native segmentation faults (ANRs)
 * in inference engines like LiteRT if passed directly. This utility downscales them
 * to a safe resolution (e.g., 1024x1024) and handles EXIF rotation.
 */
object ImageDownscaler {
    private const val TAG = "ImageDownscaler"
    private const val DEFAULT_REQ_WIDTH = 512
    private const val DEFAULT_REQ_HEIGHT = 512

    suspend fun downscale(
        context: Context,
        imageUri: String,
        reqWidth: Int = DEFAULT_REQ_WIDTH,
        reqHeight: Int = DEFAULT_REQ_HEIGHT
    ): Bitmap = withContext(Dispatchers.IO) {
        val uri = Uri.parse(imageUri)

        fun openStream() = if (uri.scheme?.lowercase() in listOf(null, "", "file")) {
            FileInputStream(uri.path ?: "")
        } else {
            context.contentResolver.openInputStream(uri)
        }

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            openStream()?.use { BitmapFactory.decodeStream(it, null, this) }
            
            // Calculate inSampleSize
            var inSampleSize = 1
            val height = outHeight
            val width = outWidth
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            this.inSampleSize = inSampleSize
            inJustDecodeBounds = false
        }

        val bitmap = requireNotNull(openStream()?.use { BitmapFactory.decodeStream(it, null, options) }) {
            "Unable to decode image URI: $imageUri"
        }

        // Handle rotation from Exif
        var rotatedBitmap = bitmap
        try {
            val orientation = openStream()?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1.0f, 1.0f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    matrix.preScale(1.0f, -1.0f)
                    matrix.postRotate(180f)
                }
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.preScale(-1.0f, 1.0f)
                    matrix.postRotate(90f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.preScale(-1.0f, 1.0f)
                    matrix.postRotate(270f)
                }
            }
            if (!matrix.isIdentity) {
                rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read or apply Exif orientation for $imageUri", e)
        }

        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }

        rotatedBitmap
    }

    suspend fun downscaleToPngBytes(
        context: Context,
        imageUri: String,
        reqWidth: Int = DEFAULT_REQ_WIDTH,
        reqHeight: Int = DEFAULT_REQ_HEIGHT
    ): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = downscale(context, imageUri, reqWidth, reqHeight)
        val pngBytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        bitmap.recycle()
        // Aggressive GC hint before passing back to native engine
        System.gc()
        pngBytes
    }

    suspend fun downscaleToTempFile(
        context: Context,
        imageUri: String,
        reqWidth: Int = DEFAULT_REQ_WIDTH,
        reqHeight: Int = DEFAULT_REQ_HEIGHT
    ): String = withContext(Dispatchers.IO) {
        val bytes = downscaleToPngBytes(context, imageUri, reqWidth, reqHeight)
        val tempFile = File.createTempFile("downscaled_", ".png", context.cacheDir)
        tempFile.writeBytes(bytes)
        tempFile.absolutePath
    }
}
