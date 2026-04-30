package com.browntowndev.pocketcrew.feature.studio

import androidx.annotation.OptIn as AndroidXOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@AndroidXOptIn(UnstableApi::class)
@Composable
internal fun StudioVideoPlayer(
    localUri: String,
    contentDescription: String,
    autoPlay: Boolean = false,
    controlsEnabled: Boolean = true,
    muted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember(localUri, autoPlay) { mutableStateOf(autoPlay) }
    var controlsVisible by remember(localUri) { mutableStateOf(true) }

    if (LocalInspectionMode.current) {
        StudioVideoPreviewPlaceholder(
            contentDescription = contentDescription,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val player = remember(localUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(localUri.toUri()))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = autoPlay
            volume = if (muted) 0f else 1f
            prepare()
        }
    }

    LaunchedEffect(isPlaying, controlsEnabled) {
        controlsVisible = controlsEnabled
        if (isPlaying && controlsEnabled) {
            delay(CONTROL_FADE_DELAY_MILLIS)
            controlsVisible = false
        }
    }

    LaunchedEffect(autoPlay, muted, player) {
        isPlaying = autoPlay
        player.playWhenReady = autoPlay
        player.volume = if (muted) 0f else 1f
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .then(
                if (controlsEnabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        isPlaying = !isPlaying
                        player.playWhenReady = isPlaying
                    }
                } else {
                    Modifier
                }
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    this.player = player
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { playerView ->
                playerView.player = player
                player.playWhenReady = isPlaying
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsEnabled) {
            AnimatedVisibility(
                visible = controlsVisible || !isPlaying,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                IconButton(
                    onClick = {
                        isPlaying = !isPlaying
                        player.playWhenReady = isPlaying
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.52f), CircleShape),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause video" else "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioVideoPreviewPlaceholder(
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.DarkGray)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Movie,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(56.dp),
        )
    }
}

private const val CONTROL_FADE_DELAY_MILLIS = 1_000L
