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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Utility for downscaling and normalizing images before passing them to inference engines.
 * 
 * Large images (e.g. 12MP camera photos) can cause native segmentation faults (ANRs)
 * in inference engines like LiteRT if passed directly. This utility downscales them
 * to a safe maximum dimension while preserving aspect ratio and handles EXIF rotation.
 */
object ImageDownscaler {
    private const val TAG = "ImageDownscaler"
    private const val DEFAULT_REQ_WIDTH = 2048
    private const val DEFAULT_REQ_HEIGHT = 2048

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

            this.inSampleSize = calculateInSampleSize(outWidth, outHeight, reqWidth, reqHeight)
            inJustDecodeBounds = false
        }

        val bitmap = requireNotNull(openStream()?.use { BitmapFactory.decodeStream(it, null, options) }) {
            "Unable to decode image URI: $imageUri"
        }

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

        val resizedBitmap = resizeToFit(rotatedBitmap, reqWidth, reqHeight)
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        if (resizedBitmap != rotatedBitmap) {
            rotatedBitmap.recycle()
        }

        resizedBitmap
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

    internal fun calculateInSampleSize(
        outWidth: Int,
        outHeight: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        if (outWidth <= reqWidth && outHeight <= reqHeight) {
            return 1
        }

        val widthRatio = max(1, ceil(outWidth.toDouble() / reqWidth.toDouble()).toInt())
        val heightRatio = max(1, ceil(outHeight.toDouble() / reqHeight.toDouble()).toInt())
        return max(widthRatio, heightRatio)
    }

    internal fun resizeToFit(
        bitmap: Bitmap,
        reqWidth: Int,
        reqHeight: Int,
    ): Bitmap {
        val (targetWidth, targetHeight) = calculateTargetDimensions(
            width = bitmap.width,
            height = bitmap.height,
            reqWidth = reqWidth,
            reqHeight = reqHeight,
        )
        if (targetWidth == bitmap.width && targetHeight == bitmap.height) {
            return bitmap
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    internal fun calculateTargetDimensions(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Pair<Int, Int> {
        if (width <= reqWidth && height <= reqHeight) {
            return width to height
        }

        val widthScale = reqWidth.toFloat() / width.toFloat()
        val heightScale = reqHeight.toFloat() / height.toFloat()
        val scale = min(widthScale, heightScale)
        val targetWidth = max(1, (width * scale).roundToInt())
        val targetHeight = max(1, (height * scale).roundToInt())
        return targetWidth to targetHeight
    }
}
