package com.browntowndev.enclave.feature.studio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import com.browntowndev.enclave.domain.port.media.VideoGenerationPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.media.GenerateMusicUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateVideoUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MultimediaGenerationService : Service() {
    companion object {
        const val TAG = "MultimediaGenerationService"
        const val ACTION_START = "com.browntowndev.enclave.studio.ACTION_START"
        const val ACTION_STOP = "com.browntowndev.enclave.studio.ACTION_STOP"

        const val EXTRA_PROMPT = "PROMPT"
        const val EXTRA_MEDIA_TYPE = "MEDIA_TYPE"

        private const val ONGOING_NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "generation_channel"
    }

    @Inject lateinit var imageGenerationPort: ImageGenerationPort

    @Inject lateinit var videoGenerationPort: VideoGenerationPort

    @Inject lateinit var generateVideoUseCase: GenerateVideoUseCase

    @Inject lateinit var generateMusicUseCase: GenerateMusicUseCase

    @Inject lateinit var studioRepository: StudioRepositoryPort

    @Inject lateinit var mediaProviderRepository: MediaProviderRepositoryPort

    @Inject lateinit var logger: LoggingPort

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        logger.info(TAG, "onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        logger.info(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                startForeground(ONGOING_NOTIFICATION_ID, createNotification("Preparing..."))
                val prompt = intent.getStringExtra(EXTRA_PROMPT)
                if (prompt == null) {
                    logger.error(TAG, "No prompt provided, stopping")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val mediaType = MediaCapability.valueOf(intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: MediaCapability.IMAGE.name)
                val settings =
                    StudioGenerationIntentCodec.readSettings(intent)
                        ?: when (mediaType) {
                            MediaCapability.IMAGE -> ImageGenerationSettings()
                            MediaCapability.VIDEO -> VideoGenerationSettings()
                            MediaCapability.MUSIC -> MusicGenerationSettings()
                        }

                updateNotification("Generating...")

                serviceScope.launch {
                    try {
                        val providers = mediaProviderRepository.getMediaProviders().first()
                        val provider = providers.firstOrNull()

                        if (provider == null) {
                            logger.error(TAG, "No provider configured")
                            return@launch
                        }

                        val result =
                            when (mediaType) {
                                MediaCapability.IMAGE -> {
                                    val imageSettings = settings as? ImageGenerationSettings ?: ImageGenerationSettings()
                                    imageGenerationPort.generateImage(prompt, provider, imageSettings)
                                }
                                MediaCapability.VIDEO -> {
                                    val videoSettings = settings as? VideoGenerationSettings ?: VideoGenerationSettings()
                                    generateVideoUseCase(prompt, videoSettings).map { listOf(it) }
                                }
                                MediaCapability.MUSIC -> {
                                    val musicSettings = settings as? MusicGenerationSettings ?: MusicGenerationSettings()
                                    generateMusicUseCase(prompt, musicSettings).map { listOf(it) }
                                }
                            }

                        result
                            .onSuccess { bytesList ->
                                val baseTime = System.currentTimeMillis()
                                bytesList.forEachIndexed { i, bytes ->
                                    val localUri = studioRepository.cacheEphemeralMedia(bytes, mediaType.name)
                                    val asset =
                                        com.browntowndev.enclave.domain.port.repository.StudioMediaAsset(
                                            id =
                                                java.util.UUID
                                                    .randomUUID()
                                                    .toString(),
                                            localUri = localUri,
                                            prompt = prompt,
                                            mediaType = mediaType.name,
                                            createdAt = baseTime + i,
                                        )
                                    studioRepository.addEphemeralMedia(asset)
                                }
                            }.onFailure { e ->
                                logger.error(TAG, "Generation failed", e)
                                studioRepository.reportGenerationError(e.message ?: "Generation failed")
                            }
                    } catch (e: Exception) {
                        logger.error(TAG, "Unexpected error in service", e)
                        studioRepository.reportGenerationError(e.message ?: "Unexpected error during generation")
                    } finally {
                        stopForeground(Service.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            ACTION_STOP -> {
                logger.info(TAG, "ACTION_STOP received")
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Multimedia Generation",
                NotificationManager.IMPORTANCE_LOW,
            )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(content: String): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle("Enclave")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ONGOING_NOTIFICATION_ID, createNotification(content))
    }

    override fun onDestroy() {
        logger.info(TAG, "onDestroy")
        serviceJob.cancel()
        super.onDestroy()
    }
}
