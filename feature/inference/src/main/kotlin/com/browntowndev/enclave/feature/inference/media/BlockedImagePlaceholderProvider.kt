package com.browntowndev.enclave.feature.inference.media

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Color
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the placeholder bytes that replace an image flagged by [com.browntowndev.enclave.domain.port.inference.ImageModerationPort].
 *
 * The placeholder is a deterministic solid-color PNG generated once on first use and cached for the
 * lifetime of the singleton. Keeping it programmatic avoids shipping a binary asset and keeps the
 * moderation substitution fully self-contained inside `:feature:inference`.
 */
@Singleton
open class BlockedImagePlaceholderProvider
    @Inject
    constructor() {
        @Volatile
        private var cachedBytes: ByteArray? = null

        open fun bytes(): ByteArray =
            cachedBytes ?: synchronized(this) {
                cachedBytes ?: encodePlaceholder(SIZE).also { cachedBytes = it }
            }

        private fun encodePlaceholder(size: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(COLOR)
            return ByteArrayOutputStream(ESTIMATED_PNG_BYTES).use { out ->
                bitmap.compress(CompressFormat.PNG, COMPRESSION_QUALITY, out)
                bitmap.recycle()
                out.toByteArray()
            }
        }

        private companion object {
            private const val SIZE = 512
            private const val COMPRESSION_QUALITY = 100
            private const val ESTIMATED_PNG_BYTES = 8 * 1024
            private val COLOR = Color.rgb(0x33, 0x33, 0x33)
        }
    }
