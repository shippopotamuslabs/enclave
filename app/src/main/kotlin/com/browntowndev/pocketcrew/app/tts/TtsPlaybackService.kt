package com.browntowndev.pocketcrew.app.tts

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackRegistryPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackStatus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A private MediaSessionService that hosts ExoPlayer + MediaSession for TTS playback.
 *
 * This service accepts app/trusted Media3 controllers and uses foregroundServiceType="mediaPlayback"
 * for Android 14+ compliance.
 *
 * The service creates an ExoPlayer with a custom DataSource.Factory that resolves
 * `pocketcrew-tts://play/{requestId}` URIs to the provider's audio stream.
 * Media3 automatically publishes the media notification.
 *
 * The service is started by [TtsPlaybackController] when TTS playback begins.
 * It creates an ExoPlayer instance, sets a MediaItem from the request metadata,
 * and ExoPlayer's custom DataSource reads the audio stream from the provider.
 */
@AndroidEntryPoint
class TtsPlaybackService : MediaSessionService() {
    @Inject lateinit var registry: TtsPlaybackRegistryPort

    @Inject lateinit var ttsDataSourceFactory: TtsDataSource.Factory

    @Inject lateinit var logger: LoggingPort

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var currentRequestId: String? = null

    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        logger.debug(TAG, "TtsPlaybackService created and player initialized")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        logger.debug(TAG, "TtsPlaybackService onStartCommand action=${intent?.action}")
        if (intent?.action == ACTION_PLAY_REQUEST) {
            val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
            if (requestId != null) {
                logger.debug(TAG, "Received play request for: $requestId")
                startPlayback(requestId)
            } else {
                logger.error(TAG, "Missing request ID in play intent")
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopPlayback()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Starts playback for the given [requestId].
     * Creates ExoPlayer, builds a MediaItem from the request, and starts playback.
     */
    private fun startPlayback(requestId: String) {
        val request =
            registry.resolve(requestId) ?: run {
                logger.error(TAG, "Request not found in registry: $requestId")
                stopSelf()
                return
            }

        currentRequestId = requestId
        registry.publishStatus(requestId, TtsPlaybackStatus.Initializing)

        val mediaItem =
            MediaItem
                .Builder()
                .setUri(Uri.parse(request.toUriString()))
                .setMimeType(request.audioMimeType)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(request.notificationTitle)
                        .setArtist(request.notificationArtist)
                        .build(),
                ).build()

        player?.let { exoPlayer ->
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }

        logger.debug(TAG, "TTS playback started for request: $requestId")
    }

    private fun initializePlayer() {
        val mediaSourceFactory = DefaultMediaSourceFactory(ttsDataSourceFactory)

        val exoPlayer =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()

        exoPlayer.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    logger.debug(TAG, "TTS playback state changed: $playbackState")
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            logger.debug(TAG, "TTS playback completed")
                            finishPlayback(TtsPlaybackStatus.Completed)
                        }
                        Player.STATE_IDLE -> {
                            // If we're idle and not preparing, there was likely an error
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    logger.debug(TAG, "TTS isPlaying changed: $isPlaying")
                    if (isPlaying) {
                        currentRequestId?.let { registry.publishStatus(it, TtsPlaybackStatus.Playing) }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    logger.error(TAG, "TTS playback error: ${error.message}", error)
                    finishPlayback(TtsPlaybackStatus.Error(error.message ?: "Playback failed", error))
                }
            },
        )

        player = exoPlayer
        val session =
            MediaSession
                .Builder(this, exoPlayer)
                .setCallback(TtsMediaSessionCallback())
                .build()
        mediaSession = session
        addSession(session)
    }

    private fun finishPlayback(status: TtsPlaybackStatus) {
        currentRequestId?.let { requestId ->
            registry.publishStatus(requestId, status)
            registry.remove(requestId)
        }
        currentRequestId = null
        stopSelf()
    }

    fun stopPlayback() {
        currentRequestId?.let { requestId ->
            registry.publishStatus(requestId, TtsPlaybackStatus.Stopped)
            registry.remove(requestId)
        }
        currentRequestId = null
        player?.stop()
    }

    override fun onDestroy() {
        currentRequestId?.let { requestId ->
            registry.publishStatus(requestId, TtsPlaybackStatus.Stopped)
            registry.remove(requestId)
        }
        currentRequestId = null

        mediaSession?.let {
            removeSession(it)
            it.release()
        }
        mediaSession = null

        player?.release()
        player = null

        super.onDestroy()
        logger.debug(TAG, "TtsPlaybackService destroyed")
    }

    private inner class TtsMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            if (controller.packageName == packageName || controller.isTrusted) {
                MediaSession.ConnectionResult.accept(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                        .buildUpon()
                        .add(Player.COMMAND_STOP)
                        .build(),
                )
            } else {
                MediaSession.ConnectionResult.reject()
            }
    }

    companion object {
        private const val TAG = "TtsPlaybackService"
        internal const val ACTION_PLAY_REQUEST = "com.browntowndev.pocketcrew.tts.PLAY"
        internal const val EXTRA_REQUEST_ID = "request_id"
    }
}
