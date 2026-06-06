package com.browntowndev.enclave.feature.studio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.media.GenerateMediaUseCase
import com.browntowndev.enclave.domain.usecase.media.MediaGenerationRequestMapper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.browntowndev.enclave.core.data.R as CoreDataR

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

    @Inject lateinit var generateMediaUseCase: GenerateMediaUseCase

    @Inject lateinit var studioRepository: StudioRepositoryPort

    @Inject lateinit var logger: LoggingPort

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var generationJob: Job? = null

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
                if (generationJob?.isActive == true) {
                    logger.warning(TAG, "Ignoring duplicate generation request while one is active")
                    return START_NOT_STICKY
                }
                startForeground(ONGOING_NOTIFICATION_ID, createNotification("Preparing..."))
                val prompt = intent.getStringExtra(EXTRA_PROMPT)
                if (prompt == null) {
                    logger.error(TAG, "No prompt provided, stopping")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val mediaType =
                    MediaCapability.valueOf(
                        intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: MediaCapability.IMAGE.name,
                    )
                val settings =
                    StudioGenerationIntentCodec.readSettings(intent)
                        ?: when (mediaType) {
                            MediaCapability.IMAGE -> ImageGenerationSettings()
                            MediaCapability.VIDEO -> VideoGenerationSettings()
                            MediaCapability.MUSIC -> MusicGenerationSettings()
                        }

                studioRepository.reportActiveGeneration(
                    ActiveStudioGeneration(
                        prompt = prompt,
                        mediaType = mediaType,
                        settings = settings,
                    ),
                )
                updateNotification("Generating...")

                generationJob = serviceScope.launch {
                    try {
                        val mediaRequest =
                            MediaGenerationRequestMapper.fromStudio(
                                prompt = prompt,
                                capability = mediaType,
                                settings = settings,
                            )
                        generateMediaUseCase(
                            request = mediaRequest,
                            onImageProgress = { progress ->
                                studioRepository.reportGenerationProgress(progress)
                                updateNotification(progress.notificationLabel())
                            },
                        ).onFailure { error ->
                            logger.error(TAG, "Generation failed", error)
                            studioRepository.reportGenerationError(error.message ?: "Generation failed")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error(TAG, "Unexpected error in service", e)
                        studioRepository.reportGenerationError(e.message ?: "Unexpected error during generation")
                    } finally {
                        studioRepository.clearGenerationProgress()
                        studioRepository.clearActiveGeneration()
                        stopForeground(Service.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            ACTION_STOP -> {
                logger.info(TAG, "ACTION_STOP received")
                generationJob?.cancel()
                studioRepository.clearActiveGeneration()
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
            .setSmallIcon(CoreDataR.drawable.ic_notification)
            .setContentIntent(createStudioContentIntent())
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun createStudioContentIntent(): PendingIntent {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(StudioNotificationDeepLink.URI_PATTERN))
                .setPackage(packageName)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ONGOING_NOTIFICATION_ID, createNotification(content))
    }

    override fun onDestroy() {
        logger.info(TAG, "onDestroy")
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun ImageGenerationProgress.notificationLabel(): String =
        when (this) {
            ImageGenerationProgress.PreparingModel -> "Preparing model..."
            ImageGenerationProgress.LoadingModel -> "Loading model..."
            is ImageGenerationProgress.Generating -> "Generating image..."
            ImageGenerationProgress.SavingOutput -> "Saving output..."
        }
}
