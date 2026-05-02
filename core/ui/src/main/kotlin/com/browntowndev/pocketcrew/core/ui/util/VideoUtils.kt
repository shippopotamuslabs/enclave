package com.browntowndev.pocketcrew.core.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri

fun loadVideoFrame(
    localUri: String,
    context: Context,
): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, localUri.toUri())
        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: RuntimeException) {
        null
    } finally {
        retriever.release()
    }
}
