package com.browntowndev.pocketcrew.core.ui.component

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.util.loadVideoFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VideoThumbnail(
    videoUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    thumbnail: Bitmap? = null,
    onThumbnailMeasured: ((Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val loadedBitmap by produceState<Bitmap?>(initialValue = null, key1 = videoUri) {
        if (videoUri != null && thumbnail == null) {
            value = withContext(Dispatchers.IO) {
                loadVideoFrame(videoUri, context)
            }
        }
    }

    val displayBitmap = thumbnail ?: loadedBitmap

    if (displayBitmap != null) {
        LaunchedEffect(displayBitmap.width, displayBitmap.height) {
            if (displayBitmap.width > 0 && displayBitmap.height > 0) {
                onThumbnailMeasured?.invoke(displayBitmap.width.toFloat() / displayBitmap.height.toFloat())
            }
        }
        Image(
            bitmap = displayBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        val transition = rememberInfiniteTransition(label = "video_shimmer")
        val progress = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1500,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Restart,
            ),
            label = "video_shimmer_progress",
        )
        val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)

        Box(
            modifier = modifier
                .background(Color.Black)
                .shimmerEffect(
                    progressState = progress,
                    baseColor = baseColor,
                    highlightColor = highlightColor,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
