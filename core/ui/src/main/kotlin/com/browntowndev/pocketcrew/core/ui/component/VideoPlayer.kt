package com.browntowndev.pocketcrew.core.ui.component

import android.content.Context
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@AndroidXOptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    localUri: String,
    contentDescription: String,
    autoPlay: Boolean = false,
    controlsEnabled: Boolean = true,
    muted: Boolean = true,
    modifier: Modifier = Modifier,
    playerFactory: (Context, String, Boolean, Boolean) -> VideoPlaybackHandle = ::createVideoPlaybackHandle,
    playerContent: @Composable (Player, Modifier) -> Unit = { player, contentModifier ->
        DefaultVideoPlayerContent(
            player = player,
            modifier = contentModifier,
        )
    },
) {
    var isPlaying by remember(localUri, autoPlay) { mutableStateOf(autoPlay) }
    var isMuted by remember(localUri, muted) { mutableStateOf(muted) }
    var controlsVisible by remember(localUri) { mutableStateOf(true) }
    var videoAspectRatio by remember { mutableStateOf(1f) }

    if (LocalInspectionMode.current) {
        VideoPreviewPlaceholder(
            contentDescription = contentDescription,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val playbackHandle = remember(localUri) {
        playerFactory(context, localUri, autoPlay, muted)
    }

    LaunchedEffect(isPlaying, controlsEnabled) {
        controlsVisible = controlsEnabled
        if (isPlaying && controlsEnabled) {
            delay(CONTROL_FADE_DELAY_MILLIS)
            controlsVisible = false
        }
    }

    LaunchedEffect(isPlaying, playbackHandle) {
        playbackHandle.playWhenReady = isPlaying
    }

    LaunchedEffect(isMuted, playbackHandle) {
        playbackHandle.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(playbackHandle.player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = (videoSize.width.toFloat() / videoSize.height.toFloat()) * videoSize.pixelWidthHeightRatio
                }
            }
        }
        playbackHandle.player.addListener(listener)
        
        val initialSize = playbackHandle.player.videoSize
        if (initialSize.width > 0 && initialSize.height > 0) {
            videoAspectRatio = (initialSize.width.toFloat() / initialSize.height.toFloat()) * initialSize.pixelWidthHeightRatio
        }
        
        onDispose {
            playbackHandle.player.removeListener(listener)
        }
    }

    DisposableEffect(playbackHandle) {
        onDispose { playbackHandle.release() }
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
                    }
                } else {
                    Modifier
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(videoAspectRatio)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            playerContent(
                playbackHandle.player,
                Modifier.fillMaxSize(),
            )

            if (controlsEnabled) {
                AnimatedVisibility(
                    visible = controlsVisible || !isPlaying,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    IconButton(
                        onClick = {
                            isPlaying = !isPlaying
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

                IconButton(
                    onClick = {
                        isMuted = !isMuted
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(30.dp),
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute video" else "Mute video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultVideoPlayerContent(
    player: Player,
    modifier: Modifier = Modifier,
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
        },
        modifier = modifier,
    )
}

private fun createVideoPlaybackHandle(
    context: Context,
    localUri: String,
    autoPlay: Boolean,
    muted: Boolean,
): VideoPlaybackHandle =
    ExoVideoPlaybackHandle(
        context = context,
        localUri = localUri,
        autoPlay = autoPlay,
        muted = muted,
    )

interface VideoPlaybackHandle {
    val player: Player
    var playWhenReady: Boolean
    var volume: Float
    fun release()
}

private class ExoVideoPlaybackHandle(
    context: Context,
    localUri: String,
    autoPlay: Boolean,
    muted: Boolean,
) : VideoPlaybackHandle {
    override val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        setAudioAttributes(audioAttributes, true)

        setMediaItem(MediaItem.fromUri(localUri.toUri()))
        repeatMode = Player.REPEAT_MODE_ONE
        playWhenReady = autoPlay
        volume = if (muted) 0f else 1f
        prepare()
    }

    override var playWhenReady: Boolean
        get() = player.playWhenReady
        set(value) {
            player.playWhenReady = value
        }

    override var volume: Float
        get() = player.volume
        set(value) {
            player.volume = value
        }

    override fun release() {
        player.release()
    }
}

@Composable
private fun VideoPreviewPlaceholder(
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
